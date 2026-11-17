package com.ishaara.mobile.skills

/** open_url can be influenced by screen text, so only harmless link types are allowed. */
object UrlPolicy {
    private val ALLOWED = setOf("https", "http", "geo", "market", "mailto")

    fun isAllowed(url: String): Boolean =
        url.contains(':') && url.substringBefore(':').lowercase() in ALLOWED
}
