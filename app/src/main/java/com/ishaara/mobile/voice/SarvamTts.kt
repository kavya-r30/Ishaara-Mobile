package com.ishaara.mobile.voice

import com.ishaara.mobile.llm.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

data class SarvamConfig(
    val apiKey: String,
    val ttsModel: String,
    val ttsSpeaker: String,
    val ttsSpeakerEnglish: String,
)

class SarvamTts(
    private val http: OkHttpClient,
    private val config: suspend () -> SarvamConfig,
    private val baseUrl: String = "https://api.sarvam.ai",
) {
    /** Returns WAV bytes. Throws [IOException] on any failure. */
    suspend fun synthesize(text: String, languageCode: String?): ByteArray {
        val cfg = config()
        if (cfg.apiKey.isBlank()) throw IOException("Sarvam key is not set")
        val capped = text.take(MAX_CHARS)
        val language = languageCode?.takeIf { it in SUPPORTED } ?: FALLBACK
        val first = request(cfg, capped, language)
        if (first != null) return first
        if (language != FALLBACK) request(cfg, capped, FALLBACK)?.let { return it }
        throw IOException("Sarvam rejected the voice request")
    }

    /** Returns audio, or null if Sarvam rejected this voice and language combination. */
    private suspend fun request(cfg: SarvamConfig, text: String, language: String): ByteArray? {
        val body = buildJsonObject {
            put("text", text)
            put("language_code", language)
            put("speaker", if (language == ENGLISH) cfg.ttsSpeakerEnglish else cfg.ttsSpeaker)
            put("model", cfg.ttsModel)
            put("pace", PACE)
            put("temperature", TEMPERATURE)
        }
        val request = Request.Builder()
            .url("$baseUrl/text-to-speech")
            .header("api-subscription-key", cfg.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).await().use { response ->
            return when {
                response.isSuccessful -> {
                    val json = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    Base64.getDecoder().decode(json.getValue("audios").jsonArray[0].jsonPrimitive.content)
                }
                response.code == 400 || response.code == 422 -> null
                else -> throw IOException("Sarvam returned ${response.code}")
            }
        }
    }

    private companion object {
        const val MAX_CHARS = 500
        const val FALLBACK = "hi-IN"
        const val ENGLISH = "en-IN"
        const val PACE = 1.0
        // Measured on Aparna: at 0.6 the voice paused mid-sentence; 0.3 kept the sentence flowing.
        const val TEMPERATURE = 0.3
        val SUPPORTED = setOf(
            "bn-IN", "en-IN", "gu-IN", "hi-IN", "kn-IN", "ml-IN", "mr-IN", "od-IN", "pa-IN", "ta-IN", "te-IN",
        )
    }
}
