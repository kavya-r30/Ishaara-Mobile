package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.Screenshot
import com.ishaara.mobile.core.model.UiElement
import kotlinx.serialization.json.JsonArray

class FakeLlm(vararg calls: ToolCall) : LlmClient {
    private val queue = ArrayDeque(calls.toList())
    val requests = mutableListOf<List<ChatMessage>>()

    override suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall {
        requests += messages
        return queue.removeFirstOrNull() ?: throw LlmException("script exhausted")
    }
}

class FakeDevice(
    var screen: ScreenSnapshot = labelledScreen(),
    private val results: (AgentAction) -> ActionResult = { ActionResult.ok("done") },
) : Device {
    val executed = mutableListOf<AgentAction>()
    var screenshots = 0

    override suspend fun observe(): ScreenSnapshot = screen

    override suspend fun screenshot(snapshot: ScreenSnapshot): Screenshot {
        screenshots++
        return Screenshot("img", 1.0)
    }

    override suspend fun execute(action: AgentAction): ActionResult {
        executed += action
        return results(action)
    }
}

private val box = Bounds(0, 0, 10, 10)

fun labelledScreen(packageName: String = "com.example") = ScreenSnapshot(
    packageName,
    listOf(
        UiElement(1, ElementRole.BUTTON, "One", Bounds(0, 0, 10, 10), clickable = true),
        UiElement(2, ElementRole.BUTTON, "Two", Bounds(0, 20, 10, 30), clickable = true),
        UiElement(3, ElementRole.INPUT, "Pin", Bounds(0, 40, 10, 50), editable = true, isPassword = true),
    ),
)

private var nextCallId = 0

fun call(name: String, args: String = "{}") = ToolCall("call${nextCallId++}", name, args)

fun plan(vararg titles: String, irreversible: Boolean = false): ToolCall {
    val tasks = titles.joinToString(",") {
        """{"title":"$it","success_check":"$it is done","irreversible":$irreversible}"""
    }
    return call("create_plan", """{"tasks":[$tasks]}""")
}

fun complete(summary: String = "finished") = call("complete_task", """{"summary":"$summary"}""")
fun tap(element: Int) = call("tap", """{"element":$element}""")
fun verdict(value: String, reason: String = "because") =
    call("report_verification", """{"verdict":"$value","reason":"$reason"}""")
