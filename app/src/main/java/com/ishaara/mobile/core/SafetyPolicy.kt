package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.ScreenSnapshot

sealed interface SafetyDecision {
    data object Allow : SafetyDecision
    /** Refuse this action; the agent may try something else. */
    data class Refuse(val reason: String) : SafetyDecision
    /** End the command and hand back to the user. */
    data class StopTask(val reason: String) : SafetyDecision
}

object SafetyPolicy {
    private val blockedPackages = setOf(
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping.pay",
        "com.sbi.lotusintouch",
        "com.snapwork.hdfc",
        "com.csam.icici.bank.imobile",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
    )

    fun isBlockedApp(packageName: String): Boolean {
        if (packageName in blockedPackages) return true
        return packageName.lowercase().split('.').any { it.contains("bank") || it.startsWith("upi") }
    }

    private val callWords = Regex(
        """\b(call|calls|calling|phone|dial|ring|answer|pick(\s?it)?\s?up|reject|decline|hang\s?up|end\s(the|it|this)|speaker|mute|hold)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val callWordsIndic = listOf("कॉल", "फ़ोन", "फोन", "उठा", "काट", "डायल", "स्पीकर", "फोन लगा")

    /** True if the user's own words are about a phone call. */
    fun asksAboutCalls(command: String): Boolean =
        callWords.containsMatchIn(command) || callWordsIndic.any { it in command }

    /**
     * [command] is what the user asked for. Calls are checked against it, not against the screen: once, a
     * ringing call made the agent answer it in the middle of an unrelated price comparison.
     */
    fun check(action: AgentAction, screen: ScreenSnapshot, command: String? = null): SafetyDecision {
        val isCall = action is AgentAction.AnswerCall || action is AgentAction.EndCall || action is AgentAction.MakeCall
        if (isCall && command != null && !asksAboutCalls(command)) {
            return SafetyDecision.Refuse(
                "The user didn't ask about a phone call, so leave any call alone and carry on with the task, or call fail_task.",
            )
        }
        if (isBlockedApp(screen.packageName)) {
            val allowed = action is AgentAction.Press ||
                action is AgentAction.AskUser ||
                action is AgentAction.CompleteTask ||
                action is AgentAction.FailTask
            if (!allowed) {
                return SafetyDecision.StopTask(
                    "This is a payment or banking app, so Ishaara won't act here. Please take over.",
                )
            }
        }
        val target = when (action) {
            is AgentAction.TypeText -> action.element
            is AgentAction.SelectText -> action.element
            is AgentAction.CopyText -> action.element
            is AgentAction.CutText -> action.element
            is AgentAction.PasteText -> action.element
            else -> null
        }
        if (target != null && screen.elements.firstOrNull { it.id == target }?.isPassword == true) {
            return SafetyDecision.Refuse("Ishaara does not touch password fields. Ask the user to do it.")
        }
        return SafetyDecision.Allow
    }
}
