package com.ishaara.mobile.core.model

enum class TaskStatus { PENDING, RUNNING, RETRYING, DONE, FAILED }

data class PlannedTask(
    val title: String,
    val successCheck: String,
    val irreversible: Boolean,
    val interactive: Boolean = false,
    val price: PriceLookup? = null,
)

/** A task that finds one item's price in one shopping app. Code does it, so the price is really read. */
data class PriceLookup(val app: String, val item: String)

data class AgentTask(
    val id: Int,
    val title: String,
    val successCheck: String,
    val irreversible: Boolean,
    val status: TaskStatus = TaskStatus.PENDING,
    val attempt: Int = 1,
    val note: String? = null,
    /** Filling in a form with the user: values come only from the user. */
    val interactive: Boolean = false,
    val price: PriceLookup? = null,
)

enum class Verdict { PASSED, FAILED, UNCERTAIN }

data class Verification(val verdict: Verdict, val reason: String)

sealed interface PlanDecision {
    data class Answer(val text: String) : PlanDecision
    data class Plan(val tasks: List<PlannedTask>) : PlanDecision
}
