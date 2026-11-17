package com.ishaara.mobile.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionTest {
    private val records = mutableListOf<RunRecord>()

    // Not backgroundScope: advanceUntilIdle does not run background work.
    // Tests that check a paused run use runCurrent: advanceUntilIdle would skip virtual time past the time limit.
    private fun TestScope.sessionScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    @Test
    fun runEndsInResultAndRecordsIt() = runTest {
        val llm = FakeLlm(plan("A"), complete("did it"), verdict("passed"))
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("goal", "hi-IN", spoken = true)
        advanceUntilIdle()
        val result = session.state.value as PanelState.Result
        assertTrue(result.success)
        assertEquals("did it", result.summary)
        val record = records.single()
        assertEquals("goal", record.command)
        assertEquals("hi-IN", record.languageCode)
        assertTrue(record.spoken)
        assertTrue(record.success)
    }

    @Test
    fun followUpCommandSeesTheEarlierExchange() = runTest {
        val llm = FakeLlm(call("answer", """{"text":"Canberra"}"""), call("answer", """{"text":"About 450,000"}"""))
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("capital of australia", null, spoken = false)
        advanceUntilIdle()
        session.start("how many people live there", null, spoken = false)
        advanceUntilIdle()
        val prompt = llm.requests[1].first { it.role == "user" }.text!!
        assertTrue(prompt.contains("User: capital of australia"))
        assertTrue(prompt.contains("Ishaara: Canberra"))
    }

    @Test
    fun aQuestionStraightAfterAnAnswerIsNotLost() = runTest {
        // Unconfined resumes inside answer(), as the app's main dispatcher does.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val first = """{"label":"Time","question":"First?","kind":"manual"}"""
        val second = """{"label":"Note","question":"Second?","kind":"manual"}"""
        val llm = FakeLlm(
            call("create_plan", """{"tasks":[{"title":"Form","success_check":"done","irreversible":false,"fills_form":true}]}"""),
            call("report_form", """{"fields":[$first,$second]}"""),
            call("report_form", """{"fields":[]}"""),
        )
        val session = AgentSession(scope, llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("fill this form", null, spoken = false)
        runCurrent()
        session.answer("Done")
        runCurrent()
        assertEquals("Second?", (session.state.value as PanelState.Asking).question)
        session.answer("Done")
        runCurrent()
        assertTrue((session.state.value as PanelState.Result).success)
    }

    @Test
    fun answerWithNothingPendingIsIgnored() = runTest {
        val session = AgentSession(sessionScope(), FakeLlm(), { FakeDevice() }, { AgentConfig() }, { records += it })
        session.answer("late reply")
        assertEquals(PanelState.Idle, session.state.value)
    }

    @Test
    fun noDeviceMeansFriendlyFailure() = runTest {
        val session = AgentSession(sessionScope(), FakeLlm(), { null }, { AgentConfig() }, { records += it })
        session.start("goal", null, spoken = false)
        advanceUntilIdle()
        val result = session.state.value as PanelState.Result
        assertFalse(result.success)
        assertTrue(result.summary.contains("accessibility", ignoreCase = true))
    }

    @Test
    fun questionPausesThenAnswerResumes() = runTest {
        val llm = FakeLlm(plan("A"), call("ask_user", """{"question":"Which?","options":["X"]}"""), complete(), verdict("passed"))
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("goal", null, spoken = false)
        runCurrent()
        val asking = session.state.value as PanelState.Asking
        assertEquals("Which?", asking.question)
        assertEquals(listOf("X"), asking.options)
        session.answer("X")
        advanceUntilIdle()
        assertTrue((session.state.value as PanelState.Result).success)
    }

    @Test
    fun stopCancelsAndReturnsToIdleWithoutRecording() = runTest {
        val gate = CompletableDeferred<ToolCall>()
        val llm = object : LlmClient {
            override suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall = gate.await()
        }
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("goal", null, spoken = false)
        runCurrent()
        assertTrue(session.state.value is PanelState.Running)
        assertTrue(session.isBusy)
        session.stop()
        runCurrent()
        assertEquals(PanelState.Idle, session.state.value)
        assertFalse(session.isBusy)
        assertTrue(records.isEmpty())
    }

    @Test
    fun secondCommandIsIgnoredWhileRunning() = runTest {
        val gate = CompletableDeferred<ToolCall>()
        var calls = 0
        val llm = object : LlmClient {
            override suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall {
                calls++
                return gate.await()
            }
        }
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("one", null, spoken = false)
        runCurrent()
        session.start("two", null, spoken = false)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun dismissReturnsResultToIdle() = runTest {
        val session = AgentSession(sessionScope(), FakeLlm(call("answer", """{"text":"x"}""")), { FakeDevice() }, { AgentConfig() }, { records += it })
        session.start("goal", null, spoken = false)
        advanceUntilIdle()
        session.dismiss()
        assertEquals(PanelState.Idle, session.state.value)
    }

    @Test
    fun timeLimitEndsTheRun() = runTest {
        val gate = CompletableDeferred<ToolCall>()
        val llm = object : LlmClient {
            override suspend fun complete(messages: List<ChatMessage>, tools: JsonArray): ToolCall = gate.await()
        }
        val session = AgentSession(sessionScope(), llm, { FakeDevice() }, { AgentConfig() }, { records += it }, timeLimitMs = 1000)
        session.start("goal", null, spoken = false)
        advanceUntilIdle()
        val result = session.state.value as PanelState.Result
        assertFalse(result.success)
        assertTrue(result.summary.contains("too long"))
    }
}
