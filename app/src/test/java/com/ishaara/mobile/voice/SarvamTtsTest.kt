package com.ishaara.mobile.voice

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.Base64

class SarvamTtsTest {
    private val server = MockWebServer().apply { start() }
    private val tts = SarvamTts(
        OkHttpClient(),
        { SarvamConfig("key", "bulbul:v4-flash", "simran_hi_narration", "simran_en_narration") },
        server.url("/").toString().trimEnd('/'),
    )
    private val audio = byteArrayOf(1, 2, 3)
    private val ok = """{"audios":["${Base64.getEncoder().encodeToString(audio)}"]}"""

    @After
    fun tearDown() = server.shutdown()

    private fun field(body: String, name: String) =
        Json.parseToJsonElement(body).jsonObject.getValue(name).jsonPrimitive.content

    @Test
    fun sendsConfiguredVoiceAndDecodesAudio() = runBlocking {
        server.enqueue(MockResponse().setBody(ok))
        assertArrayEquals(audio, tts.synthesize("hello", "en-IN"))
        val request = server.takeRequest()
        assertEquals("/text-to-speech", request.path)
        assertEquals("key", request.getHeader("api-subscription-key"))
        val body = request.body.readUtf8()
        assertEquals("bulbul:v4-flash", field(body, "model"))
        assertEquals("simran_en_narration", field(body, "speaker"))
        assertEquals("en-IN", field(body, "language_code"))
        assertEquals("hello", field(body, "text"))
        assertEquals("1.0", field(body, "pace"))
        assertEquals("0.3", field(body, "temperature"))
    }

    @Test
    fun nonEnglishUsesTheMainVoice() = runBlocking {
        server.enqueue(MockResponse().setBody(ok))
        tts.synthesize("namaste", "hi-IN")
        assertEquals("simran_hi_narration", field(server.takeRequest().body.readUtf8(), "speaker"))
    }

    @Test
    fun unsupportedOrMissingLanguageUsesHindi() = runBlocking {
        server.enqueue(MockResponse().setBody(ok))
        server.enqueue(MockResponse().setBody(ok))
        tts.synthesize("a", "as-IN")
        assertEquals("hi-IN", field(server.takeRequest().body.readUtf8(), "language_code"))
        tts.synthesize("a", null)
        assertEquals("hi-IN", field(server.takeRequest().body.readUtf8(), "language_code"))
    }

    @Test
    fun rejectedLanguageRetriesOnceWithHindi() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422))
        server.enqueue(MockResponse().setBody(ok))
        assertArrayEquals(audio, tts.synthesize("a", "en-IN"))
        assertEquals("en-IN", field(server.takeRequest().body.readUtf8(), "language_code"))
        val retry = server.takeRequest().body.readUtf8()
        assertEquals("hi-IN", field(retry, "language_code"))
        assertEquals("simran_hi_narration", field(retry, "speaker"))
    }

    @Test
    fun longTextIsCapped() = runBlocking {
        server.enqueue(MockResponse().setBody(ok))
        tts.synthesize("x".repeat(3000), "hi-IN")
        assertEquals(500, field(server.takeRequest().body.readUtf8(), "text").length)
    }

    @Test
    fun missingKeyFails() {
        val noKey = SarvamTts(OkHttpClient(), { SarvamConfig("", "m", "s", "e") }, server.url("/").toString().trimEnd('/'))
        assertThrows(IOException::class.java) { runBlocking { noKey.synthesize("a", "hi-IN") } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun serverErrorFails() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(IOException::class.java) { runBlocking { tts.synthesize("a", "hi-IN") } }
    }
}
