package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private val done = AgentTask(1, "Open app", "app open", false, TaskStatus.DONE)
    private val current = AgentTask(2, "Send it", "message visible", true, TaskStatus.RUNNING)

    @Test
    fun executorTreatsScreenTextAsUntrusted() {
        assertTrue(Prompts.executor(null).contains("untrusted"))
    }

    @Test
    fun languageRuleAppearsOnlyWithLanguage() {
        assertTrue(Prompts.planner("hi-IN").contains("hi-IN"))
        assertFalse(Prompts.planner(null).contains("hi-IN"))
    }

    @Test
    fun briefCarriesEarlierResultsForward() {
        val checked = done.copy(title = "Check battery", note = "Battery 80%")
        val brief = Prompts.taskBrief("goal", listOf(checked, current), current, null)
        assertTrue(brief.contains("[done] Check battery (result: Battery 80%)"))
    }

    @Test
    fun briefShowsProgressAndCurrentTask() {
        val brief = Prompts.taskBrief("goal text", listOf(done, current), current, null)
        assertTrue(brief.contains("goal text"))
        assertTrue(brief.contains("[done] Open app"))
        assertTrue(brief.contains("Current task: Send it"))
        assertTrue(brief.contains("message visible"))
    }

    @Test
    fun retryOfIrreversibleTaskWarnsAgainstRepeating() {
        val brief = Prompts.taskBrief("g", listOf(current), current, "not visible")
        assertTrue(brief.contains("not visible"))
        assertTrue(brief.contains("did not already happen"))
    }

    @Test
    fun retryOfNormalTaskHasNoDuplicateWarning() {
        val normal = current.copy(irreversible = false)
        val brief = Prompts.taskBrief("g", listOf(normal), normal, "not visible")
        assertFalse(brief.contains("did not already happen"))
    }

    @Test
    fun hintsForKnownAppsOnly() {
        assertTrue(AppHints.forPackage("com.android.vending")!!.contains("Install"))
        assertNull(AppHints.forPackage("com.unknown.app"))
    }
}
