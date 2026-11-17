package com.ishaara.mobile.skills

object PhoneNumbers {
    private val COUNTRY_CODES = mapOf(
        "in" to "91", "us" to "1", "ca" to "1", "gb" to "44", "ae" to "971", "sg" to "65", "au" to "61", "de" to "49",
    )
    private const val DEFAULT_CODE = "91"

    fun looksLikeNumber(text: String): Boolean =
        text.matches(Regex("""\+?[\d\s\-()]{6,}"""))

    /** Digits with country code and no plus sign, as wa.me expects. */
    fun international(raw: String, countryIso: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return digits
        val code = COUNTRY_CODES[countryIso.lowercase()] ?: DEFAULT_CODE
        return code + digits.trimStart('0')
    }
}
