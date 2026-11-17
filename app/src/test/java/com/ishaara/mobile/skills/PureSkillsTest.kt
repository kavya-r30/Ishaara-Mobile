package com.ishaara.mobile.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureSkillsTest {
    @Test
    fun recognisesPhoneNumbers() {
        assertTrue(PhoneNumbers.looksLikeNumber("+91 98765 43210"))
        assertTrue(PhoneNumbers.looksLikeNumber("9876543210"))
        assertFalse(PhoneNumbers.looksLikeNumber("Rahul"))
        assertFalse(PhoneNumbers.looksLikeNumber("123"))
    }

    @Test
    fun normalisesForWhatsApp() {
        assertEquals("919876543210", PhoneNumbers.international("+91 98765-43210", "in"))
        assertEquals("919876543210", PhoneNumbers.international("9876543210", "in"))
        assertEquals("919876543210", PhoneNumbers.international("09876543210", "in"))
        assertEquals("14155550100", PhoneNumbers.international("415 555 0100", "us"))
        assertEquals("919876543210", PhoneNumbers.international("9876543210", ""))
    }

    @Test
    fun urlAllowlist() {
        assertTrue(UrlPolicy.isAllowed("https://example.com"))
        assertTrue(UrlPolicy.isAllowed("geo:0,0?q=coffee"))
        assertTrue(UrlPolicy.isAllowed("market://details?id=a.b"))
        assertTrue(UrlPolicy.isAllowed("mailto:a@b.c"))
        assertFalse(UrlPolicy.isAllowed("intent://x#Intent;end"))
        assertFalse(UrlPolicy.isAllowed("file:///sdcard/x"))
        assertFalse(UrlPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(UrlPolicy.isAllowed("no scheme"))
    }
}
