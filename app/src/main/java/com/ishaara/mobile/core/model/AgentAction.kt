package com.ishaara.mobile.core.model

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
enum class DeviceKey { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SCREENSHOT }
enum class MediaCommand { PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, STOP }
enum class SettingsSection { MAIN, WIFI, BLUETOOTH, MOBILE_DATA, SOUND, DISPLAY, BATTERY, LOCATION, APPS }

sealed interface AgentAction {
    // Screen interaction
    data class Tap(val element: Int) : AgentAction
    data class LongPress(val element: Int) : AgentAction
    data class TypeText(val element: Int, val text: String, val submit: Boolean) : AgentAction
    data class Scroll(val direction: ScrollDirection, val element: Int?) : AgentAction
    data class Press(val key: DeviceKey) : AgentAction
    data class Wait(val seconds: Int) : AgentAction
    data class TapPoint(val x: Int, val y: Int) : AgentAction

    // Shortcuts
    data class OpenApp(val name: String) : AgentAction
    data class OpenUrl(val url: String) : AgentAction
    data class WhatsAppSend(val contact: String, val message: String) : AgentAction
    data class SendSms(val contact: String, val message: String) : AgentAction
    data class YouTubeSearch(val query: String) : AgentAction
    data class PlayStoreSearch(val query: String) : AgentAction
    data class Media(val command: MediaCommand) : AgentAction
    data class MakeCall(val target: String) : AgentAction
    data object AnswerCall : AgentAction
    data object EndCall : AgentAction
    data object ShowCallScreen : AgentAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : AgentAction
    data class SetTimer(val seconds: Int, val label: String?) : AgentAction
    data object OpenAlarms : AgentAction
    data class SetFlashlight(val on: Boolean) : AgentAction
    data class OpenCamera(val video: Boolean) : AgentAction
    data class TakePhoto(val selfie: Boolean) : AgentAction
    data class OpenSettings(val section: SettingsSection) : AgentAction

    // Device tools
    data class CloseApp(val name: String) : AgentAction
    data object GetRunningApps : AgentAction
    data object GetMediaState : AgentAction
    data object GetUiTree : AgentAction
    data object ReadClipboard : AgentAction
    data class SetClipboard(val text: String) : AgentAction
    data class SelectText(val element: Int, val start: Int?, val end: Int?) : AgentAction
    data class CopyText(val element: Int) : AgentAction
    data class CutText(val element: Int) : AgentAction
    data class PasteText(val element: Int) : AgentAction
    data class PressKeys(val combo: String) : AgentAction
    data class Swipe(val direction: ScrollDirection, val element: Int?) : AgentAction
    data class DoubleTap(val element: Int) : AgentAction
    data class Drag(val fromElement: Int, val toElement: Int) : AgentAction
    data object ReadNotifications : AgentAction
    data object GetDeviceStatus : AgentAction
    data object ListApps : AgentAction
    data class FindContact(val name: String) : AgentAction
    data class AddContact(val name: String, val phone: String) : AgentAction
    data class SplitScreen(val first: String, val second: String) : AgentAction
    data object ExitSplitScreen : AgentAction
    data class SearchInApp(val app: String, val query: String) : AgentAction
    data class WebSearch(val query: String) : AgentAction
    data class Navigate(val place: String) : AgentAction
    data class SetVolume(val percent: Int) : AgentAction

    // Agent control: handled by AgentLoop, never reach Device
    data object LookAtScreen : AgentAction
    data class AskUser(val question: String, val options: List<String>) : AgentAction
    data class UpdatePlan(val tasks: List<PlannedTask>) : AgentAction
    data class CompleteTask(val summary: String) : AgentAction
    data class FailTask(val reason: String) : AgentAction
}

data class ActionResult(
    val ok: Boolean,
    val code: String,
    val message: String,
    /** True when the action confirmed its own effect, so the verifier can be skipped. */
    val selfVerified: Boolean = false,
) {
    companion object {
        fun ok(message: String, selfVerified: Boolean = false) = ActionResult(true, "OK", message, selfVerified)
        fun fail(code: String, message: String) = ActionResult(false, code, message)
    }
}

