package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {
    private val box = Bounds(0, 0, 10, 10)

    @Test
    fun callWordsAreRecognisedInEnglishAndHindi() {
        listOf("answer the call", "pick up", "call Rahul", "hang up", "put it on speaker", "फोन उठाओ", "रवि को कॉल करो")
            .forEach { assertTrue(it, SafetyPolicy.asksAboutCalls(it)) }
        listOf("Compare the price of Amul milk on Zepto", "bring up YouTube", "open settings", "string lights on Amazon")
            .forEach { assertFalse(it, SafetyPolicy.asksAboutCalls(it)) }
    }

    @Test
    fun callActionsNeedTheUserToHaveAskedForThem() {
        val screen = ScreenSnapshot("com.samsung.android.incallui", emptyList())
        assertTrue(SafetyPolicy.check(AgentAction.AnswerCall, screen, "compare milk prices") is SafetyDecision.Refuse)
        assertTrue(SafetyPolicy.check(AgentAction.EndCall, screen, "open youtube") is SafetyDecision.Refuse)
        assertEquals(SafetyDecision.Allow, SafetyPolicy.check(AgentAction.AnswerCall, screen, "answer it"))
    }

    @Test
    fun blocksKnownPaymentApps() {
        assertTrue(SafetyPolicy.isBlockedApp("net.one97.paytm"))
        assertTrue(SafetyPolicy.isBlockedApp("com.phonepe.app"))
    }

    @Test
    fun blocksBankAndUpiPackageSegments() {
        assertTrue(SafetyPolicy.isBlockedApp("com.example.mybank.mobile"))
        assertTrue(SafetyPolicy.isBlockedApp("in.org.npci.upiapp"))
    }

    @Test
    fun doesNotBlockWordsThatOnlyContainUpi() {
        assertFalse(SafetyPolicy.isBlockedApp("com.stupidgame.app"))
    }

    @Test
    fun tapInBlockedAppStopsTheTask() {
        val decision = SafetyPolicy.check(AgentAction.Tap(1), ScreenSnapshot("net.one97.paytm", emptyList()))
        assertTrue(decision is SafetyDecision.StopTask)
    }

    @Test
    fun leavingBlockedAppIsAllowed() {
        val screen = ScreenSnapshot("net.one97.paytm", emptyList())
        assertEquals(SafetyDecision.Allow, SafetyPolicy.check(AgentAction.Press(DeviceKey.HOME), screen))
        assertEquals(SafetyDecision.Allow, SafetyPolicy.check(AgentAction.CompleteTask("opened"), screen))
    }

    @Test
    fun typingIntoPasswordFieldIsRefused() {
        val field = UiElement(1, ElementRole.INPUT, "Password", box, editable = true, isPassword = true)
        val decision = SafetyPolicy.check(AgentAction.TypeText(1, "x", false), ScreenSnapshot("a.b", listOf(field)))
        assertTrue(decision is SafetyDecision.Refuse)
    }

    @Test
    fun clipboardActionsOnPasswordFieldsAreRefused() {
        val field = UiElement(1, ElementRole.INPUT, "Password", box, editable = true, isPassword = true)
        val screen = ScreenSnapshot("a.b", listOf(field))
        listOf(
            AgentAction.SelectText(1, null, null),
            AgentAction.CopyText(1),
            AgentAction.CutText(1),
            AgentAction.PasteText(1),
        ).forEach { assertTrue(SafetyPolicy.check(it, screen) is SafetyDecision.Refuse) }
    }

    @Test
    fun normalActionAllowed() {
        val decision = SafetyPolicy.check(AgentAction.Tap(1), ScreenSnapshot("com.whatsapp", emptyList()))
        assertEquals(SafetyDecision.Allow, decision)
    }
}
