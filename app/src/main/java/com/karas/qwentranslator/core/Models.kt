package com.karas.qwentranslator.core

enum class Operation {
    TRANSLATE,
    EXPLAIN,
}

data class TranslationRequest(
    val text: String,
    val operation: Operation,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "ru",
    val explanation: ExplanationOptions = ExplanationOptions(),
    val bypassCache: Boolean = false,
) {
    companion object {
        const val MAX_TEXT_LENGTH = 12_000
    }
}

data class TranslationResult(
    val outputText: String,
    val providerId: String,
    val detectedSourceLanguage: String? = null,
    val operation: Operation,
)

sealed class TranslationException(
    message: String, cause: Throwable? = null,
    val kind: FailureKind = FailureKind.UNKNOWN, val provider: String? = null,
) : Exception(message, cause) {
    class NoProvider(message: String, kind: FailureKind = FailureKind.NO_PROVIDER, provider: String? = null) :
        TranslationException(message, kind = kind, provider = provider)
    class ProviderFailure(message: String, cause: Throwable? = null, kind: FailureKind = FailureKind.UNKNOWN, provider: String? = null) :
        TranslationException(message, cause, kind, provider)
    class Configuration(message: String, kind: FailureKind = FailureKind.CONFIGURATION) : TranslationException(message, kind = kind)
}
