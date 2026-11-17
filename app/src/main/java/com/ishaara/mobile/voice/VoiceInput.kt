package com.ishaara.mobile.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

sealed interface VoiceEvent {
    data class Level(val value: Float) : VoiceEvent
    data object Understanding : VoiceEvent
    data class Final(val text: String, val language: String?) : VoiceEvent
    data class Failed(val message: String) : VoiceEvent
}

/**
 * Records until the speaker stops, then transcribes the whole recording. The live stream is used only
 * to detect the end of speech, because its early guesses at the words and language are unreliable.
 * Needs RECORD_AUDIO.
 */
class VoiceInput(
    private val http: OkHttpClient,
    private val apiKey: suspend () -> String,
) {
    private companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_BYTES = 3200 // 100 ms of 16-bit mono
        const val NO_SPEECH_MS = 7000L
        const val MAX_MS = 25000L
        const val MIN_AUDIO_BYTES = 8000 // A quarter of a second
    }

    private val transcriber = SarvamTranscriber(http)

    @Volatile
    private var endOfSpeech: CompletableDeferred<Unit>? = null

    @Volatile
    private var stoppedByUser = false

    /** Stops recording and transcribes what was said so far. */
    fun finishNow() {
        stoppedByUser = true
        endOfSpeech?.complete(Unit)
    }

    @SuppressLint("MissingPermission")
    fun listen(latin: Boolean = false): Flow<VoiceEvent> = callbackFlow {
        val key = apiKey()
        if (key.isBlank()) {
            trySend(VoiceEvent.Failed("Add your Sarvam key in Settings to use voice."))
            close()
            return@callbackFlow
        }

        val done = CompletableDeferred<Unit>()
        endOfSpeech = done
        stoppedByUser = false
        val heardSpeech = AtomicBoolean(false)
        var streamed: Transcript? = null
        var failure: String? = null

        val stt = SarvamStt(http, key) { event ->
            when (event) {
                SttEvent.SpeechStarted, is SttEvent.Partial -> heardSpeech.set(true)
                is SttEvent.Final -> if (event.text.isNotBlank()) {
                    heardSpeech.set(true)
                    streamed = Transcript(event.text, event.language)
                    done.complete(Unit)
                }
                is SttEvent.Error -> {
                    if (!done.isCompleted) failure = event.message
                    done.complete(Unit)
                }
                SttEvent.Ignored -> Unit
            }
        }

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            FRAME_BYTES * 2,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize,
        )
        recorder.startRecording()
        val audio = ByteArrayOutputStream()
        val released = AtomicBoolean(false)
        fun releaseRecorder() {
            if (released.compareAndSet(false, true)) {
                recorder.stop()
                recorder.release()
            }
        }

        val reader = launch {
            val buffer = ByteArray(FRAME_BYTES)
            val start = System.currentTimeMillis()
            while (isActive && !done.isCompleted) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    audio.write(buffer, 0, read)
                    stt.sendAudio(buffer, read)
                    trySend(VoiceEvent.Level(level(buffer, read)))
                }
                val elapsed = System.currentTimeMillis() - start
                if ((!heardSpeech.get() && elapsed > NO_SPEECH_MS) || elapsed > MAX_MS) done.complete(Unit)
            }
        }

        val work = launch {
            done.await()
            reader.cancelAndJoin()
            releaseRecorder()
            stt.close()

            val problem = failure
            when {
                problem != null -> trySend(VoiceEvent.Failed(problem))
                !heardSpeech.get() && !stoppedByUser -> trySend(VoiceEvent.Failed("Didn't catch that. Try again or type it."))
                audio.size() < MIN_AUDIO_BYTES -> trySend(VoiceEvent.Failed("Didn't catch that. Try again or type it."))
                else -> {
                    trySend(VoiceEvent.Understanding)
                    val result = try {
                        transcriber.transcribe(key, audio.toByteArray(), latin)
                    } catch (e: IOException) {
                        streamed
                    }
                    if (result == null || result.text.isBlank()) {
                        trySend(VoiceEvent.Failed("Didn't catch that. Try again or type it."))
                    } else {
                        trySend(VoiceEvent.Final(result.text, result.language))
                    }
                }
            }
            close()
        }

        awaitClose {
            work.cancel()
            reader.cancel()
            releaseRecorder()
            stt.close()
            endOfSpeech = null
        }
    }.flowOn(Dispatchers.IO)

    private fun level(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += (sample * sample).toDouble()
            i += 2
        }
        val rms = sqrt(sum / (length / 2).coerceAtLeast(1))
        return (rms / 6000.0).toFloat().coerceIn(0f, 1f)
    }
}
