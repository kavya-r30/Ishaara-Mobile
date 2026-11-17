package com.ishaara.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptLanguageTest {
    @Test
    fun englishIsNull() {
        assertNull(ScriptLanguage.detect("open whatsapp and send hi to rahul"))
    }

    @Test
    fun englishStaysEnglishWhateverTheSpeechServiceGuessed() {
        assertNull(ScriptLanguage.detect("what is on my screen", "ur-IN"))
    }

    @Test
    fun hindi() {
        assertEquals("hi-IN", ScriptLanguage.detect("राहुल को हाय भेजो"))
    }

    @Test
    fun hindiWithEnglishWords() {
        assertEquals("hi-IN", ScriptLanguage.detect("YouTube पर MrBeast का पहला video चलाओ"))
    }

    @Test
    fun oneForeignWordInEnglishIsEnglish() {
        assertNull(ScriptLanguage.detect("send नमस्ते to my friend rahul on whatsapp please"))
    }

    @Test
    fun marathiComesFromTheSpeechServiceBecauseTheScriptIsShared() {
        assertEquals("mr-IN", ScriptLanguage.detect("माझा अलार्म बंद कर", "mr-IN"))
    }

    @Test
    fun otherScripts() {
        assertEquals("ta-IN", ScriptLanguage.detect("வணக்கம்"))
        assertEquals("bn-IN", ScriptLanguage.detect("নমস্কার"))
    }

    @Test
    fun urduScriptRepliesInHindi() {
        assertEquals("hi-IN", ScriptLanguage.detect("میرا الارم بند کرو"))
    }

    @Test
    fun noLettersIsNull() {
        assertNull(ScriptLanguage.detect("123 !?"))
    }
}
