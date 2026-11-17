package com.ishaara.mobile.skills

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ishaara.mobile.core.model.ActionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class ClipboardSkill(private val context: Context) {
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    suspend fun read(): ActionResult {
        val result = readText() ?: return ActionResult.fail("CLIPBOARD_UNAVAILABLE", "Android did not allow reading the clipboard.")
        return ActionResult.ok(if (result.isEmpty()) "The clipboard is empty." else "Clipboard: $result")
    }

    fun set(text: String): ActionResult {
        clipboard.setPrimaryClip(ClipData.newPlainText("Ishaara", text))
        return ActionResult.ok("Copied to the clipboard.")
    }

    fun select(node: AccessibilityNodeInfo, start: Int?, end: Int?): ActionResult {
        val length = node.text?.length ?: 0
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, (start ?: 0).coerceIn(0, length))
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, (end ?: length).coerceIn(0, length))
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)) {
            ActionResult.ok("Selected the text.")
        } else {
            ActionResult.fail("SELECT_FAILED", "That element doesn't support selection. Use copy_text to copy its whole text.")
        }
    }

    fun copy(node: AccessibilityNodeInfo): ActionResult {
        val hasSelection = node.textSelectionStart >= 0 && node.textSelectionStart != node.textSelectionEnd
        if (hasSelection && node.performAction(AccessibilityNodeInfo.ACTION_COPY)) return ActionResult.ok("Copied the selection.")
        val text = node.text ?: node.contentDescription
            ?: return ActionResult.fail("NO_TEXT", "That element has no text to copy.")
        clipboard.setPrimaryClip(ClipData.newPlainText("Ishaara", text))
        return ActionResult.ok("Copied: $text")
    }

    fun cut(node: AccessibilityNodeInfo): ActionResult =
        if (node.performAction(AccessibilityNodeInfo.ACTION_CUT)) {
            ActionResult.ok("Cut the selection.")
        } else {
            ActionResult.fail("CUT_FAILED", "Cut didn't work. Select text in an input first.")
        }

    suspend fun paste(node: AccessibilityNodeInfo): ActionResult {
        if (!node.isEditable) return ActionResult.fail("NOT_EDITABLE", "That element does not accept text.")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return ActionResult.ok("Pasted.")
        val text = readText().orEmpty()
        if (text.isEmpty()) return ActionResult.fail("PASTE_FAILED", "Nothing could be pasted.")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, node.text?.toString().orEmpty() + text)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            ActionResult.ok("Pasted.")
        } else {
            ActionResult.fail("PASTE_FAILED", "The app did not accept the text.")
        }
    }

    private suspend fun readText(): String? {
        val deferred = CompletableDeferred<String>()
        ClipboardActivity.pending = deferred
        context.startActivity(
            Intent(context, ClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return withTimeoutOrNull(2000) { deferred.await() }
    }
}
