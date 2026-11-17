package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.AgentEvent
import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    private val events = mutableListOf<AgentEvent>()
    private val questions = mutableListOf<String>()
    private var userAnswer = "Yes"

    private fun loop(llm: FakeLlm, device: FakeDevice, config: AgentConfig = AgentConfig()) = AgentLoop(
        llm, device, config,
        onEvent = { events += it },
        askUser = { q, _ -> questions += q; userAnswer },
    )

    private val finished get() = events.filterIsInstance<AgentEvent.Finished>().single()
    private fun statuses(tasks: List<AgentTask>) = tasks.map { it.status }

    @Test
    fun plainQuestionIsAnsweredWithoutActing() = runTest {
        val device = FakeDevice()
        loop(FakeLlm(call("answer", """{"text":"Hello"}""")), device).run("hi")
        assertTrue(finished.success)
        assertEquals("Hello", finished.summary)
        assertTrue(device.executed.isEmpty())
    }

    @Test
    fun aRingingCallIsNeverAnsweredForAnUnrelatedCommand() = runTest {
        // This happened on a real phone: a call rang during a price comparison and the agent answered it.
        val device = FakeDevice()
        val llm = FakeLlm(
            plan("Answer call", irreversible = true),
            call("answer_call"),
            call("fail_task", """{"reason":"Not asked to answer the call"}"""),
            call("fail_task", """{"reason":"Not asked"}"""),
            call("fail_task", """{"reason":"Not asked"}"""),
        )
        loop(llm, device).run("Compare the price of Amul Taaza milk on Zepto, Blinkit and Instamart")
        assertTrue(device.executed.none { it is AgentAction.AnswerCall })
        assertTrue(llm.requests[2].any { it.text.orEmpty().startsWith("REFUSED") })
    }

    @Test
    fun aCallIsAnsweredWhenTheUserAsks() = runTest {
        val device = FakeDevice()
        val llm = FakeLlm(plan("Answer call", irreversible = true), call("answer_call"), complete("answered"), verdict("passed"))
        loop(llm, device).run("pick up the call")
        assertTrue(AgentAction.AnswerCall in device.executed)
    }

    @Test
    fun taskCompletesOnlyAfterVerificationPasses() = runTest {
        val device = FakeDevice()
        val llm = FakeLlm(plan("A"), tap(1), complete("did A"), verdict("passed"))
        loop(llm, device).run("goal")
        assertEquals(listOf<AgentAction>(AgentAction.Tap(1)), device.executed)
        assertTrue(finished.success)
        assertEquals("did A", finished.summary)
        assertEquals(listOf(TaskStatus.DONE), statuses(finished.tasks))
    }

    @Test
    fun checklistIsPublishedBeforeWorkStarts() = runTest {
        loop(FakeLlm(plan("A", "B"), complete(), verdict("passed"), complete(), verdict("passed")), FakeDevice()).run("goal")
        val first = events.filterIsInstance<AgentEvent.TasksChanged>().first()
        assertEquals(listOf("A", "B"), first.tasks.map { it.title })
        assertEquals(listOf(TaskStatus.PENDING, TaskStatus.PENDING), statuses(first.tasks))
    }

    @Test
    fun tasksRunInOrderAndLastSummaryIsTheResult() = runTest {
        val llm = FakeLlm(plan("A", "B"), complete("a"), verdict("passed"), complete("b"), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals("b", finished.summary)
        assertEquals(listOf(TaskStatus.DONE, TaskStatus.DONE), statuses(finished.tasks))
    }

    @Test
    fun failedVerificationRetriesWithReason() = runTest {
        val device = FakeDevice()
        val llm = FakeLlm(plan("A"), complete(), verdict("failed", "nothing changed"), tap(1), complete("ok"), verdict("passed"))
        loop(llm, device).run("goal")
        val retrying = events.filterIsInstance<AgentEvent.TasksChanged>()
            .flatMap { it.tasks }.first { it.status == TaskStatus.RETRYING }
        assertEquals(2, retrying.attempt)
        assertTrue(retrying.note!!.contains("nothing changed"))
        assertTrue(finished.success)
        val retryBrief = llm.requests[3].first { it.role == "user" }.text!!
        assertTrue(retryBrief.contains("nothing changed"))
    }

    @Test
    fun exhaustedAttemptsFailTheCommand() = runTest {
        val script = buildList {
            add(plan("A", "B"))
            repeat(3) { add(complete()); add(verdict("failed", "still wrong")) }
        }.toTypedArray()
        loop(FakeLlm(*script), FakeDevice()).run("goal")
        assertFalse(finished.success)
        assertEquals(listOf(TaskStatus.FAILED, TaskStatus.PENDING), statuses(finished.tasks))
        assertTrue(finished.summary.contains("still wrong"))
    }

    @Test
    fun uncertainOnNormalTaskRetries() = runTest {
        val llm = FakeLlm(plan("A"), complete(), verdict("uncertain"), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertTrue(finished.success)
        assertTrue(questions.isEmpty())
    }

    @Test
    fun uncertainOnIrreversibleTaskAsksTheUser() = runTest {
        val llm = FakeLlm(plan("Send", irreversible = true), complete(), verdict("uncertain"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals(1, questions.size)
        assertTrue(finished.success)
        assertEquals(listOf(TaskStatus.DONE), statuses(finished.tasks))
    }

    @Test
    fun userSayingNoToIrreversibleCheckRetries() = runTest {
        userAnswer = "No"
        val llm = FakeLlm(plan("Send", irreversible = true), complete(), verdict("uncertain"), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals(TaskStatus.DONE, finished.tasks.single().status)
        assertEquals(2, finished.tasks.single().attempt)
    }

    @Test
    fun retryOfIrreversibleTaskWarnsAgainstDuplicates() = runTest {
        val llm = FakeLlm(plan("Send", irreversible = true), complete(), verdict("failed"), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertTrue(llm.requests[3].first { it.role == "user" }.text!!.contains("did not already happen"))
    }

    @Test
    fun selfVerifiedActionSkipsTheVerifier() = runTest {
        val device = FakeDevice(results = { ActionResult.ok("Flashlight on", selfVerified = true) })
        val llm = FakeLlm(plan("Torch"), call("set_flashlight", """{"on":true}"""), complete("Your light's on!"))
        loop(llm, device).run("goal")
        assertEquals(3, llm.requests.size)
        assertEquals("Your light's on!", finished.summary)
        assertEquals(TaskStatus.DONE, finished.tasks.single().status)
    }

    @Test
    fun readingInformationDoesNotEndTheTask() = runTest {
        val device = FakeDevice()
        val llm = FakeLlm(
            plan("Open Gmail"),
            call("list_apps"),
            call("open_app", """{"name":"Gmail"}"""),
            complete("Gmail's open."),
            verdict("passed"),
        )
        loop(llm, device).run("open gmail")
        assertEquals(listOf(AgentAction.ListApps, AgentAction.OpenApp("Gmail")), device.executed)
        assertEquals("Gmail's open.", finished.summary)
    }

    @Test
    fun readResultNeedsNoVerifier() = runTest {
        val llm = FakeLlm(plan("Battery"), call("get_device_status"), complete("You're at 80%."))
        loop(llm, FakeDevice()).run("battery?")
        assertEquals(3, llm.requests.size)
        assertTrue(finished.success)
    }

    @Test
    fun actionErrorGoesBackToTheModel() = runTest {
        val device = FakeDevice(results = {
            if (it == AgentAction.Tap(9)) ActionResult.fail("ELEMENT_NOT_FOUND", "no such element") else ActionResult.ok("done")
        })
        val llm = FakeLlm(plan("A"), tap(9), tap(1), complete(), verdict("passed"))
        loop(llm, device).run("goal")
        val toolMessage = llm.requests[2].last { it.role == "tool" }
        assertTrue(toolMessage.text!!.contains("ELEMENT_NOT_FOUND"))
        assertTrue(finished.success)
    }

    @Test
    fun malformedToolCallIsReportedOnce() = runTest {
        val llm = FakeLlm(plan("A"), call("fly"), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertTrue(llm.requests[2].last { it.role == "tool" }.text!!.contains("fly"))
        assertTrue(finished.success)
    }

    @Test
    fun secondMalformedToolCallEndsTheAttempt() = runTest {
        val llm = FakeLlm(plan("A"), call("fly"), call("fly"))
        loop(llm, FakeDevice(), AgentConfig(maxAttempts = 1)).run("goal")
        assertFalse(finished.success)
    }

    @Test
    fun blockedAppStopsWithoutRetrying() = runTest {
        val device = FakeDevice(screen = labelledScreen("net.one97.paytm"))
        val llm = FakeLlm(plan("Pay"), tap(1))
        loop(llm, device).run("goal")
        assertTrue(device.executed.isEmpty())
        assertEquals(2, llm.requests.size)
        assertFalse(finished.success)
        assertTrue(finished.summary.contains("payment"))
        assertEquals(TaskStatus.FAILED, finished.tasks.single().status)
    }

    @Test
    fun passwordFieldIsRefusedAndTheModelIsTold() = runTest {
        val device = FakeDevice()
        val llm = FakeLlm(plan("A"), call("type_text", """{"element":3,"text":"1234"}"""), complete(), verdict("passed"))
        loop(llm, device).run("goal")
        assertTrue(device.executed.isEmpty())
        assertTrue(llm.requests[2].last { it.role == "tool" }.text!!.contains("password"))
    }

    @Test
    fun repeatedActionOnSameScreenHintsThenFails() = runTest {
        val llm = FakeLlm(plan("A"), tap(1), tap(1), tap(1))
        loop(llm, FakeDevice(), AgentConfig(maxAttempts = 1)).run("goal")
        assertTrue(llm.requests[3].any { it.role == "user" && it.text!!.contains("no effect") })
        assertFalse(finished.success)
        assertTrue(finished.summary.contains("stuck", ignoreCase = true))
    }

    @Test
    fun stepLimitPerTaskEndsTheAttempt() = runTest {
        val llm = FakeLlm(plan("A"), tap(1), tap(2))
        loop(llm, FakeDevice(), AgentConfig(maxStepsPerTask = 2, maxAttempts = 1)).run("goal")
        assertFalse(finished.success)
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun totalStepLimitStopsWithoutRetrying() = runTest {
        val llm = FakeLlm(plan("A"), tap(1), tap(2))
        loop(llm, FakeDevice(), AgentConfig(maxTotalSteps = 2, maxAttempts = 3)).run("goal")
        assertFalse(finished.success)
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun askUserPausesAndFeedsTheAnswerBack() = runTest {
        userAnswer = "Rahul Verma"
        val llm = FakeLlm(plan("A"), call("ask_user", """{"question":"Which Rahul?","options":["Rahul Verma"]}"""), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals(listOf("Which Rahul?"), questions)
        assertEquals("Which Rahul?", events.filterIsInstance<AgentEvent.Asking>().single().question)
        assertTrue(llm.requests[2].last { it.role == "tool" }.text!!.contains("Rahul Verma"))
    }

    @Test
    fun retryRemembersWhatTheUserAlreadyAnswered() = runTest {
        userAnswer = "Riya"
        val llm = FakeLlm(
            plan("Fill form"),
            call("ask_user", """{"question":"What's your name?"}"""),
            complete(),
            verdict("failed", "email is empty"),
            complete(),
            verdict("passed"),
        )
        loop(llm, FakeDevice()).run("goal")
        val retryBrief = llm.requests[4].first { it.role == "user" }.text!!
        assertTrue(retryBrief.contains("What's your name? Riya"))
    }

    private val formPlan = call(
        "create_plan",
        """{"tasks":[{"title":"Fill form","success_check":"filled","irreversible":false,"fills_form":true}]}""",
    )

    @Test
    fun formTaskIsFilledByAskingTheUser() = runTest {
        userAnswer = "Riya"
        val device = FakeDevice()
        val field = """{"label":"Name","question":"Your name?","kind":"text","element":1}"""
        val llm = FakeLlm(
            formPlan,
            call("report_form", """{"fields":[$field]}"""),
            call("report_form", """{"fields":[],"done_message":"All filled in!"}"""),
        )
        loop(llm, device).run("fill this form")
        assertEquals(listOf("Your name?"), questions)
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(1, "Riya", false)), device.executed)
        assertEquals("All filled in!", finished.summary)
    }

    @Test
    fun updatePlanReplacesPendingTasks() = runTest {
        val newTasks = """{"tasks":[{"title":"Log in","success_check":"signed in","irreversible":false}]}"""
        val llm = FakeLlm(plan("A", "B"), call("update_plan", newTasks), complete(), verdict("passed"), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals(listOf("A", "Log in"), finished.tasks.map { it.title })
        assertTrue(finished.success)
    }

    @Test
    fun screenshotAttachedWhenScreenIsUnreadable() = runTest {
        val device = FakeDevice(screen = ScreenSnapshot("com.example", emptyList()))
        val llm = FakeLlm(plan("A"), complete(), verdict("passed"))
        loop(llm, device).run("goal")
        assertEquals("img", llm.requests[1].last().imageJpegBase64)
    }

    @Test
    fun noScreenshotWhenVisionDisabled() = runTest {
        val device = FakeDevice(screen = ScreenSnapshot("com.example", emptyList()))
        val llm = FakeLlm(plan("A"), complete(), verdict("passed"))
        loop(llm, device, AgentConfig(visionEnabled = false)).run("goal")
        assertEquals(0, device.screenshots)
        assertNull(llm.requests[1].last().imageJpegBase64)
    }

    @Test
    fun lookAtScreenAddsScreenshotToNextStepOnly() = runTest {
        val llm = FakeLlm(plan("A"), call("look_at_screen"), tap(1), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertNull(llm.requests[1].last().imageJpegBase64)
        assertEquals("img", llm.requests[2].last().imageJpegBase64)
        assertNull(llm.requests[3].last().imageJpegBase64)
    }

    @Test
    fun onlyLatestScreenIsSent() = runTest {
        val llm = FakeLlm(plan("A"), tap(1), tap(2), complete(), verdict("passed"))
        loop(llm, FakeDevice()).run("goal")
        assertEquals(1, llm.requests[3].count { it.role == "user" && it.text!!.startsWith("Current screen") })
    }

    @Test
    fun llmFailureEndsWithMessage() = runTest {
        loop(FakeLlm(plan("A")), FakeDevice()).run("goal")
        assertFalse(finished.success)
        assertEquals("script exhausted", finished.summary)
    }

    @Test
    fun invalidPlanEndsWithMessage() = runTest {
        loop(FakeLlm(call("create_plan", """{"tasks":[]}""")), FakeDevice()).run("goal")
        assertFalse(finished.success)
    }
}
