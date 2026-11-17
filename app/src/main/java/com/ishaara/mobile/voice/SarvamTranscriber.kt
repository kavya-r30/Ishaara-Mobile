package com.ishaara.mobile.voice

import com.ishaara.mobile.llm.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Transcript(val text: String, val language: String?)

/**
 * Transcribes a whole recording. Hearing all of it gives far better language detection and accuracy
 * than streaming, which has to guess from the first moments of speech.
 */
class SarvamTranscriber(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.sarvam.ai",
) {
    /**
     * [pcm] is 16 kHz mono 16-bit audio. With [latin], the result is in English letters whatever language
     * was spoken, which is what answers such as names need. Throws [IOException] on failure.
     */
    suspend fun transcribe(apiKey: String, pcm: ByteArray, latin: Boolean = false): Transcript {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav(pcm).toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "saaras:v4")
            // English words stay in English and Indic words stay in their own script.
            .addFormDataPart("mode", if (latin) "translit" else "codemix")
            .addFormDataPart("language_code", "unknown")
            .addFormDataPart("keyterms", KEYTERMS)
            .build()
        val request = Request.Builder()
            .url("$baseUrl/speech-to-text")
            .header("api-subscription-key", apiKey)
            .post(body)
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("Sarvam returned ${response.code}")
            try {
                val json = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                return Transcript(
                    json["transcript"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                    json["language_code"]?.jsonPrimitive?.contentOrNull,
                )
            } catch (e: IllegalArgumentException) {
                throw IOException("Sarvam's reply couldn't be read")
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val HEADER_BYTES = 44
        private const val KEYTERMS =
            """["WhatsApp","YouTube","Instagram","Chrome","Gmail","Play Store","Ishaara","Facebook","Spotify","Telegram","Google Maps"]"""

        fun wav(pcm: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + pcm.size)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }
}
