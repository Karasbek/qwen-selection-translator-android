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
)

data class TranslationResult(
    val outputText: String,
    val providerId: String,
    val detectedSourceLanguage: String? = null,
    val operation: Operation,
)

sealed class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoProvider(message: String) : TranslationException(message)
    class ProviderFailure(message: String, cause: Throwable? = null) : TranslationException(message, cause)
    class Configuration(message: String) : TranslationException(message)
}
