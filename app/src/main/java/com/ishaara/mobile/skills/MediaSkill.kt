package com.ishaara.mobile.skills

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.MediaCommand

class MediaSkill(private val context: Context) {
    fun youtubeSearch(query: String): ActionResult = launch(
        Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra(SearchManager.QUERY, query),
        "YouTube results for \"$query\" opened.",
        "YouTube is not installed.",
    )

    fun playStoreSearch(query: String): ActionResult = launch(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}&c=apps"))
            .setPackage("com.android.vending"),
        "Play Store results for \"$query\" opened.",
        "Play Store is not available on this phone.",
    )

    fun control(command: MediaCommand): ActionResult {
        val key = when (command) {
            MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaCommand.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaCommand.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return ActionResult.ok("Sent ${command.name.lowercase().replace('_', ' ')} to the media player.", selfVerified = true)
    }

    private fun launch(intent: Intent, success: String, missing: String): ActionResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.ok(success)
    } catch (e: ActivityNotFoundException) {
        ActionResult.fail("APP_NOT_FOUND", missing)
    }
}
