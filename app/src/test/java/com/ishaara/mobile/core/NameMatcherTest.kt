package com.ishaara.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NameMatcherTest {
    private val names = listOf("YouTube", "YouTube Music", "WhatsApp", "Camera", "Rahul Sharma", "Rahul Verma")

    @Test
    fun exactMatchWinsOverPrefix() {
        assertEquals(NameMatch.One("YouTube"), NameMatcher.match("youtube", names))
    }

    @Test
    fun prefixMatch() {
        assertEquals(NameMatch.One("WhatsApp"), NameMatcher.match("whats", names))
    }

    @Test
    fun severalPrefixMatchesAreAmbiguous() {
        assertEquals(NameMatch.Many(listOf("Rahul Sharma", "Rahul Verma")), NameMatcher.match("rahul", names))
    }

    @Test
    fun containsMatch() {
        assertEquals(NameMatch.One("Rahul Verma"), NameMatcher.match("verma", names))
    }

    @Test
    fun typoWithinTwoEdits() {
        assertEquals(NameMatch.One("Camera"), NameMatcher.match("camra", names))
    }

    @Test
    fun noMatch() {
        assertEquals(NameMatch.None, NameMatcher.match("zzzzzz", names))
    }
}
