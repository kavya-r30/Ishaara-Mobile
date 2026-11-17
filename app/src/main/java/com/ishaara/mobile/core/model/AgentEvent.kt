package com.ishaara.mobile.core.model

sealed interface AgentEvent {
    data class TasksChanged(val tasks: List<AgentTask>) : AgentEvent
    data class Step(val text: String) : AgentEvent
    data class Asking(val question: String, val options: List<String>) : AgentEvent
    data class Finished(val success: Boolean, val summary: String, val tasks: List<AgentTask>) : AgentEvent
}
