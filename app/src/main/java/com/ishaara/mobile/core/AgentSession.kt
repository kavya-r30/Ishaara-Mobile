package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentEvent
import com.ishaara.mobile.core.model.AgentTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface PanelState {
    data object Idle : PanelState
    data class Running(val tasks: List<AgentTask>, val step: String) : PanelState
    data class Asking(val question: String, val options: List<String>, val tasks: List<AgentTask>) : PanelState
    data class Result(val success: Boolean, val summary: String, val tasks: List<AgentTask>) : PanelState
}

data class RunRecord(
    val command: String,
    val languageCode: String?,
    val spoken: Boolean,
    val success: Boolean,
    val summary: String,
    val tasks: List<AgentTask>,
)

/** Runs one command at a time and turns its events into a single state for the UI. */
class AgentSession(
    private val scope: CoroutineScope,
    private val llm: LlmClient,
    private val deviceProvider: () -> Device?,
    private val configProvider: suspend () -> AgentConfig,
    private val onFinished: suspend (RunRecord) -> Unit,
    // Forms wait on the user between steps, so this only guards against a run that never ends.
    private val timeLimitMs: Long = 30 * 60 * 1000L,
    private val trace: (String) -> Unit = {},
) {
    private companion object {
        const val MAX_REMEMBERED = 4
    }

    private val _state = MutableStateFlow<PanelState>(PanelState.Idle)
    val state: StateFlow<PanelState> = _state

    private var job: Job? = null
    private var pendingAnswer: CompletableDeferred<String>? = null
    private var tasks: List<AgentTask> = emptyList()
    private val remembered = ArrayDeque<String>()

    val isBusy: Boolean get() = job?.isActive == true

    /** True while the conversation is by voice, so questions are spoken and listened for. */
    var spoken: Boolean = false
        private set

    var languageCode: String? = null
        private set

    fun start(command: String, languageCode: String?, spoken: Boolean) {
        if (isBusy) return
        this.spoken = spoken
        this.languageCode = languageCode
        tasks = emptyList()
        _state.value = PanelState.Running(emptyList(), "Thinking")
        val conversation = remembered.joinToString("\n")
        job = scope.launch {
            var result: PanelState.Result? = null
            try {
                val device = deviceProvider()
                if (device == null) {
                    result = PanelState.Result(false, "Turn on Ishaara in accessibility settings first.", emptyList())
                } else {
                    val loop = AgentLoop(
                        llm, device, configProvider(),
                        onEvent = { event ->
                            handle(event)
                            if (event is AgentEvent.Finished) {
                                result = PanelState.Result(event.success, event.summary, event.tasks)
                            }
                        },
                        askUser = { _, _ ->
                            val answer = CompletableDeferred<String>()
                            pendingAnswer = answer
                            answer.await()
                        },
                        trace = trace,
                        isSpoken = { this@AgentSession.spoken },
                    )
                    withTimeout(timeLimitMs) { loop.run(command, languageCode, conversation) }
                }
            } catch (e: TimeoutCancellationException) {
                result = PanelState.Result(false, "This took too long, so Ishaara stopped.", tasks)
            }
            result?.let { finished ->
                pendingAnswer = null
                _state.value = finished
                remember(command, finished.summary)
                onFinished(
                    RunRecord(command, languageCode, spoken, finished.success, finished.summary, finished.tasks),
                )
            }
        }
    }

    fun answer(text: String, spoken: Boolean = false) {
        val pending = pendingAnswer ?: return
        this.spoken = spoken
        pendingAnswer = null
        _state.value = PanelState.Running(tasks, "Continuing")
        // Last, because the run can resume inside this call and ask its next question straight away.
        pending.complete(text)
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingAnswer = null
        _state.value = PanelState.Idle
    }

    fun dismiss() {
        if (!isBusy) _state.value = PanelState.Idle
    }

    private fun remember(command: String, summary: String) {
        remembered.addLast("User: $command\nIshaara: $summary")
        while (remembered.size > MAX_REMEMBERED) remembered.removeFirst()
    }

    private fun handle(event: AgentEvent) {
        when (event) {
            is AgentEvent.TasksChanged -> {
                tasks = event.tasks
                val current = _state.value
                val step = (current as? PanelState.Running)?.step ?: ""
                if (current !is PanelState.Asking) _state.value = PanelState.Running(tasks, step)
            }
            is AgentEvent.Step -> _state.value = PanelState.Running(tasks, event.text)
            is AgentEvent.Asking -> _state.value = PanelState.Asking(event.question, event.options, tasks)
            is AgentEvent.Finished -> tasks = event.tasks
        }
    }
}
