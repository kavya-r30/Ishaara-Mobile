package com.ishaara.mobile.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ishaara.mobile.core.model.Bounds
import com.ishaara.mobile.core.model.ElementRole
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.UiElement

data class LiveScreen(val snapshot: ScreenSnapshot, val nodes: Map<Int, AccessibilityNodeInfo>) {
    companion object {
        val EMPTY = LiveScreen(ScreenSnapshot("unknown", emptyList()), emptyMap())
    }
}

class NodeTreeReader(private val ownPackage: String) {
    private companion object {
        const val MAX_ELEMENTS = 80
        const val MAX_TEXT = 120
        const val MAX_DUMP_LINES = 200
        const val SYSTEM_UI = "com.android.systemui"
    }

    private class Draft(val node: AccessibilityNodeInfo, val element: UiElement) {
        val interactive get() = element.clickable || element.editable || element.scrollable
    }

    fun read(
        windows: List<AccessibilityWindowInfo>,
        activeRoot: AccessibilityNodeInfo?,
        screen: Rect,
        includeSystemUi: Boolean,
    ): LiveScreen {
        // During transitions the window list can come back without roots while the active root is available.
        val roots = rootsOf(windows, includeSystemUi).ifEmpty {
            listOfNotNull(activeRoot?.takeIf { it.packageName?.toString() != ownPackage })
        }
        val drafts = mutableListOf<Draft>()
        roots.forEach { visit(it, false, screen, drafts) }

        val kept = if (drafts.size <= MAX_ELEMENTS) {
            drafts
        } else {
            val interactive = drafts.filter { it.interactive }.take(MAX_ELEMENTS)
            val rest = drafts.filterNot { it.interactive }.take(MAX_ELEMENTS - interactive.size)
            val allowed = (interactive + rest).toSet()
            drafts.filter { it in allowed }
        }
        val elements = kept.mapIndexed { i, d -> d.element.copy(id = i + 1) }
        val nodes = kept.mapIndexed { i, d -> (i + 1) to d.node }.toMap()
        // Ishaara's own windows never count: the chat bar can still be closing when a command starts.
        val activePackage = windows.firstOrNull { it.isActive }?.root?.packageName?.toString()?.takeIf { it != ownPackage }
            ?: roots.firstOrNull()?.packageName?.toString()
            ?: "unknown"
        return LiveScreen(ScreenSnapshot(activePackage, elements), nodes)
    }

    /** Full dump for hard screens. Password values are never included. */
    fun dump(windows: List<AccessibilityWindowInfo>, includeSystemUi: Boolean): String {
        val lines = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (lines.size >= MAX_DUMP_LINES) return
            val rect = Rect().also(node::getBoundsInScreen)
            val text = if (node.isPassword) "(hidden)" else node.text?.toString().orEmpty().take(MAX_TEXT)
            lines += buildString {
                append("  ".repeat(depth)).append(node.className?.toString()?.substringAfterLast('.'))
                if (text.isNotEmpty()) append(" text=\"$text\"")
                node.contentDescription?.let { append(" desc=\"${it.toString().take(MAX_TEXT)}\"") }
                node.viewIdResourceName?.let { append(" id=$it") }
                append(" [${rect.left},${rect.top}][${rect.right},${rect.bottom}]")
                if (node.isClickable) append(" clickable")
                if (node.isEditable) append(" editable")
                if (node.isScrollable) append(" scrollable")
                if (!node.isVisibleToUser) append(" hidden")
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        rootsOf(windows, includeSystemUi).forEach { walk(it, 0) }
        return lines.joinToString("\n")
    }

    private fun rootsOf(windows: List<AccessibilityWindowInfo>, includeSystemUi: Boolean): List<AccessibilityNodeInfo> =
        windows
            .filter { it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                val root = window.root ?: return@mapNotNull null
                val pkg = root.packageName?.toString()
                when {
                    pkg == ownPackage -> null
                    pkg == SYSTEM_UI && !window.isActive && !includeSystemUi -> null
                    else -> root
                }
            }

    private fun visit(node: AccessibilityNodeInfo, insideClickable: Boolean, screen: Rect, out: MutableList<Draft>) {
        if (!node.isVisibleToUser) return
        val rect = Rect().also(node::getBoundsInScreen)
        val onScreen = rect.intersect(screen) && !rect.isEmpty

        val clickable = node.isClickable || node.isLongClickable
        val own = ownLabel(node)
        val interactive = clickable || node.isEditable || node.isScrollable
        val textOnly = !interactive && own.isNotEmpty()

        if (onScreen && (interactive || (textOnly && !insideClickable))) {
            var label = if (own.isEmpty() && clickable) childText(node) else own
            // A bare switch takes its meaning from its row, such as an alarm's time.
            if (label.isEmpty() && node.isCheckable) label = node.parent?.let(::childText).orEmpty()
            out += Draft(
                node,
                UiElement(
                    id = 0,
                    role = roleOf(node, clickable),
                    label = label.take(MAX_TEXT),
                    bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                    clickable = clickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    isPassword = node.isPassword,
                    checked = if (node.isCheckable) node.isChecked else null,
                    value = inputValue(node),
                    resourceId = node.viewIdResourceName,
                ),
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { visit(it, insideClickable || clickable, screen, out) }
        }
    }

    private fun ownLabel(node: AccessibilityNodeInfo): String {
        val desc = node.contentDescription?.toString().orEmpty()
        if (desc.isNotBlank()) return desc.trim()
        if (node.isEditable) {
            val hint = node.hintText?.toString().orEmpty().trim()
            if (hint.isNotEmpty()) return hint
            return node.labeledBy?.text?.toString().orEmpty().trim()
        }
        if (node.isPassword) return ""
        return node.text?.toString().orEmpty().trim()
    }

    private fun inputValue(node: AccessibilityNodeInfo): String? {
        if (!node.isEditable || node.isPassword) return null
        val text = node.text?.toString().orEmpty()
        return text.takeIf { it.isNotEmpty() && it != node.hintText?.toString() }?.take(MAX_TEXT)
    }

    private fun childText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo) {
            if (parts.size >= 4) return
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                if (!child.isVisibleToUser) continue
                val text = if (child.isPassword) "" else ownLabel(child)
                if (text.isNotEmpty()) parts += text
                if (!child.isClickable) walk(child)
            }
        }
        walk(node)
        return parts.joinToString(", ")
    }

    private fun roleOf(node: AccessibilityNodeInfo, clickable: Boolean): ElementRole {
        val cls = node.className?.toString().orEmpty()
        return when {
            node.isEditable -> ElementRole.INPUT
            node.isCheckable -> ElementRole.SWITCH
            clickable -> ElementRole.BUTTON
            node.isScrollable -> ElementRole.LIST
            cls.endsWith("ImageView") -> ElementRole.IMAGE
            else -> ElementRole.TEXT
        }
    }
}
