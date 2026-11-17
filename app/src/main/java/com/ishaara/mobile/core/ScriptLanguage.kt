package com.ishaara.mobile.core

/**
 * Decides the reply language from the script of the text itself. Speech services guess the language
 * from sound and are often wrong on short commands, while the script of the final text is reliable.
 */
object ScriptLanguage {
    private const val MIN_SHARE = 0.3
    private val DEVANAGARI_LANGUAGES = setOf("mr-IN", "ne-IN", "kok-IN", "sa-IN", "mai-IN", "doi-IN", "brx-IN")

    /** Returns a language code, or null for English. [spoken] only separates languages that share a script. */
    fun detect(text: String, spoken: String? = null): String? {
        val counts = HashMap<Character.UnicodeScript, Int>()
        var letters = 0
        text.codePoints().forEach { point ->
            if (Character.isLetter(point)) {
                letters++
                val script = Character.UnicodeScript.of(point)
                counts[script] = (counts[script] ?: 0) + 1
            }
        }
        val top = counts.filterKeys { it != Character.UnicodeScript.LATIN }.maxByOrNull { it.value } ?: return null
        if (top.value < letters * MIN_SHARE) return null
        return when (top.key) {
            Character.UnicodeScript.DEVANAGARI -> spoken?.takeIf { it in DEVANAGARI_LANGUAGES } ?: "hi-IN"
            Character.UnicodeScript.BENGALI -> if (spoken == "as-IN") spoken else "bn-IN"
            Character.UnicodeScript.GUJARATI -> "gu-IN"
            Character.UnicodeScript.GURMUKHI -> "pa-IN"
            Character.UnicodeScript.TAMIL -> "ta-IN"
            Character.UnicodeScript.TELUGU -> "te-IN"
            Character.UnicodeScript.KANNADA -> "kn-IN"
            Character.UnicodeScript.MALAYALAM -> "ml-IN"
            Character.UnicodeScript.ORIYA -> "od-IN"
            // Hindi speech is often transcribed in Urdu script, so reply in Hindi.
            Character.UnicodeScript.ARABIC -> "hi-IN"
            else -> null
        }
    }

    fun nameOf(code: String): String = when (code) {
        "hi-IN" -> "Hindi"
        "mr-IN" -> "Marathi"
        "bn-IN" -> "Bengali"
        "as-IN" -> "Assamese"
        "gu-IN" -> "Gujarati"
        "pa-IN" -> "Punjabi"
        "ta-IN" -> "Tamil"
        "te-IN" -> "Telugu"
        "kn-IN" -> "Kannada"
        "ml-IN" -> "Malayalam"
        "od-IN" -> "Odia"
        else -> code
    }
}
