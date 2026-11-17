package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.UiElement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormFillerTest {
    private val asked = mutableListOf<String>()
    private val replies = ArrayDeque<String>()

    private fun page(json: String) = call("report_form", json)

    private fun filler(llm: FakeLlm, device: FakeDevice, spoken: Boolean = false, goal: String = "fill this form") = FormFiller(
        llm, device, null, goal, visionEnabled = true,
        isSpoken = { spoken },
        onStep = {},
        ask = { q, _ -> asked += q; replies.removeFirst() },
    )

    private val nameField = """{"label":"Name","question":"What's your name?","kind":"text","element":1}"""
    private val emailField = """{"label":"Email","question":"What's your email?","kind":"email","element":2}"""
    private val sizeField = """{"label":"Size","question":"Which size?","kind":"choice","options":[{"label":"Small","element":3},{"label":"Medium","element":4}]}"""
    private val empty = """{"fields":[]}"""

    @Test
    fun asksEachFieldInOrderThenFinishes() = runTest {
        replies += listOf("Kavya", "kavya@gmail.com", "medium please")
        val device = FakeDevice()
        val llm = FakeLlm(page("""{"fields":[$nameField,$emailField,$sizeField]}"""), page(empty))
        val result = filler(llm, device).run()
        assertEquals(listOf("What's your name?", "What's your email?", "Which size?"), asked)
        assertEquals(
            listOf<AgentAction>(
                AgentAction.TypeText(1, "Kavya", false),
                AgentAction.TypeText(2, "kavya@gmail.com", false),
                AgentAction.Tap(4),
            ),
            device.executed,
        )
        assertTrue(result.success)
    }

    private fun webForm(dy: Int, firstId: Int) = ScreenSnapshot(
        "com.android.chrome",
        listOf(
            UiElement(firstId, ElementRole.BUTTON, "Home", Bounds(0, 0, 10, 10), clickable = true),
            UiElement(firstId + 1, ElementRole.TEXT, "Name:", Bounds(0, 100 + dy, 50, 120 + dy)),
            UiElement(firstId + 2, ElementRole.TEXT, "Size", Bounds(0, 200 + dy, 50, 220 + dy)),
            UiElement(firstId + 3, ElementRole.INPUT, "", Bounds(60, 100 + dy, 200, 120 + dy), editable = true),
        ),
    )

    private val webName = """{"label":"Name","question":"What's your name?","kind":"text","element":4}"""

    @Test
    fun anUnnamedFieldIsFoundAfterThePageScrolls() = runTest {
        val device = FakeDevice(webForm(dy = 0, firstId = 1))
        val llm = FakeLlm(page("""{"fields":[$webName]}"""), page(empty))
        val filler = FormFiller(llm, device, null, "fill this form", true, { false }, {}, { q, _ ->
            asked += q
            device.screen = webForm(dy = -50, firstId = 11)
            "Kavya"
        })
        filler.run()
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(14, "Kavya", false)), device.executed)
    }

    @Test
    fun aFieldThatCantBeMatchedIsReadAgainNotTappedByItsOldNumber() = runTest {
        val device = FakeDevice(webForm(dy = 0, firstId = 1))
        val moved = """{"label":"Name","question":"What's your name?","kind":"text","element":31}"""
        val llm = FakeLlm(page("""{"fields":[$webName]}"""), page("""{"fields":[$moved]}"""), page(empty))
        val filler = FormFiller(llm, device, null, "fill this form", true, { false }, {}, { q, _ ->
            asked += q
            device.screen = labelledScreen()
            "Kavya"
        })
        filler.run()
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(31, "Kavya", false)), device.executed)
    }

    private fun checkboxes(firstId: Int) = ScreenSnapshot(
        "com.android.chrome",
        listOf(
            UiElement(firstId, ElementRole.BUTTON, "Home", Bounds(0, 0, 10, 10), clickable = true),
            UiElement(firstId + 1, ElementRole.SWITCH, "", Bounds(0, 100, 10, 110), clickable = true, checked = false),
            UiElement(firstId + 2, ElementRole.SWITCH, "", Bounds(0, 200, 10, 210), clickable = true, checked = false),
        ),
    )

    @Test
    fun everyOptionIsMatchedAgainstTheScreenTheFormWasReadFrom() = runTest {
        // Each new look at the screen numbers elements afresh. Looking the second option up by its old
        // number on the newer screen once tapped the browser's Home button.
        val device = FakeDevice(checkboxes(firstId = 1))
        val toppings = """{"label":"Toppings","question":"Which toppings?","kind":"multi","options":[{"label":"Cheese","element":2},{"label":"Onion","element":3}]}"""
        val llm = FakeLlm(page("""{"fields":[$toppings]}"""), page(empty))
        var looks = 0
        val counting = object : Device by device {
            override suspend fun observe(): ScreenSnapshot = checkboxes(firstId = 1 + 10 * looks++)
        }
        FormFiller(llm, counting, null, "fill this form", true, { false }, {}, { q, _ -> asked += q; "Cheese and onion" }).run()
        val taps = device.executed.map { (it as AgentAction.Tap).element }
        assertEquals(2, taps.size)
        assertEquals(listOf(12, 23), taps)
    }

    @Test
    fun aLostOptionIsNeverTappedByItsOldNumber() = runTest {
        val device = FakeDevice(webForm(dy = 0, firstId = 1))
        val toppings = """{"label":"Toppings","question":"Which toppings?","kind":"multi","options":[{"label":"Onion","element":3}]}"""
        val llm = FakeLlm(page("""{"fields":[$toppings]}"""), page(empty), page(empty))
        val filler = FormFiller(llm, device, null, "fill this form", true, { false }, {}, { q, _ ->
            asked += q
            device.screen = labelledScreen()
            "Onion"
        })
        filler.run()
        assertTrue(device.executed.isEmpty())
    }

    @Test
    fun aFieldReportedAgainIsNotAskedAgain() = runTest {
        replies += "Kavya"
        val llm = FakeLlm(page("""{"fields":[$nameField]}"""), page("""{"fields":[$nameField]}"""))
        filler(llm, FakeDevice()).run()
        assertEquals(1, asked.size)
    }

    @Test
    fun anInventedValueIsIgnoredAndTheUserIsAsked() = runTest {
        replies += "Kavya"
        val invented = """{"label":"Name","question":"Your name?","kind":"text","element":1,"value":"John Doe"}"""
        val device = FakeDevice()
        filler(FakeLlm(page("""{"fields":[$invented]}"""), page(empty)), device).run()
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(1, "Kavya", false)), device.executed)
    }

    @Test
    fun aValueFromTheCommandIsUsedWithoutAsking() = runTest {
        val given = """{"label":"Name","question":"Your name?","kind":"text","element":1,"value":"Kavya"}"""
        val device = FakeDevice()
        filler(FakeLlm(page("""{"fields":[$given]}"""), page(empty)), device, goal = "fill this, my name is Kavya").run()
        assertTrue(asked.isEmpty())
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(1, "Kavya", false)), device.executed)
    }

    @Test
    fun skippedFieldIsLeftEmpty() = runTest {
        replies += "Skip"
        val device = FakeDevice()
        filler(FakeLlm(page("""{"fields":[$nameField]}"""), page(empty)), device).run()
        assertTrue(device.executed.isEmpty())
    }

    @Test
    fun spokenEmailIsReadBackAndCanBeCorrected() = runTest {
        replies += listOf("Kavyashay at gmail dot com", "No", "kavya.shah@gmail.com")
        val device = FakeDevice()
        filler(FakeLlm(page("""{"fields":[$emailField]}"""), page(empty)), device, spoken = true).run()
        assertTrue(asked[1].contains("kavyashay@gmail.com"))
        assertEquals(listOf<AgentAction>(AgentAction.TypeText(2, "kavya.shah@gmail.com", false)), device.executed)
    }

    @Test
    fun finalButtonNeedsConfirmation() = runTest {
        replies += "No"
        val device = FakeDevice()
        filler(FakeLlm(page("""{"fields":[],"action_element":9,"final":true}""")), device).run()
        assertTrue(device.executed.isEmpty())
    }

    @Test
    fun confirmedSubmitTapsTheButton() = runTest {
        replies += "Yes"
        val device = FakeDevice()
        val result = filler(FakeLlm(page("""{"fields":[],"action_element":9,"final":true,"done_message":"Sent!"}""")), device).run()
        assertEquals(listOf<AgentAction>(AgentAction.Tap(9)), device.executed)
        assertEquals("Sent!", result.summary)
    }

    @Test
    fun nextPageContinuesWithoutAsking() = runTest {
        replies += "Kavya"
        val device = FakeDevice()
        val llm = FakeLlm(page("""{"fields":[],"action_element":5,"final":false}"""), page("""{"fields":[$nameField]}"""), page(empty))
        filler(llm, device).run()
        assertEquals(AgentAction.Tap(5), device.executed.first())
    }

    @Test
    fun noFormIsReported() = runTest {
        val result = filler(FakeLlm(page("""{"no_form":true}""")), FakeDevice()).run()
        assertFalse(result.success)
    }

    @Test
    fun cleansSpokenEmails() {
        assertEquals("kavya@gmail.com", FormFiller.cleanEmail("Kavya at the rate gmail dot com."))
        assertEquals("kavya@gmail.com", FormFiller.cleanEmail("KAVYA@GMAIL.COM."))
    }

    @Test
    fun matchesOptions() {
        val toppings = listOf("Bacon", "Extra Cheese", "Onion")
        assertEquals("Extra Cheese", FormFiller.matchOption("cheese", toppings))
        assertEquals("Onion", FormFiller.matchOption("onion please", toppings))
        assertNull(FormFiller.matchOption("pineapple", toppings))
    }
}
