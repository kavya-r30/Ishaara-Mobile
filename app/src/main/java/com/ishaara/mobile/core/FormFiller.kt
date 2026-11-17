package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.ScrollDirection
import com.ishaara.mobile.core.model.UiElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class FormOption(val label: String, val element: Int)

data class FormField(
    val label: String,
    val question: String,
    val kind: String,
    val element: Int?,
    val options: List<FormOption>,
    val value: String?,
)

data class FormPage(
    val noForm: Boolean,
    val fields: List<FormField>,
    val moreBelow: Boolean,
    val actionElement: Int?,
    val final: Boolean,
    val submitQuestion: String?,
    val doneMessage: String?,
)

data class FormResult(val success: Boolean, val summary: String)

/**
 * Fills in a form with the user. The model only reads each page. This code does the asking and typing
 * in order, because a model left to run the whole form repeats questions and invents values.
 */
class FormFiller(
    private val llm: LlmClient,
    private val device: Device,
    private val languageCode: String?,
    private val goal: String,
    private val visionEnabled: Boolean,
    private val isSpoken: () -> Boolean,
    private val onStep: suspend (String) -> Unit,
    private val ask: suspend (question: String, options: List<String>) -> String,
) {
    private val handled = mutableSetOf<String>()
    private val answers = mutableListOf<String>()
    private var formScreen: ScreenSnapshot? = null

    suspend fun run(): FormResult {
        var doneMessage: String? = null
        var scrolls = 0
        repeat(MAX_PAGES) { round ->
            onStep("Reading the form")
            val page = read()
            doneMessage = page.doneMessage ?: doneMessage
            if (page.noForm && round == 0) return FormResult(false, "I can't see a form on this screen.")

            val fresh = page.fields.filter { normal(it.label) !in handled }
            if (fresh.isNotEmpty()) {
                fresh.forEach { field ->
                    onStep("Filling in ${field.label}")
                    fill(field)
                    handled += normal(field.label)
                }
                return@repeat
            }
            if (page.moreBelow && scrolls < MAX_SCROLLS) {
                scrolls++
                device.execute(AgentAction.Scroll(ScrollDirection.DOWN, null))
                return@repeat
            }
            val action = page.actionElement ?: return finished(doneMessage)
            if (!page.final) {
                device.execute(AgentAction.Tap(action))
                handled.clear()
                scrolls = 0
                return@repeat
            }
            val question = page.submitQuestion ?: "All filled in. Shall I submit it?"
            if (!isYes(ask(question, listOf("Yes", "No")))) return FormResult(true, "All filled in. I've left it for you to check.")
            val button = current(action) ?: return FormResult(false, "Everything's filled in, but I lost the submit button. Could you tap it?")
            if (!device.execute(AgentAction.Tap(button)).ok) {
                return FormResult(false, "Everything's filled in, but the submit button didn't respond. Could you tap it?")
            }
            return finished(doneMessage)
        }
        return finished(doneMessage)
    }

    private fun finished(message: String?) = FormResult(true, message ?: "All done with the form.")

    /**
     * Asking opens the chat bar, which makes apps rebuild their screens, so element numbers from before
     * the question no longer apply. This finds the same element on the screen as it is now.
     */
    private suspend fun current(element: Int): Int? {
        // Element numbers belong to the screen the form was read from; every new read numbers things afresh.
        val before = formScreen
        val old = before?.elements?.firstOrNull { it.id == element }
        val now = device.observe()
        if (old == null) return element
        val sameKind = { e: UiElement -> e.editable == old.editable && (e.checked != null) == (old.checked != null) }
        val match = now.elements.firstOrNull { it.bounds == old.bounds && sameKind(it) }
            ?: now.elements.firstOrNull { old.label.isNotBlank() && it.label == old.label && sameKind(it) }
            ?: scrolledBy(before, now)?.let { (dx, dy) ->
                val moved = Bounds(old.bounds.left + dx, old.bounds.top + dy, old.bounds.right + dx, old.bounds.bottom + dy)
                now.elements.firstOrNull { it.bounds == moved && sameKind(it) }
            }
        return match?.id
    }

    /**
     * Fields on web pages often have no name of their own, so when the page scrolls they can only be found
     * by how far the named things around them moved. Unmoved items, like the browser's toolbar, don't count.
     */
    private fun scrolledBy(before: ScreenSnapshot, now: ScreenSnapshot): Pair<Int, Int>? {
        fun unique(screen: ScreenSnapshot) = screen.elements.filter { it.label.isNotBlank() }
            .groupBy { it.label }.filterValues { it.size == 1 }.mapValues { it.value.single() }
        val after = unique(now)
        return unique(before).mapNotNull { (label, old) ->
            val new = after[label] ?: return@mapNotNull null
            val sameSize = new.bounds.right - new.bounds.left == old.bounds.right - old.bounds.left &&
                new.bounds.bottom - new.bounds.top == old.bounds.bottom - old.bounds.top
            if (!sameSize) null else (new.bounds.left - old.bounds.left) to (new.bounds.top - old.bounds.top)
        }
            .filter { it != 0 to 0 }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.takeIf { it.value >= MIN_AGREEING }
            ?.key
    }

    /**
     * The field couldn't be matched, so the page is read again to find it. A stale element number is never
     * used instead: on a changed screen it can belong to anything, such as the browser's Home button.
     */
    private suspend fun relocate(field: FormField, option: String? = null): Int? {
        val found = read(lookingFor = field.label).fields.firstOrNull { normal(it.label) == normal(field.label) }
            ?: return null
        return if (option == null) found.element else found.options.firstOrNull { normal(it.label) == normal(option) }?.element
    }

    private suspend fun tapOption(field: FormField, option: FormOption) {
        val target = current(option.element) ?: relocate(field, option.label) ?: return
        device.execute(AgentAction.Tap(target))
    }

    private suspend fun read(lookingFor: String? = null): FormPage {
        val screen = device.observe()
        // A lookup for one field returns numbers for this screen only, to be used straight away.
        if (lookingFor == null) formScreen = screen
        val shot = if (visionEnabled) device.screenshot(screen) else null
        val text = buildString {
            append("The user's command: ").append(goal)
            if (answers.isNotEmpty()) append("\nAnswers so far:\n").append(answers.joinToString("\n"))
            if (lookingFor != null) {
                append("\nList only the field \"").append(lookingFor)
                    .append("\" with its current element numbers, even if it's already answered or filled in.")
            } else if (handled.isNotEmpty()) {
                append("\nAlready handled, leave these out: ").append(handled.joinToString())
            }
            append("\n\nCurrent screen:\n").append(ScreenFormatter.format(screen))
        }
        val call = llm.complete(
            listOf(ChatMessage.system(Prompts.formReader(languageCode)), ChatMessage.user(text, shot?.jpegBase64)),
            ToolSchema.FORM,
        )
        return parse(call)
    }

    private suspend fun fill(field: FormField) {
        when (field.kind) {
            "secret", "manual" -> ask(field.question, listOf("Done"))
            "choice" -> {
                val labels = field.options.map { it.label }
                val picked = matchOption(ask(field.question, labels + SKIP), labels)
                    ?: matchOption(askAgain(field, labels), labels)
                field.options.firstOrNull { it.label == picked }?.let { tapOption(field, it) }
            }
            "multi" -> {
                val answer = ask(field.question, listOf(SKIP))
                if (!isSkip(answer)) {
                    field.options.filter { matchOption(answer, listOf(it.label)) != null }
                        .forEach { tapOption(field, it) }
                }
            }
            "dropdown" -> fillDropdown(field)
            else -> fillText(field)
        }
    }

    private suspend fun askAgain(field: FormField, labels: List<String>): String =
        ask(field.question, labels + SKIP)

    private suspend fun fillDropdown(field: FormField) {
        val element = field.element ?: return
        val answer = ask(field.question, listOf(SKIP))
        if (isSkip(answer)) return
        val target = current(element) ?: relocate(field)
        if (target == null) {
            ask("I couldn't find ${field.label}. Could you set it yourself?", listOf("Done"))
            return
        }
        device.execute(AgentAction.Tap(target))
        val open = device.observe()
        val choice = open.elements.firstOrNull { it.clickable && matchOption(answer, listOf(it.label)) != null }
        if (choice != null) {
            device.execute(AgentAction.Tap(choice.id))
        } else {
            device.execute(AgentAction.Press(DeviceKey.BACK))
            ask("I couldn't pick that. Could you set ${field.label} yourself?", listOf("Done"))
        }
    }

    private suspend fun fillText(field: FormField) {
        val element = field.element ?: return
        val given = field.value?.takeIf { normal(goal + answers.joinToString(" ")).contains(normal(it)) }
        var value = given ?: ask(field.question, listOf(SKIP))
        if (isSkip(value)) return
        answers += "${field.label}: $value"
        if (field.kind == "email") {
            value = cleanEmail(value)
            if (given == null && isSpoken()) {
                val check = ask("I heard $value. Is that right?", listOf("Yes", "No"))
                if (!isYes(check)) value = cleanEmail(ask("Sorry! Could you type your email for me?", listOf(SKIP)))
                if (isSkip(value)) return
            }
        }
        if (field.kind == "phone" || field.kind == "number") value = value.filter { it.isLetterOrDigit() || it == '+' }
        val target = current(element) ?: relocate(field)
        val typed = target != null && device.execute(AgentAction.TypeText(target, value.trimEnd('.', '।', ' '), false)).ok
        if (!typed) {
            ask("I couldn't type into ${field.label}. Could you fill it in yourself?", listOf("Done"))
        }
    }

    companion object {
        private const val MAX_PAGES = 20
        private const val MAX_SCROLLS = 6
        private const val MIN_AGREEING = 2
        private const val SKIP = "Skip"

        private fun normal(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()

        fun isSkip(answer: String) = normal(answer).let { it.startsWith("skip") || it == "leaveitblank" || it == "छोड़ो" }

        fun isYes(answer: String) = answer.trim().lowercase().let { it.startsWith("yes") || it.startsWith("yeah") || it.startsWith("sure") || it.startsWith("हाँ") || it.startsWith("haan") }

        /** Finds the option the user meant, whichever way round the words contain each other. */
        fun matchOption(answer: String, labels: List<String>): String? {
            val said = normal(answer)
            if (said.isEmpty()) return null
            return labels.firstOrNull { normal(it) == said }
                ?: labels.firstOrNull { normal(it).contains(said) || said.contains(normal(it)) }
                ?: labels.firstOrNull { label -> label.split(' ').any { normal(it).length >= 3 && said.contains(normal(it)) } }
        }

        /** Spoken emails arrive with words and spaces: "kavya at gmail dot com". */
        fun cleanEmail(raw: String): String =
            raw.lowercase()
                .replace(" at the rate ", "@")
                .replace(" at ", "@")
                .replace(" dot ", ".")
                .filterNot { it.isWhitespace() }
                .trimEnd('.', '।')

        private fun parse(call: ToolCall): FormPage {
            if (call.name != "report_form") throw ToolParseException("Unknown form tool: ${call.name}")
            val json = try {
                Json.parseToJsonElement(call.argumentsJson).jsonObject
            } catch (e: IllegalArgumentException) {
                throw ToolParseException("Form report isn't valid JSON")
            }
            fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull
            fun JsonObject.number(name: String) = this[name]?.jsonPrimitive?.intOrNull
            fun JsonObject.flag(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false
            val fields = (json["fields"] as? JsonArray).orEmpty().map { item ->
                val f = item.jsonObject
                val label = f.text("label").orEmpty()
                FormField(
                    label = label,
                    question = f.text("question") ?: "What should I put for $label?",
                    kind = f.text("kind") ?: "text",
                    element = f.number("element"),
                    options = (f["options"] as? JsonArray).orEmpty().mapNotNull { option ->
                        val o = option.jsonObject
                        val element = o.number("element") ?: return@mapNotNull null
                        FormOption(o.text("label").orEmpty(), element)
                    },
                    value = f.text("value")?.takeIf { it.isNotBlank() },
                )
            }
            return FormPage(
                noForm = json.flag("no_form"),
                fields = fields,
                moreBelow = json.flag("more_below"),
                actionElement = json.number("action_element"),
                final = json.flag("final"),
                submitQuestion = json.text("submit_question"),
                doneMessage = json.text("done_message"),
            )
        }
    }
}
