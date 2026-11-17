package com.ishaara.mobile.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SttEventTest {
    @Test
    fun parsesPartial() {
        assertEquals(
            SttEvent.Partial("hello"),
            SttEvent.parse("""{"event":"transcript.partial","utterance_idx":0,"text":"hello"}"""),
        )
    }

    @Test
    fun parsesFinalWithLanguage() {
        assertEquals(
            SttEvent.Final("namaste", "hi-IN"),
            SttEvent.parse("""{"event":"transcript.final","utterance_idx":0,"text":"namaste","language":"hi-IN"}"""),
        )
    }

    @Test
    fun parsesFinalWithoutLanguage() {
        assertEquals(SttEvent.Final("hi", null), SttEvent.parse("""{"event":"transcript.final","text":"hi"}"""))
    }

    @Test
    fun parsesVadStart() {
        assertEquals(SttEvent.SpeechStarted, SttEvent.parse("""{"event":"vad.speech_start","utterance_idx":0}"""))
    }

    @Test
    fun parsesError() {
        assertEquals(
            SttEvent.Error("bad key"),
            SttEvent.parse("""{"event":"error","code":"x","is_fatal":true,"message":"bad key"}"""),
        )
    }

    @Test
    fun ignoresOtherEventsAndBadJson() {
        assertEquals(SttEvent.Ignored, SttEvent.parse("""{"event":"session.begin"}"""))
        assertEquals(SttEvent.Ignored, SttEvent.parse("not json"))
    }

    @Test
    fun buildsAudioFrame() {
        val frame = SarvamStt.audioFrame(byteArrayOf(1, 2, 3), 2)
        val expected = Base64.getEncoder().encodeToString(byteArrayOf(1, 2))
        assertEquals("""{"event":"audio_input","audio":"$expected"}""", frame)
    }

    @Test
    fun buildsRealtimeUrl() {
        val url = SarvamStt.realtimeUrl()
        assertTrue(url, url.startsWith("wss://api.sarvam.ai/speech-to-text-realtime/ws?"))
        listOf("language_code=auto", "model=saaras:v4", "mode=transcribe", "stream_type=balanced", "sample_rate=16000", "encoding=linear16", "endpointing=vad")
            .forEach { assertTrue(it, url.contains(it)) }
    }
}
