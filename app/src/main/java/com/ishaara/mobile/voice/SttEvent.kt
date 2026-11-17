package com.ishaara.mobile.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SttEvent {
    data object SpeechStarted : SttEvent
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String, val language: String?) : SttEvent
    data class Error(val message: String) : SttEvent
    data object Ignored : SttEvent

    companion object {
        fun parse(raw: String): SttEvent {
            val json = try {
                Json.parseToJsonElement(raw).jsonObject
            } catch (e: IllegalArgumentException) {
                return Ignored
            }
            fun field(name: String) = json[name]?.jsonPrimitive?.contentOrNull
            return when (field("event")) {
                "vad.speech_start" -> SpeechStarted
                "transcript.partial" -> Partial(field("text").orEmpty())
                "transcript.final" -> Final(field("text").orEmpty(), field("language"))
                "error" -> Error(field("message") ?: "Voice recognition failed.")
                else -> Ignored
            }
        }
    }
}
