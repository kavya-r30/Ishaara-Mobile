package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFormatterTest {
    private val box = Bounds(0, 0, 10, 10)

    private fun el(id: Int, role: ElementRole, label: String, clickable: Boolean = false) =
        UiElement(id, role, label, box, clickable = clickable)

    @Test
    fun formatsEachRole() {
        val snapshot = ScreenSnapshot(
            "com.whatsapp",
            listOf(
                el(1, ElementRole.BUTTON, "Back", clickable = true),
                el(2, ElementRole.TEXT, "Rahul"),
                UiElement(3, ElementRole.INPUT, "Message", box, editable = true),
                UiElement(4, ElementRole.INPUT, "Search", box, editable = true, value = "cats"),
                UiElement(5, ElementRole.INPUT, "PIN", box, editable = true, isPassword = true),
                UiElement(6, ElementRole.SWITCH, "Wi-Fi", box, clickable = true, checked = true),
                el(7, ElementRole.BUTTON, "", clickable = true),
                UiElement(8, ElementRole.LIST, "", box, scrollable = true),
            ),
        )
        val expected = """
            App: com.whatsapp
            [1] button "Back"
            [2] text "Rahul"
            [3] input "Message" (empty)
            [4] input "Search" (value: "cats")
            [5] input "PIN" (password)
            [6] switch "Wi-Fi" (on)
            [7] button (no label)
            [8] list (no label) (scrollable)
        """.trimIndent()
        assertEquals(expected, ScreenFormatter.format(snapshot))
    }

    @Test
    fun emptyScreenSaysSo() {
        assertEquals(
            "App: x\n(no readable elements)",
            ScreenFormatter.format(ScreenSnapshot("x", emptyList())),
        )
    }

    @Test
    fun needsVisionWhenFewElements() {
        assertTrue(ScreenFormatter.needsVision(ScreenSnapshot("x", listOf(el(1, ElementRole.TEXT, "a")))))
    }

    @Test
    fun needsVisionWhenMostClickablesUnlabelled() {
        val elements = listOf(
            el(1, ElementRole.BUTTON, "", clickable = true),
            el(2, ElementRole.BUTTON, "", clickable = true),
            el(3, ElementRole.BUTTON, "Ok", clickable = true),
        )
        assertTrue(ScreenFormatter.needsVision(ScreenSnapshot("x", elements)))
    }

    @Test
    fun needsVisionWhenAnyInputIsUnnamed() {
        val elements = listOf(
            el(1, ElementRole.BUTTON, "One", clickable = true),
            el(2, ElementRole.BUTTON, "Two", clickable = true),
            UiElement(3, ElementRole.INPUT, "", box, editable = true),
        )
        assertTrue(ScreenFormatter.needsVision(ScreenSnapshot("x", elements)))
    }

    @Test
    fun noVisionForWellLabelledScreen() {
        val elements = listOf(
            el(1, ElementRole.BUTTON, "One", clickable = true),
            el(2, ElementRole.BUTTON, "Two", clickable = true),
            el(3, ElementRole.TEXT, "Three"),
        )
        assertFalse(ScreenFormatter.needsVision(ScreenSnapshot("x", elements)))
    }
}
