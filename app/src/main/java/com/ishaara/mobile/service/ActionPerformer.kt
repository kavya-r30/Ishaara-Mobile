package com.ishaara.mobile.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.ScrollDirection
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Lets the performer get the overlay out of the way of gestures and screenshots. */
interface OverlayControl {
    fun setTouchable(touchable: Boolean)
    suspend fun setHidden(hidden: Boolean)
}

class ActionPerformer(
    private val service: AccessibilityService,
    private val overlay: OverlayControl,
) {
    private val screen: Rect
        get() = service.resources.displayMetrics.let { Rect(0, 0, it.widthPixels, it.heightPixels) }

    suspend fun tap(node: AccessibilityNodeInfo): ActionResult {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ActionResult.ok("Tapped.")
            }
            current = current.parent
        }
        // A stale or scrolled-away element reports a position that would tap something else entirely.
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        node.refresh()
        val rect = bounds(node)
        if (rect.isEmpty || !node.isVisibleToUser || !screen.contains(rect.centerX(), rect.centerY())) {
            return ActionResult.fail("OFF_SCREEN", "That element isn't on screen. Scroll to it first.")
        }
        return gestureResult(tapPath(rect.centerX().toFloat(), rect.centerY().toFloat()), 60, "Tapped.")
    }

    suspend fun tapAt(x: Int, y: Int): ActionResult =
        gestureResult(tapPath(x.toFloat(), y.toFloat()), 60, "Tapped.")

    suspend fun longPress(node: AccessibilityNodeInfo): ActionResult {
        if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return ActionResult.ok("Long pressed.")
        }
        val rect = bounds(node)
        return gestureResult(tapPath(rect.centerX().toFloat(), rect.centerY().toFloat()), 700, "Long pressed.")
    }

    suspend fun doubleTap(node: AccessibilityNodeInfo): ActionResult {
        val rect = bounds(node)
        val path = tapPath(rect.centerX().toFloat(), rect.centerY().toFloat())
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .addStroke(GestureDescription.StrokeDescription(path, 150, 50))
            .build()
        return if (dispatch(description)) ActionResult.ok("Double tapped.") else ActionResult.fail("GESTURE_FAILED", "The double tap did not register.")
    }

    suspend fun swipe(direction: ScrollDirection, node: AccessibilityNodeInfo?): ActionResult {
        val area = node?.let(::bounds) ?: screen
        val path = Path()
        val cx = area.exactCenterX()
        val cy = area.exactCenterY()
        val dx = area.width() * 0.35f
        val dy = area.height() * 0.35f
        when (direction) {
            ScrollDirection.UP -> { path.moveTo(cx, cy + dy); path.lineTo(cx, cy - dy) }
            ScrollDirection.DOWN -> { path.moveTo(cx, cy - dy); path.lineTo(cx, cy + dy) }
            ScrollDirection.LEFT -> { path.moveTo(cx + dx, cy); path.lineTo(cx - dx, cy) }
            ScrollDirection.RIGHT -> { path.moveTo(cx - dx, cy); path.lineTo(cx + dx, cy) }
        }
        return gestureResult(path, 300, "Swiped.")
    }

    suspend fun drag(from: AccessibilityNodeInfo, to: AccessibilityNodeInfo): ActionResult {
        val start = bounds(from)
        val end = bounds(to)
        val path = Path().apply {
            moveTo(start.exactCenterX(), start.exactCenterY())
            lineTo(end.exactCenterX(), end.exactCenterY())
        }
        return gestureResult(path, 900, "Dragged.")
    }

    suspend fun type(node: AccessibilityNodeInfo, text: String, submit: Boolean): ActionResult {
        if (!node.isEditable) return ActionResult.fail("NOT_EDITABLE", "That element does not accept text.")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        // Some apps accept the action but ignore it, so check. If so, tap the field and paste, as a person would.
        if (!showsText(node, text)) {
            tap(node)
            delay(FIELD_FOCUS_MS)
            service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Ishaara", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (!showsText(node, text)) {
                return ActionResult.fail("TYPE_FAILED", "The text did not appear in that field. It may be the wrong element; use look_at_screen.")
            }
        }
        if (submit && !node.performAction(AccessibilityAction.ACTION_IME_ENTER.id)) {
            return ActionResult.fail("SUBMIT_FAILED", "Text entered, but Enter did not work. Tap the submit button instead.")
        }
        return ActionResult.ok(if (submit) "Typed and submitted." else "Typed.")
    }

    suspend fun scroll(direction: ScrollDirection, node: AccessibilityNodeInfo?, nodes: Collection<AccessibilityNodeInfo>): ActionResult {
        val target = node ?: nodes.filter { it.isScrollable }.maxByOrNull { bounds(it).width() * bounds(it).height() }
        val action = when (direction) {
            ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (target != null && target.performAction(action)) return ActionResult.ok("Scrolled.")
        // Swiping up moves content down the page, which is what "scroll down" means.
        val swipe = when (direction) {
            ScrollDirection.DOWN -> ScrollDirection.UP
            ScrollDirection.UP -> ScrollDirection.DOWN
            ScrollDirection.LEFT -> ScrollDirection.RIGHT
            ScrollDirection.RIGHT -> ScrollDirection.LEFT
        }
        return swipe(swipe, target)
    }

    fun press(key: DeviceKey): ActionResult {
        val action = when (key) {
            DeviceKey.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            DeviceKey.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            DeviceKey.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            DeviceKey.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            DeviceKey.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            DeviceKey.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            DeviceKey.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
        }
        return if (service.performGlobalAction(action)) ActionResult.ok("Pressed.") else ActionResult.fail("KEY_FAILED", "That key did not work.")
    }

    /** Ignores case, spaces and punctuation, because apps reformat input: 9876543210 can show as (987) 654-3210. */
    private suspend fun showsText(node: AccessibilityNodeInfo, text: String): Boolean {
        fun normal(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
        repeat(FIELD_CHECKS) {
            delay(FIELD_UPDATE_MS)
            if (!node.refresh()) return false
            // Password fields hide their contents, so there is nothing to compare.
            if (node.isPassword) return true
            if (normal(node.text?.toString().orEmpty()).contains(normal(text))) return true
        }
        return false
    }

    private companion object {
        const val FIELD_UPDATE_MS = 250L
        const val FIELD_CHECKS = 5
        const val FIELD_FOCUS_MS = 400L
        const val GESTURE_TIMEOUT_MS = 4000L
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun tapPath(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    private suspend fun gestureResult(path: Path, durationMs: Long, message: String): ActionResult {
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return if (dispatch(description)) ActionResult.ok(message) else ActionResult.fail("GESTURE_FAILED", "The gesture did not register.")
    }

    private suspend fun dispatch(description: GestureDescription): Boolean {
        overlay.setTouchable(false)
        try {
            // Android doesn't always report back, and waiting forever would freeze the whole task.
            return withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val sent = service.dispatchGesture(
                        description,
                        object : AccessibilityService.GestureResultCallback() {
                            override fun onCompleted(d: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onCancelled(d: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(false)
                            }
                        },
                        null,
                    )
                    if (!sent) continuation.resume(false)
                }
            } ?: false
        } finally {
            overlay.setTouchable(true)
        }
    }
}
