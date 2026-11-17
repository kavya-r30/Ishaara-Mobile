package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.MediaCommand
import com.ishaara.mobile.core.model.PlanDecision
import com.ishaara.mobile.core.model.PlannedTask
import com.ishaara.mobile.core.model.ScrollDirection
import com.ishaara.mobile.core.model.SettingsSection
import com.ishaara.mobile.core.model.Verdict
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolParserTest {
    private fun call(name: String, args: String) = ToolCall("id", name, args)

    @Test
    fun parsesScreenActions() {
        assertEquals(AgentAction.Tap(5), ToolParser.parseAction(call("tap", """{"element":5}""")))
        assertEquals(
            AgentAction.TypeText(2, "hi", false),
            ToolParser.parseAction(call("type_text", """{"element":2,"text":"hi"}""")),
        )
        assertEquals(
            AgentAction.TypeText(2, "hi", true),
            ToolParser.parseAction(call("type_text", """{"element":2,"text":"hi","submit":true}""")),
        )
        assertEquals(
            AgentAction.Scroll(ScrollDirection.DOWN, null),
            ToolParser.parseAction(call("scroll", """{"direction":"down"}""")),
        )
        assertEquals(AgentAction.Press(DeviceKey.QUICK_SETTINGS), ToolParser.parseAction(call("press", """{"key":"quick_settings"}""")))
        assertEquals(AgentAction.TapPoint(10, 20), ToolParser.parseAction(call("tap_point", """{"x":10,"y":20}""")))
    }

    @Test
    fun parsesShortcuts() {
        assertEquals(
            AgentAction.WhatsAppSend("Rahul", "hi"),
            ToolParser.parseAction(call("whatsapp_send", """{"contact":"Rahul","message":"hi"}""")),
        )
        assertEquals(AgentAction.AnswerCall, ToolParser.parseAction(call("answer_call", "{}")))
        assertEquals(AgentAction.EndCall, ToolParser.parseAction(call("end_call", "{}")))
        assertEquals(AgentAction.SetFlashlight(true), ToolParser.parseAction(call("set_flashlight", """{"on":true}""")))
        assertEquals(AgentAction.SetAlarm(6, 30, null), ToolParser.parseAction(call("set_alarm", """{"hour":6,"minute":30}""")))
        assertEquals(AgentAction.Media(MediaCommand.NEXT), ToolParser.parseAction(call("media_control", """{"command":"next"}""")))
        assertEquals(AgentAction.OpenCamera(false), ToolParser.parseAction(call("open_camera", "{}")))
        assertEquals(AgentAction.OpenSettings(SettingsSection.WIFI), ToolParser.parseAction(call("open_settings", """{"section":"wifi"}""")))
    }

    @Test
    fun parsesAgentControl() {
        assertEquals(
            AgentAction.AskUser("Which?", listOf("A", "B")),
            ToolParser.parseAction(call("ask_user", """{"question":"Which?","options":["A","B"]}""")),
        )
        assertEquals(AgentAction.AskUser("Which?", emptyList()), ToolParser.parseAction(call("ask_user", """{"question":"Which?"}""")))
        assertEquals(AgentAction.CompleteTask("done"), ToolParser.parseAction(call("complete_task", """{"summary":"done"}""")))
        assertEquals(AgentAction.FailTask("no"), ToolParser.parseAction(call("fail_task", """{"reason":"no"}""")))
        assertEquals(
            AgentAction.UpdatePlan(listOf(PlannedTask("T", "C", true))),
            ToolParser.parseAction(call("update_plan", """{"tasks":[{"title":"T","success_check":"C","irreversible":true}]}""")),
        )
    }

    @Test
    fun parsesDeviceTools() {
        assertEquals(AgentAction.CloseApp("YouTube"), ToolParser.parseAction(call("close_app", """{"name":"YouTube"}""")))
        assertEquals(AgentAction.GetUiTree, ToolParser.parseAction(call("get_ui_tree", "{}")))
        assertEquals(AgentAction.SelectText(2, null, null), ToolParser.parseAction(call("select_text", """{"element":2}""")))
        assertEquals(AgentAction.SelectText(2, 1, 4), ToolParser.parseAction(call("select_text", """{"element":2,"start":1,"end":4}""")))
        assertEquals(AgentAction.PasteText(3), ToolParser.parseAction(call("paste_text", """{"element":3}""")))
        assertEquals(AgentAction.PressKeys("ctrl+v"), ToolParser.parseAction(call("press_keys", """{"combo":"ctrl+v"}""")))
    }

    @Test
    fun rejectsBadCalls() {
        assertThrows(ToolParseException::class.java) { ToolParser.parseAction(call("fly", "{}")) }
        assertThrows(ToolParseException::class.java) { ToolParser.parseAction(call("tap", "{}")) }
        assertThrows(ToolParseException::class.java) { ToolParser.parseAction(call("tap", "not json")) }
        assertThrows(ToolParseException::class.java) { ToolParser.parseAction(call("press", """{"key":"explode"}""")) }
    }

    @Test
    fun parsesPlanAndAnswer() {
        assertEquals(PlanDecision.Answer("Hello"), ToolParser.parsePlan(call("answer", """{"text":"Hello"}""")))
        assertEquals(
            PlanDecision.Plan(listOf(PlannedTask("Open", "Open is visible", false))),
            ToolParser.parsePlan(call("create_plan", """{"tasks":[{"title":"Open","success_check":"Open is visible","irreversible":false}]}""")),
        )
    }

    @Test
    fun rejectsEmptyPlanAndOversizedPlan() {
        assertThrows(ToolParseException::class.java) { ToolParser.parsePlan(call("create_plan", """{"tasks":[]}""")) }
        val nine = (1..9).joinToString(",") { """{"title":"t$it","success_check":"c","irreversible":false}""" }
        assertThrows(ToolParseException::class.java) { ToolParser.parsePlan(call("create_plan", """{"tasks":[$nine]}""")) }
    }

    @Test
    fun parsesVerification() {
        val v = ToolParser.parseVerification(call("report_verification", """{"verdict":"uncertain","reason":"cannot see"}"""))
        assertEquals(Verdict.UNCERTAIN, v.verdict)
        assertEquals("cannot see", v.reason)
    }

    @Test
    fun everyExecutorToolIsParseable() {
        val samples = mapOf(
            "open_app" to """{"name":"a"}""", "open_url" to """{"url":"https://a"}""",
            "whatsapp_send" to """{"contact":"a","message":"b"}""", "send_sms" to """{"contact":"a","message":"b"}""",
            "youtube_search" to """{"query":"a"}""", "play_store_search" to """{"query":"a"}""",
            "media_control" to """{"command":"play_pause"}""", "make_call" to """{"target":"a"}""",
            "answer_call" to "{}", "end_call" to "{}", "show_call_screen" to "{}",
            "set_alarm" to """{"hour":1,"minute":2}""", "set_timer" to """{"seconds":60}""",
            "open_alarms" to "{}", "set_flashlight" to """{"on":false}""", "open_camera" to """{"video":true}""", "take_photo" to """{"selfie":true}""",
            "open_settings" to """{"section":"main"}""", "close_app" to """{"name":"a"}""",
            "get_running_apps" to "{}", "get_media_state" to "{}", "get_ui_tree" to "{}", "read_clipboard" to "{}",
            "set_clipboard" to """{"text":"a"}""", "select_text" to """{"element":1}""",
            "copy_text" to """{"element":1}""", "cut_text" to """{"element":1}""", "paste_text" to """{"element":1}""",
            "press_keys" to """{"combo":"ctrl+c"}""", "swipe" to """{"direction":"left"}""",
            "double_tap" to """{"element":1}""", "drag" to """{"from_element":1,"to_element":2}""",
            "read_notifications" to "{}", "get_device_status" to "{}", "list_apps" to "{}",
            "find_contact" to """{"name":"a"}""", "add_contact" to """{"name":"a","phone":"1"}""",
            "open_split_screen" to """{"first_app":"a","second_app":"b"}""", "exit_split_screen" to "{}",
            "search_in_app" to """{"app":"a","query":"b"}""", "web_search" to """{"query":"a"}""",
            "navigate_to" to """{"place":"a"}""", "set_volume" to """{"percent":50}""", "tap" to """{"element":1}""",
            "long_press" to """{"element":1}""", "type_text" to """{"element":1,"text":"a"}""",
            "scroll" to """{"direction":"up"}""", "press" to """{"key":"back"}""", "wait" to """{"seconds":1}""",
            "look_at_screen" to "{}", "tap_point" to """{"x":1,"y":2}""", "ask_user" to """{"question":"a"}""",
            "update_plan" to """{"tasks":[{"title":"a","success_check":"b","irreversible":false}]}""",
            "complete_task" to """{"summary":"a"}""", "fail_task" to """{"reason":"a"}""",
        )
        val names = ToolSchema.EXECUTOR.map {
            it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content
        }
        assertEquals(samples.keys, names.toSet())
        names.forEach { name -> assertTrue(ToolParser.parseAction(call(name, samples.getValue(name))) is AgentAction) }
    }
}
