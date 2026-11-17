package com.ishaara.mobile.skills

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.ishaara.mobile.core.model.ActionResult

/** Android doesn't let apps inject raw key events, so each combo maps to the action it stands for. */
class KeySkill(
    private val service: AccessibilityService,
    private val clipboard: ClipboardSkill,
) {
    private val audio get() = service.getSystemService(AudioManager::class.java)

    suspend fun press(combo: String): ActionResult = when (combo.trim().lowercase()) {
        "ctrl+a" -> onFocused { clipboard.select(it, null, null) }
        "ctrl+c" -> onFocused { clipboard.copy(it) }
        "ctrl+x" -> onFocused { clipboard.cut(it) }
        "ctrl+v" -> onFocused { clipboard.paste(it) }
        "enter" -> onFocused {
            if (it.performAction(AccessibilityAction.ACTION_IME_ENTER.id)) ActionResult.ok("Pressed Enter.")
            else ActionResult.fail("KEY_FAILED", "Enter did not work here. Tap the submit button instead.")
        }
        "volume_up" -> volume(AudioManager.ADJUST_RAISE)
        "volume_down" -> volume(AudioManager.ADJUST_LOWER)
        "mute" -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
        "power+volume_down" -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        "dpad_up" -> dpad(AccessibilityService.GLOBAL_ACTION_DPAD_UP)
        "dpad_down" -> dpad(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN)
        "dpad_left" -> dpad(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT)
        "dpad_right" -> dpad(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
        "dpad_center" -> dpad(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
        "headset" ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                global(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
            } else {
                unsupported(combo)
            }
        else -> unsupported(combo)
    }

    private suspend fun onFocused(action: suspend (AccessibilityNodeInfo) -> ActionResult): ActionResult {
        val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult.fail("NO_FOCUS", "No input has focus. Tap one first, or use the *_text tools with an element number.")
        return action(node)
    }

    private fun volume(direction: Int): ActionResult {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return ActionResult.ok("Volume changed.", selfVerified = true)
    }

    private fun dpad(action: Int): ActionResult =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) global(action) else unsupported("dpad on this Android version")

    private fun global(action: Int): ActionResult =
        if (service.performGlobalAction(action)) ActionResult.ok("Pressed.") else ActionResult.fail("KEY_FAILED", "That key did not work.")

    private fun unsupported(combo: String) =
        ActionResult.fail("UNSUPPORTED_KEY", "$combo isn't supported. Android doesn't let apps send arbitrary keys. Use tap or the other tools.")
}
