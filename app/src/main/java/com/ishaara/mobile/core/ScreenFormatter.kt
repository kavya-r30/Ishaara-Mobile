package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.UiElement

object ScreenFormatter {
    fun format(snapshot: ScreenSnapshot): String = buildString {
        append("App: ").append(snapshot.packageName)
        if (snapshot.elements.isEmpty()) {
            append("\n(no readable elements)")
        } else {
            snapshot.elements.forEach { append('\n').append(line(it)) }
        }
    }

    fun needsVision(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.elements.size < 3) return true
        // Web forms often leave inputs unnamed in the tree while showing their labels on screen.
        if (snapshot.elements.any { it.editable && it.label.isBlank() }) return true
        val clickable = snapshot.elements.filter { it.clickable }
        return clickable.isNotEmpty() && clickable.count { it.label.isBlank() } * 2 > clickable.size
    }

    private fun line(e: UiElement): String {
        val role = when (e.role) {
            ElementRole.BUTTON -> "button"
            ElementRole.INPUT -> "input"
            ElementRole.TEXT -> "text"
            ElementRole.IMAGE -> "image"
            ElementRole.LIST -> "list"
            ElementRole.SWITCH -> "switch"
        }
        val label = if (e.label.isBlank()) "(no label)" else "\"${e.label}\""
        val suffix = buildString {
            when {
                e.isPassword -> append(" (password)")
                e.editable && e.value.isNullOrEmpty() -> append(" (empty)")
                e.editable -> append(" (value: \"${e.value}\")")
            }
            if (e.checked != null) append(if (e.checked) " (on)" else " (off)")
            if (e.scrollable) append(" (scrollable)")
        }
        return "[${e.id}] $role $label$suffix"
    }
}