/** A read's result is the information itself, and the screen doesn't change. */
val AgentAction.isRead: Boolean
    get() = when (this) {
        AgentAction.GetRunningApps, AgentAction.GetMediaState, AgentAction.ReadNotifications,
        AgentAction.GetDeviceStatus, AgentAction.ListApps, AgentAction.ReadClipboard,
        is AgentAction.FindContact -> true
        else -> false
    }

/** Short present-tense text for the checklist's secondary line. */
fun AgentAction.describe(screen: ScreenSnapshot?): String {
    fun label(id: Int?): String =
        screen?.elements?.firstOrNull { it.id == id }?.label?.takeIf { it.isNotBlank() } ?: "item $id"
    return when (this) {
        is AgentAction.Tap -> "Tapping ${label(element)}"
        is AgentAction.LongPress -> "Long pressing ${label(element)}"
        is AgentAction.TypeText -> "Typing \"$text\""
        is AgentAction.Scroll -> "Scrolling ${direction.name.lowercase()}"
        is AgentAction.Press -> "Pressing ${key.name.lowercase().replace('_', ' ')}"
        is AgentAction.Wait -> "Waiting for the screen"
        is AgentAction.TapPoint -> "Tapping the screen"
        is AgentAction.OpenApp -> "Opening $name"
        is AgentAction.OpenUrl -> "Opening link"
        is AgentAction.WhatsAppSend -> "Messaging $contact on WhatsApp"
        is AgentAction.SendSms -> "Writing a text to $contact"
        is AgentAction.YouTubeSearch -> "Searching YouTube for $query"
        is AgentAction.PlayStoreSearch -> "Searching Play Store for $query"
        is AgentAction.Media -> "Controlling playback"
        is AgentAction.MakeCall -> "Calling $target"
        AgentAction.AnswerCall -> "Answering the call"
        AgentAction.EndCall -> "Ending the call"
        AgentAction.ShowCallScreen -> "Opening the call screen"
        is AgentAction.SetAlarm -> "Setting an alarm"
        is AgentAction.SetTimer -> "Starting a timer"
        AgentAction.OpenAlarms -> "Opening your alarms"
        is AgentAction.SetFlashlight -> if (on) "Turning on the flashlight" else "Turning off the flashlight"
        is AgentAction.OpenCamera -> "Opening the camera"
        is AgentAction.TakePhoto -> if (selfie) "Taking a selfie" else "Taking a photo"
        is AgentAction.OpenSettings -> "Opening settings"
        is AgentAction.CloseApp -> "Closing $name"
        AgentAction.GetRunningApps -> "Checking recent apps"
        AgentAction.GetMediaState -> "Checking what's playing"
        AgentAction.GetUiTree -> "Reading the screen in detail"
        AgentAction.ReadClipboard -> "Reading the clipboard"
        is AgentAction.SetClipboard -> "Copying to the clipboard"
        is AgentAction.SelectText -> "Selecting text in ${label(element)}"
        is AgentAction.CopyText -> "Copying ${label(element)}"
        is AgentAction.CutText -> "Cutting text from ${label(element)}"
        is AgentAction.PasteText -> "Pasting into ${label(element)}"
        is AgentAction.PressKeys -> "Pressing $combo"
        is AgentAction.Swipe -> "Swiping ${direction.name.lowercase()}"
        is AgentAction.DoubleTap -> "Double tapping ${label(element)}"
        is AgentAction.Drag -> "Dragging ${label(fromElement)}"
        AgentAction.ReadNotifications -> "Reading notifications"
        AgentAction.GetDeviceStatus -> "Checking the phone's status"
        AgentAction.ListApps -> "Checking installed apps"
        is AgentAction.FindContact -> "Looking up $name"
        is AgentAction.AddContact -> "Adding $name to contacts"
        is AgentAction.SplitScreen -> "Opening $first and $second side by side"
        AgentAction.ExitSplitScreen -> "Closing split screen"
        is AgentAction.SearchInApp -> "Searching $app for $query"
        is AgentAction.WebSearch -> "Searching the web"
        is AgentAction.Navigate -> "Getting directions to $place"
        is AgentAction.SetVolume -> "Setting the volume"
        AgentAction.LookAtScreen -> "Looking at the screen"
        is AgentAction.AskUser -> "Waiting for your answer"
        is AgentAction.UpdatePlan -> "Updating the plan"
        is AgentAction.CompleteTask -> "Checking the result"
        is AgentAction.FailTask -> "Trying another way"
    }
}
