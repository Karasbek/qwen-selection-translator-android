package com.karas.qwentranslator.core

/** Ordered UI results map to independent engine operations; another block can be added here. */
enum class ResultBlock(val operation: Operation) {
    EXPLANATION(Operation.EXPLAIN), TRANSLATION(Operation.TRANSLATE);
    companion object { val defaultOrder = listOf(EXPLANATION, TRANSLATION) }
}
enum class ExplanationLanguage { ENGLISH, SAME_AS_SOURCE, RUSSIAN }
enum class ExplanationDetail { SIMPLE, NORMAL, DETAILED }
data class ExplanationOptions(
    val language: ExplanationLanguage = ExplanationLanguage.ENGLISH,
    val detail: ExplanationDetail = ExplanationDetail.SIMPLE,
    val examples: Boolean = true,
)
enum class FailureKind {
    AUTHENTICATION, RATE_LIMIT, TIMEOUT, NETWORK, ACCESS_DENIED, UNAVAILABLE,
    MALFORMED_RESPONSE, CONFIGURATION, INPUT_TOO_LONG, NO_PROVIDER, UNKNOWN,
}
data class TranslationFailure(val kind: FailureKind, val provider: String? = null) {
    companion object {
        fun from(error: Exception): TranslationFailure = if (error is TranslationException)
            TranslationFailure(error.kind, error.provider) else TranslationFailure(FailureKind.UNKNOWN)
    }
}
