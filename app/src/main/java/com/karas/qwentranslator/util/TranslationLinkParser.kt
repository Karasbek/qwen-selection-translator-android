package com.karas.qwentranslator.util

/** Recognizes a public translation URL, not ReadEra-specific state or a foreign package identity.
 * Values are already decoded by Android Uri. Language and provider still come from Qwen settings.
 */
object TranslationLinkParser {
    private val language = Regex("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,4})?")

    fun parse(scheme: String?, host: String?, path: String?, queryText: String?, fragment: String?): Invocation? {
        if (scheme?.lowercase() !in setOf("http", "https") ||
            !host.equals("translate.google.com", ignoreCase = true) || path != "/") return null
        val text = if (queryText != null) queryText else {
            val parts = fragment?.split('/', limit = 3) ?: return null
            if (parts.size != 3 || (parts[0] != "auto" && !language.matches(parts[0])) ||
                !language.matches(parts[1])) return null
            parts[2]
        }
        if (text.isBlank()) return null
        return Invocation(text, readOnly = true, source = InvocationSource.TRANSLATION_LINK)
    }
}
