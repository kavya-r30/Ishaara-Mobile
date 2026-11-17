package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.AgentEvent
import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.PlanDecision
import com.ishaara.mobile.core.model.PlannedTask
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.TaskStatus
import com.ishaara.mobile.core.model.Verdict
import com.ishaara.mobile.core.model.Verification
import com.ishaara.mobile.core.model.describe
import com.ishaara.mobile.core.model.isRead

data class AgentConfig(
    val visionEnabled: Boolean = true,
    val maxStepsPerTask: Int = 40,
    val maxTotalSteps: Int = 80,
    val maxAttempts: Int = 3,
)

/** Plans a command into tasks, then acts, verifies and retries each one. */
class AgentLoop(
    private val llm: LlmClient,
    private val device: Device,
    private val config: AgentConfig = AgentConfig(),
    private val onEvent: suspend (AgentEvent) -> Unit,
    private val askUser: suspend (question: String, options: List<String>) -> String,
    private val trace: (String) -> Unit = {},
    private val isSpoken: () -> Boolean = { false },
) {
    private var tasks: List<AgentTask> = emptyList()
    private var nextId = 1
    private var totalSteps = 0

    /** Kept across attempts, because a retry starts a fresh conversation with the model. */
    private val answers = mutableListOf<String>()

    private sealed interface Attempt {
        data class Completed(val summary: String) : Attempt
        data class Failed(val reason: String) : Attempt
        /** Retrying would not help. */
        data class Stopped(val reason: String) : Attempt
    }

    private sealed interface Outcome {
        data class Done(val summary: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun run(goal: String, languageCode: String? = null, conversation: String? = null) {
        try {
            runPlan(goal, languageCode, conversation)
        } catch (e: LlmException) {
            finish(false, e.message ?: "The assistant could not be reached.")
        } catch (e: IllegalArgumentException) {
            finish(false, "The assistant returned something invalid. Please try again.")
        }
    }

    private suspend fun runPlan(goal: String, languageCode: String?, conversation: String?) {
        val screen = device.observe()
        val earlier = if (conversation.isNullOrBlank()) "" else "Earlier in this conversation:\n$conversation\n\n"
        val planCall = llm.complete(
            listOf(
                ChatMessage.system(Prompts.planner(languageCode)),
                ChatMessage.user("${earlier}Command: $goal\n\nCurrent screen:\n${ScreenFormatter.format(screen)}"),
            ),
            ToolSchema.PLANNER,
        )
        trace("plan: ${planCall.name} ${planCall.argumentsJson}")
        when (val decision = ToolParser.parsePlan(planCall)) {
            is PlanDecision.Answer -> {
                finish(true, decision.text)
                return
            }
            is PlanDecision.Plan -> {
                tasks = decision.tasks.map(::toTask)
                publish()
            }
        }
        var summary = ""
        while (true) {
            val next = tasks.firstOrNull { it.status == TaskStatus.PENDING } ?: break
            when (val outcome = runTask(goal, languageCode, next.id)) {
                is Outcome.Done -> summary = outcome.summary
                is Outcome.Failed -> {
                    finish(false, outcome.reason)
                    return
                }
            }
        }
        finish(true, summary)
    }

    private suspend fun runTask(goal: String, languageCode: String?, id: Int): Outcome {
        var failure: String? = null
        for (attempt in 1..config.maxAttempts) {
            update(id) {
                copy(
                    status = if (attempt == 1) TaskStatus.RUNNING else TaskStatus.RETRYING,
                    attempt = attempt,
                    note = failure,
                )
            }
            when (val result = runAttempt(goal, languageCode, id, failure)) {
                is Attempt.Completed -> {
                    // The note carries this task's result to later tasks, e.g. a value they need to send.
                    update(id) { copy(status = TaskStatus.DONE, note = result.summary) }
                    return Outcome.Done(result.summary)
                }
                is Attempt.Failed -> failure = result.reason
                is Attempt.Stopped -> {
                    update(id) { copy(status = TaskStatus.FAILED, note = result.reason) }
                    return Outcome.Failed(result.reason)
                }
            }
        }
        update(id) { copy(status = TaskStatus.FAILED, note = failure) }
        return Outcome.Failed("I couldn't finish \"${task(id).title}\". ${failure.orEmpty()}".trim())
    }

    private suspend fun runAttempt(goal: String, languageCode: String?, id: Int, failure: String?): Attempt {
        if (task(id).interactive) return fillForm(goal, languageCode)
        task(id).price?.let { lookup ->
            val found = PriceChecker(llm, device, lookup, config.visionEnabled) {
                trace("task $id price: $it")
                onEvent(AgentEvent.Step(it))
            }.run()
            trace("task $id price result: $found")
            return Attempt.Completed(found)
        }
        val history = mutableListOf(
            ChatMessage.system(Prompts.executor(languageCode)),
            ChatMessage.user(Prompts.taskBrief(goal, tasks, task(id), failure, answers)),
        )
        val stuck = StuckDetector()
        val log = mutableListOf<String>()
        var wantScreenshot = false
        var malformed = 0
        // The last action's result is proof in itself, so the verifier isn't needed. The model still
        // decides when the task is finished, because a read may only be one step of it.
        var lastActionConfirmed = false

        for (step in 1..config.maxStepsPerTask) {
            if (totalSteps >= config.maxTotalSteps) return Attempt.Stopped("This took too many steps, so Ishaara stopped.")
            totalSteps++

            val screen = look(wantScreenshot)
            wantScreenshot = false
            val call = llm.complete(history + screenMessage(screen, task(id)), ToolSchema.EXECUTOR)
            trace("task $id step $step on ${screen.packageName}: ${call.name} ${call.argumentsJson}")
            history += ChatMessage.assistant(call)

            val action = try {
                ToolParser.parseAction(call)
            } catch (e: IllegalArgumentException) {
                malformed++
                if (malformed > 1) return Attempt.Failed("The assistant made invalid requests.")
                history += ChatMessage.tool(call, "ERROR: ${call.name}: ${e.message}")
                continue
            }

            when (val decision = SafetyPolicy.check(action, screen, goal)) {
                is SafetyDecision.StopTask -> return Attempt.Stopped(decision.reason)
                is SafetyDecision.Refuse -> {
                    history += ChatMessage.tool(call, "REFUSED: ${decision.reason}")
                    continue
                }
                SafetyDecision.Allow -> Unit
            }

            onEvent(AgentEvent.Step(action.describe(screen)))

            when (action) {
                is AgentAction.CompleteTask -> {
                    if (lastActionConfirmed) return Attempt.Completed(action.summary)
                    val check = verify(task(id), action.summary, log)
                    return settle(check, action.summary, task(id))
                }
                is AgentAction.FailTask -> return Attempt.Failed(action.reason)
                is AgentAction.LookAtScreen -> {
                    wantScreenshot = config.visionEnabled
                    history += ChatMessage.tool(
                        call,
                        if (config.visionEnabled) "A screenshot comes with the next screen." else "Screenshots are off. Use the element list.",
                    )
                }
                is AgentAction.AskUser -> {
                    onEvent(AgentEvent.Asking(action.question, action.options))
                    val answer = askUser(action.question, action.options)
                    answers += "${action.question} ${answer}"
                    publish()
                    history += ChatMessage.tool(call, "The user answered: $answer")
                }
                is AgentAction.UpdatePlan -> {
                    tasks = tasks.filter { it.status != TaskStatus.PENDING } + action.tasks.take(8).map(::toTask)
                    publish()
                    history += ChatMessage.tool(call, "Plan updated. Continue with the current task.")
                }
                else -> {
                    val result = device.execute(action)
                    log += "${call.name} ${call.argumentsJson}: ${result.code} ${result.message}"
                    lastActionConfirmed = result.ok && (result.selfVerified || action.isRead)
                    val next = if (lastActionConfirmed) " Confirmed. If the task is finished, call complete_task." else ""
                    history += ChatMessage.tool(call, "${result.code}: ${result.message}$next")
                    when (stuck.record(ScreenFormatter.format(screen), "${call.name} ${call.argumentsJson}")) {
                        StuckSignal.OK -> Unit
                        StuckSignal.HINT -> history += ChatMessage.user(
                            "Your last action had no effect on the screen. Try something different.",
                        )
                        StuckSignal.STOP -> return Attempt.Failed("Ishaara got stuck repeating the same action.")
                    }
                }
            }
        }
        return Attempt.Failed("Ran out of steps for this task.")
    }

    private suspend fun fillForm(goal: String, languageCode: String?): Attempt {
        val filler = FormFiller(
            llm, device, languageCode, goal, config.visionEnabled, isSpoken,
            onStep = {
                trace("task form: $it")
                onEvent(AgentEvent.Step(it))
            },
            ask = { question, options ->
                trace("task form: ask_user {\"question\":\"$question\"}")
                onEvent(AgentEvent.Asking(question, options))
                val answer = askUser(question, options)
                publish()
                answer
            },
        )
        val result = filler.run()
        return if (result.success) Attempt.Completed(result.summary) else Attempt.Stopped(result.summary)
    }

    private suspend fun verify(task: AgentTask, claim: String, log: List<String>): Verification {
        val screen = look(false)
        val text = buildString {
            append("Task: ").append(task.title)
            append("\nSuccess check: ").append(task.successCheck)
            append("\nThe agent claims: ").append(claim)
            tasks.filter { it.status == TaskStatus.DONE && !it.note.isNullOrBlank() }.forEach {
                append("\nEarlier result: ").append(it.title).append(": ").append(it.note)
            }
            append("\nActions and results:\n")
            if (log.isEmpty()) append("(none in this attempt)\n") else log.forEach { append("- ").append(it).append('\n') }
            append("\nCurrent screen:\n").append(ScreenFormatter.format(screen))
        }
        val call = llm.complete(
            listOf(ChatMessage.system(Prompts.VERIFIER), ChatMessage.user(text, screen.screenshot?.jpegBase64)),
            ToolSchema.VERIFIER,
        )
        trace("verify task ${task.id}: ${call.argumentsJson}")
        return ToolParser.parseVerification(call)
    }

    private suspend fun settle(check: Verification, summary: String, task: AgentTask): Attempt = when (check.verdict) {
        Verdict.PASSED -> Attempt.Completed(summary)
        Verdict.FAILED -> Attempt.Failed(check.reason)
        Verdict.UNCERTAIN -> {
            if (task.irreversible) {
                val question = "Ishaara can't confirm this from the screen. Did \"${task.title}\" happen?"
                val options = listOf("Yes", "No")
                onEvent(AgentEvent.Asking(question, options))
                val answer = askUser(question, options)
                publish()
                if (answer.trim().startsWith("yes", ignoreCase = true) || answer.trim().startsWith("हाँ")) {
                    Attempt.Completed(summary)
                } else {
                    Attempt.Failed("The user said it did not happen.")
                }
            } else {
                Attempt.Failed(check.reason)
            }
        }
    }

    private suspend fun look(wantScreenshot: Boolean): ScreenSnapshot {
        val snapshot = device.observe()
        if (!config.visionEnabled) return snapshot
        if (!wantScreenshot && !ScreenFormatter.needsVision(snapshot)) return snapshot
        return snapshot.copy(screenshot = device.screenshot(snapshot))
    }

    private fun screenMessage(screen: ScreenSnapshot, task: AgentTask): ChatMessage {
        val text = buildString {
            append("Current screen:\n").append(ScreenFormatter.format(screen))
            AppHints.forPackage(screen.packageName)?.let { append("\n\nTips for this app: ").append(it) }
            if (screen.screenshot != null) {
                append("\n\nA screenshot is attached. Numbered boxes match the element numbers.")
            }
            if (screen.elements.isEmpty()) {
                append("\n\nThe element list is empty. If a screenshot is attached and shows a normal app screen, act on it with tap_point. If it shows a permission dialog, or there is no screenshot, or it is black, this is a protected page such as a permission prompt or password entry: never assume what it shows, don't tap blindly, and call ask_user with a short request for the user to handle this screen, offering the option \"Done\". If you already asked and it is still unreadable, call fail_task saying this screen can't be read, and don't ask again.")
            }
            append("\n\nCurrent task: ").append(task.title)
            append("\nSuccess check: ").append(task.successCheck)
            append("\nIf this already holds on the screen, call complete_task now. Do nothing beyond this task; later tasks handle the rest.")
        }
        return ChatMessage.user(text, screen.screenshot?.jpegBase64)
    }

    private fun toTask(planned: PlannedTask) =
        AgentTask(
            nextId++, planned.title, planned.successCheck, planned.irreversible,
            interactive = planned.interactive, price = planned.price,
        )

    private fun task(id: Int): AgentTask = tasks.first { it.id == id }

    private suspend fun update(id: Int, change: AgentTask.() -> AgentTask) {
        tasks = tasks.map { if (it.id == id) it.change() else it }
        publish()
    }

    private suspend fun publish() = onEvent(AgentEvent.TasksChanged(tasks))

    private suspend fun finish(success: Boolean, summary: String) {
        trace("finished: success=$success $summary")
        onEvent(AgentEvent.Finished(success, summary, tasks))
    }
}
