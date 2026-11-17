package com.ishaara.mobile.voice

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64

/** One realtime session, used to detect when the speaker has finished. */
class SarvamStt(
    http: OkHttpClient,
    apiKey: String,
    private val onEvent: (SttEvent) -> Unit,
) {
    private val socket: WebSocket = http.newWebSocket(
        Request.Builder().url(realtimeUrl()).header("API-SUBSCRIPTION-KEY", apiKey).build(),
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = onEvent(SttEvent.parse(text))

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(SttEvent.Error("Voice recognition couldn't connect."))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (code != NORMAL_CLOSE) onEvent(SttEvent.Error("Voice recognition stopped ($code)."))
            }
        },
    )

    fun sendAudio(buffer: ByteArray, length: Int) {
        socket.send(audioFrame(buffer, length))
    }

    fun close() {
        socket.close(NORMAL_CLOSE, null)
    }

    companion object {
        private const val NORMAL_CLOSE = 1000

        fun realtimeUrl(): String =
            "wss://api.sarvam.ai/speech-to-text-realtime/ws" +
                "?language_code=auto&model=saaras:v4&mode=transcribe&stream_type=balanced" +
                "&endpointing=vad&encoding=linear16&sample_rate=16000&silence_duration_ms=800"

        fun audioFrame(buffer: ByteArray, length: Int): String {
            val audio = Base64.getEncoder().encodeToString(buffer.copyOf(length))
            return """{"event":"audio_input","audio":"$audio"}"""
        }
    }
}
