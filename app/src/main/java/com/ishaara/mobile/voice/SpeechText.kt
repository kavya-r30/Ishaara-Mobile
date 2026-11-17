package com.ishaara.mobile.voice

/**
 * Rewrites a reply so the voice reads it smoothly. Symbols and short units such as "₹30" or "500 ml"
 * made the voice stumble and pause mid-sentence.
 */
object SpeechText {
    private val rupees = Regex("""(?:₹|\bRs\.?|\bINR)\s?(\d[\d,]*(?:\.\d+)?)""")
    private val units = listOf(
        Regex("""(\d)\s?ml\b""", RegexOption.IGNORE_CASE) to "$1 millilitres",
        Regex("""(\d)\s?kg\b""", RegexOption.IGNORE_CASE) to "$1 kilograms",
        Regex("""(\d)\s?gm?\b""", RegexOption.IGNORE_CASE) to "$1 grams",
        Regex("""(\d)\s?(?:l|ltr|litre|liter)\b""", RegexOption.IGNORE_CASE) to "$1 litres",
        Regex("""(\d)\s?km\b""", RegexOption.IGNORE_CASE) to "$1 kilometres",
        Regex("""(\d)\s?mins?\b""", RegexOption.IGNORE_CASE) to "$1 minutes",
    )

    fun forSpeech(text: String, languageCode: String): String {
        val english = languageCode == "en-IN"
        var out = text
            .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
            .replace(Regex("""\s*[—–]\s*"""), ", ")
            .replace(Regex("""[*#_~`•|<>\[\]{}]"""), " ")
            .replace(" & ", if (english) " and " else " और ")
        out = rupees.replace(out) { m -> "${m.groupValues[1].replace(",", "")} ${if (english) "rupees" else "रुपये"}" }
        units.forEach { (pattern, words) -> out = pattern.replace(out, words) }
        // Emojis and other pictographs are read out or turned into pauses.
        out = out.filter { Character.getType(it) != Character.OTHER_SYMBOL.toInt() && !Character.isSurrogate(it) }
        return out.replace(Regex("""\s+"""), " ").replace(Regex("""\s+([,.!?।])"""), "$1").trim()
    }
}
