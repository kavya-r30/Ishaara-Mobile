package com.ishaara.mobile.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Display
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.Screenshot
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64

class ScreenshotTaker(
    private val service: AccessibilityService,
    private val overlay: OverlayControl,
) {
    private companion object {
        const val TARGET_WIDTH = 768
        const val JPEG_QUALITY = 70
    }

    /** Returns null when the screen cannot be captured, such as on a FLAG_SECURE window. */
    suspend fun capture(snapshot: ScreenSnapshot): Screenshot? {
        overlay.setHidden(true)
        val bitmap = try {
            grab()
        } finally {
            overlay.setHidden(false)
        } ?: return null
        return withContext(Dispatchers.Default) {
            val scale = bitmap.width.toDouble() / TARGET_WIDTH
            val height = (bitmap.height / scale).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, TARGET_WIDTH, height, true).copy(Bitmap.Config.ARGB_8888, true)
            annotate(scaled, snapshot, scale)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            Screenshot(Base64.getEncoder().encodeToString(out.toByteArray()), scale)
        }
    }

    private suspend fun grab(): Bitmap? = suspendCancellableCoroutine { continuation ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    continuation.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(null)
                }
            },
        )
    }

    private fun annotate(bitmap: Bitmap, snapshot: ScreenSnapshot, scale: Double) {
        val canvas = Canvas(bitmap)
        val box = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.rgb(255, 92, 108)
        }
        val tagBackground = Paint().apply { color = Color.rgb(255, 92, 108) }
        val tagText = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isAntiAlias = true
            isFakeBoldText = true
        }
        snapshot.elements.filter { it.clickable || it.editable }.forEach { e ->
            val left = (e.bounds.left / scale).toFloat()
            val top = (e.bounds.top / scale).toFloat()
            val right = (e.bounds.right / scale).toFloat()
            val bottom = (e.bounds.bottom / scale).toFloat()
            canvas.drawRect(left, top, right, bottom, box)
            val tag = e.id.toString()
            canvas.drawRect(left, top, left + tagText.measureText(tag) + 8, top + 22, tagBackground)
            canvas.drawText(tag, left + 4, top + 17, tagText)
        }
    }
}
