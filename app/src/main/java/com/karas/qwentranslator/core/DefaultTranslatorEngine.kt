package com.karas.qwentranslator.core

class DefaultTranslatorEngine(
    private val providers: List<TranslatorProvider>,
) : TranslatorEngine {
    override suspend fun execute(request: TranslationRequest): TranslationResult {
        require(request.text.isNotBlank()) { "Text must not be blank" }

        val errors = mutableListOf<String>()
        val candidates = providers.filter { it.supports(request.operation) }
        if (candidates.isEmpty()) {
            throw TranslationException.NoProvider("Нет провайдера для ${request.operation}")
        }

        for (provider in candidates) {
            try {
                return provider.execute(request)
            } catch (e: TranslationException.Configuration) {
                errors += "${provider.id}: ${e.message}"
            } catch (e: Exception) {
                errors += "${provider.id}: ${e.message ?: e::class.java.simpleName}"
            }
        }

        throw TranslationException.NoProvider(
            buildString {
                append("Не удалось обработать текст.")
                if (errors.isNotEmpty()) {
                    append(" ")
                    append(errors.joinToString(" | "))
                }
            },
        )
    }
}
