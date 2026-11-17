package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.MediaCommand
import com.ishaara.mobile.core.model.PlanDecision
import com.ishaara.mobile.core.model.PlannedTask
import com.ishaara.mobile.core.model.PriceLookup
import com.ishaara.mobile.core.model.ScrollDirection
import com.ishaara.mobile.core.model.SettingsSection
import com.ishaara.mobile.core.model.Verdict
import com.ishaara.mobile.core.model.Verification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Extends IllegalArgumentException so wrong JSON shapes and bad values are handled the same way. */
class ToolParseException(message: String) : IllegalArgumentException(message)

object ToolParser {
    private const val MAX_TASKS = 8

    fun parseAction(call: ToolCall): AgentAction {
        val a = Args(call)
        return when (call.name) {
            "tap" -> AgentAction.Tap(a.int("element"))
            "long_press" -> AgentAction.LongPress(a.int("element"))
            "type_text" -> AgentAction.TypeText(a.int("element"), a.str("text"), a.boolOr("submit", false))
            "scroll" -> AgentAction.Scroll(a.enum<ScrollDirection>("direction"), a.intOrNull("element"))
            "press" -> AgentAction.Press(a.enum<DeviceKey>("key"))
            "wait" -> AgentAction.Wait(a.int("seconds").coerceIn(1, 5))
            "tap_point" -> AgentAction.TapPoint(a.int("x"), a.int("y"))
            "open_app" -> AgentAction.OpenApp(a.str("name"))
            "open_url" -> AgentAction.OpenUrl(a.str("url"))
            "whatsapp_send" -> AgentAction.WhatsAppSend(a.str("contact"), a.str("message"))
            "send_sms" -> AgentAction.SendSms(a.str("contact"), a.str("message"))
            "youtube_search" -> AgentAction.YouTubeSearch(a.str("query"))
            "play_store_search" -> AgentAction.PlayStoreSearch(a.str("query"))
            "media_control" -> AgentAction.Media(a.enum<MediaCommand>("command"))
            "make_call" -> AgentAction.MakeCall(a.str("target"))
            "answer_call" -> AgentAction.AnswerCall
            "end_call" -> AgentAction.EndCall
            "show_call_screen" -> AgentAction.ShowCallScreen
            "set_alarm" -> AgentAction.SetAlarm(a.int("hour"), a.int("minute"), a.strOrNull("label"))
            "set_timer" -> AgentAction.SetTimer(a.int("seconds"), a.strOrNull("label"))
            "open_alarms" -> AgentAction.OpenAlarms
            "set_flashlight" ->AgentAction.SetFlashlight(a.bool("on"))
            "open_camera" -> AgentAction.OpenCamera(a.boolOr("video", false))
            "take_photo" -> AgentAction.TakePhoto(a.boolOr("selfie", false))
            "open_settings" -> AgentAction.OpenSettings(a.enum<SettingsSection>("section"))
            "close_app" -> AgentAction.CloseApp(a.str("name"))
            "get_running_apps" -> AgentAction.GetRunningApps
            "get_media_state" -> AgentAction.GetMediaState
            "get_ui_tree" -> AgentAction.GetUiTree
            "read_clipboard" -> AgentAction.ReadClipboard
            "set_clipboard" -> AgentAction.SetClipboard(a.str("text"))
            "select_text" -> AgentAction.SelectText(a.int("element"), a.intOrNull("start"), a.intOrNull("end"))
            "copy_text" -> AgentAction.CopyText(a.int("element"))
            "cut_text" -> AgentAction.CutText(a.int("element"))
            "paste_text" -> AgentAction.PasteText(a.int("element"))
            "press_keys" -> AgentAction.PressKeys(a.str("combo"))
            "swipe" -> AgentAction.Swipe(a.enum<ScrollDirection>("direction"), a.intOrNull("element"))
            "double_tap" -> AgentAction.DoubleTap(a.int("element"))
            "drag" -> AgentAction.Drag(a.int("from_element"), a.int("to_element"))
            "read_notifications" -> AgentAction.ReadNotifications
            "get_device_status" -> AgentAction.GetDeviceStatus
            "list_apps" -> AgentAction.ListApps
            "find_contact" -> AgentAction.FindContact(a.str("name"))
            "add_contact" -> AgentAction.AddContact(a.str("name"), a.str("phone"))
            "open_split_screen" -> AgentAction.SplitScreen(a.str("first_app"), a.str("second_app"))
            "exit_split_screen" -> AgentAction.ExitSplitScreen
            "search_in_app" -> AgentAction.SearchInApp(a.str("app"), a.str("query"))
            "web_search" -> AgentAction.WebSearch(a.str("query"))
            "navigate_to" -> AgentAction.Navigate(a.str("place"))
            "set_volume" -> AgentAction.SetVolume(a.int("percent").coerceIn(0, 100))
            "look_at_screen" -> AgentAction.LookAtScreen
            "ask_user" -> AgentAction.AskUser(a.str("question"), a.strList("options"))
            "update_plan" -> AgentAction.UpdatePlan(a.tasks())
            "complete_task" -> AgentAction.CompleteTask(a.str("summary"))
            "fail_task" -> AgentAction.FailTask(a.str("reason"))
            else -> throw ToolParseException("Unknown tool: ${call.name}")
        }
    }

