package com.ishaara.mobile.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun pricesAndUnitsAreReadAsWords() {
        assertEquals(
            "Zepto has Amul Taaza, 500 millilitres, for 30 rupees.",
            SpeechText.forSpeech("Zepto has Amul Taaza, 500 ml, for ₹30.", "en-IN"),
        )
    }

    @Test
    fun hindiRepliesSayRupeesInHindi() {
        assertEquals("Zepto पर 33 रुपये का है।", SpeechText.forSpeech("Zepto पर ₹33 का है।", "hi-IN"))
    }

    @Test
    fun symbolsAndEmojisAreDropped() {
        assertEquals("Done, it's set! The alarm is on.", SpeechText.forSpeech("Done — it’s set! 🎉 The **alarm** is on.", "en-IN"))
    }

    @Test
    fun bigAmountsLoseTheirCommas() {
        assertEquals("It costs 1250 rupees.", SpeechText.forSpeech("It costs Rs. 1,250.", "en-IN"))
    }
}
