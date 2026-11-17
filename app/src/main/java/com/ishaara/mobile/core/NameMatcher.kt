package com.ishaara.mobile.core

sealed interface NameMatch {
    data class One(val name: String) : NameMatch
    data class Many(val names: List<String>) : NameMatch
    data object None : NameMatch
}

/** Matches spoken or typed names against installed apps or contacts: exact, prefix, contains, then typos. */
object NameMatcher {
    fun match(query: String, names: List<String>): NameMatch {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return NameMatch.None
        val tiers = listOf<(String) -> Boolean>(
            { it == q },
            { it.startsWith(q) },
            { it.split(' ').any { word -> word.startsWith(q) } || it.contains(q) },
            { editDistance(it, q) <= 2 },
        )
        for (tier in tiers) {
            val hits = names.filter { tier(it.lowercase()) }.distinct()
            when (hits.size) {
                0 -> continue
                1 -> return NameMatch.One(hits[0])
                else -> return NameMatch.Many(hits)
            }
        }
        return NameMatch.None
    }

    private fun editDistance(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val above = row[j]
                row[j] = minOf(
                    row[j] + 1,
                    row[j - 1] + 1,
                    diagonal + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                diagonal = above
            }
        }
        return row[b.length]
    }
}