    fun parsePlan(call: ToolCall): PlanDecision {
        val a = Args(call)
        return when (call.name) {
            "answer" -> PlanDecision.Answer(a.str("text"))
            "create_plan" -> {
                val tasks = a.tasks()
                if (tasks.isEmpty() || tasks.size > MAX_TASKS) {
                    throw ToolParseException("A plan needs 1 to $MAX_TASKS tasks, got ${tasks.size}")
                }
                PlanDecision.Plan(tasks)
            }
            else -> throw ToolParseException("Unknown planner tool: ${call.name}")
        }
    }

    fun parseVerification(call: ToolCall): Verification {
        if (call.name != "report_verification") throw ToolParseException("Unknown verifier tool: ${call.name}")
        val a = Args(call)
        return Verification(a.enum<Verdict>("verdict"), a.str("reason"))
    }

    private class Args(call: ToolCall) {
        val json: JsonObject = try {
            Json.parseToJsonElement(call.argumentsJson.ifBlank { "{}" }).jsonObject
        } catch (e: IllegalArgumentException) {
            throw ToolParseException("Arguments for ${call.name} are not valid JSON")
        }

        fun strOrNull(name: String): String? = json[name]?.jsonPrimitive?.contentOrNull
        fun str(name: String): String = strOrNull(name) ?: missing(name)
        fun intOrNull(name: String): Int? = json[name]?.jsonPrimitive?.intOrNull
        fun int(name: String): Int = intOrNull(name) ?: missing(name)
        fun bool(name: String): Boolean = json[name]?.jsonPrimitive?.booleanOrNull ?: missing(name)
        fun boolOr(name: String, default: Boolean): Boolean = json[name]?.jsonPrimitive?.booleanOrNull ?: default

        inline fun <reified E : Enum<E>> enum(name: String): E {
            val raw = str(name).uppercase()
            return enumValues<E>().firstOrNull { it.name == raw }
                ?: throw ToolParseException("Invalid value for $name: $raw")
        }

        fun strList(name: String): List<String> =
            (json[name] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        fun tasks(): List<PlannedTask> {
            val array = json["tasks"] as? JsonArray ?: missing("tasks")
            return array.map {
                val t = it.jsonObject
                PlannedTask(
                    title = t["title"]?.jsonPrimitive?.contentOrNull ?: missing("title"),
                    successCheck = t["success_check"]?.jsonPrimitive?.contentOrNull ?: missing("success_check"),
                    irreversible = t["irreversible"]?.jsonPrimitive?.booleanOrNull ?: false,
                    interactive = t["fills_form"]?.jsonPrimitive?.booleanOrNull ?: false,
                    price = t["price_app"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { app ->
                        t["price_item"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { PriceLookup(app, it) }
                    },
                )
            }
        }

        private fun missing(name: String): Nothing = throw ToolParseException("Missing argument: $name")
    }
}
