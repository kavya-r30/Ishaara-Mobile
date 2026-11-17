package com.ishaara.mobile.skills

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.service.ActionPerformer
import com.ishaara.mobile.service.ScreenSettler
import kotlinx.coroutines.delay

/**
 * Taking a photo barely changes the screen, so an agent looking for a change keeps re-tapping and
 * takes several. This does it in one step: exactly one tap on the shutter.
 */
class CameraSkill(
    private val service: AccessibilityService,
    private val settler: ScreenSettler,
    private val performer: ActionPerformer,
) {
    private companion object {
        val SHUTTER_LABELS = listOf("Shutter", "Take photo", "Take picture", "Capture", "Camera button")
        val SWITCH_LABELS = listOf("Switch to front camera", "Switch camera", "Flip camera", "Front camera", "Switch to selfie")
        const val SHUTTER_POLLS = 10
        const val POLL_DELAY_MS = 500L
        const val CAPTURE_DELAY_MS = 1500L
    }

    suspend fun takePhoto(selfie: Boolean): ActionResult {
        try {
            service.startActivity(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            return ActionResult.fail("NOT_AVAILABLE", "No camera app was found.")
        }
        settler.actionPerformed(LAUNCH_EXPECT_MS)
        settler.await()
        findAny(SHUTTER_LABELS) ?: return ActionResult.fail(
            "SHUTTER_NOT_FOUND",
            "The camera opened but its shutter button wasn't found. Look at the screen; there may be a dialog to answer first.",
        )

        if (selfie) {
            val switch = findAny(SWITCH_LABELS)
                ?: return ActionResult.fail("SWITCH_NOT_FOUND", "The button to switch to the front camera wasn't found. Look at the screen.")
            performer.tap(switch)
            delay(CAPTURE_DELAY_MS)
        }

        val shutter = findAny(SHUTTER_LABELS)
            ?: return ActionResult.fail("SHUTTER_NOT_FOUND", "The shutter button disappeared. Look at the screen.")
        val tapped = performer.tap(shutter)
        if (!tapped.ok) return tapped
        delay(CAPTURE_DELAY_MS)
        return ActionResult.ok(if (selfie) "Took a selfie." else "Took a photo.", selfVerified = true)
    }

    private suspend fun findAny(labels: List<String>): AccessibilityNodeInfo? {
        repeat(SHUTTER_POLLS) {
            val root = service.rootInActiveWindow
            if (root != null) {
                labels.forEach { label ->
                    root.findAccessibilityNodeInfosByText(label).firstOrNull { it.isVisibleToUser }?.let { return it }
                }
            }
            delay(POLL_DELAY_MS)
        }
        return null
    }
}

private const val LAUNCH_EXPECT_MS = 4000L
