package com.ishaara.mobile.voice

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SarvamTranscriberTest {
    private val server = MockWebServer().apply { start() }
    private val transcriber = SarvamTranscriber(OkHttpClient(), server.url("/").toString().trimEnd('/'))

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun sendsWholeRecordingAndReadsTheTranscript() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"transcript":" मेरा alarm बंद कर दो। ","language_code":"hi-IN"}"""))
        val result = transcriber.transcribe("key", ByteArray(100))
        assertEquals(Transcript("मेरा alarm बंद कर दो।", "hi-IN"), result)

        val request = server.takeRequest()
        assertEquals("/speech-to-text", request.path)
        assertEquals("key", request.getHeader("api-subscription-key"))
        val body = request.body.readUtf8()
        listOf("saaras:v4", "codemix", "unknown", "WhatsApp").forEach { assertTrue(it, body.contains(it)) }
    }

    @Test
    fun answersAreTranscribedInEnglishLetters() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"transcript":"Kavya."}"""))
        transcriber.transcribe("key", ByteArray(100), latin = true)
        assertTrue(server.takeRequest().body.readUtf8().contains("translit"))
    }

    @Test
    fun serverErrorFails() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(IOException::class.java) { runBlocking { transcriber.transcribe("key", ByteArray(10)) } }
    }

    @Test
    fun wavHeaderDescribesTheAudio() {
        val wav = SarvamTranscriber.wav(ByteArray(10))
        assertEquals(54, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))
        assertEquals(10, wav[40].toInt())
    }
}
