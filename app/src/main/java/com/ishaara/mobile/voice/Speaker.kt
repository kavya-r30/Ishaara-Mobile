package com.ishaara.mobile.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.ishaara.mobile.core.ScriptLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.IOException

/** Plays Sarvam speech. Failures are logged only, because the text is already on screen. */
class Speaker(private val context: Context, private val tts: SarvamTts) {
    private var player: MediaPlayer? = null
    private var playing: CompletableDeferred<Unit>? = null

    /** Speaks [text] in the language it is written in, and returns when it has finished. */
    suspend fun speak(text: String) {
        val finished = CompletableDeferred<Unit>()
        try {
            val language = ScriptLanguage.detect(text) ?: "en-IN"
            val audio = tts.synthesize(SpeechText.forSpeech(text, language), language)
            val file = File(context.cacheDir, "reply.wav").apply { writeBytes(audio) }
            stop()
            playing = finished
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(file.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            finished.await()
        } catch (e: CancellationException) {
            stop()
            throw e
        } catch (e: IOException) {
            Log.w("Ishaara", "Could not speak: ${e.message}")
        } catch (e: RuntimeException) {
            Log.w("Ishaara", "Could not play: ${e.message}")
        }
    }

    fun stop() {
        player?.release()
        player = null
        playing?.complete(Unit)
        playing = null
    }
}
