package com.ishaara.mobile.skills

import android.app.Activity
import android.content.ClipboardManager
import kotlinx.coroutines.CompletableDeferred

/** Android only lets the focused app read the clipboard, so this invisible activity takes focus for a moment. */
class ClipboardActivity : Activity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val clip = getSystemService(ClipboardManager::class.java).primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        pending?.complete(text)
        pending = null
        finish()
    }

    companion object {
        @Volatile
        var pending: CompletableDeferred<String>? = null
    }
}
