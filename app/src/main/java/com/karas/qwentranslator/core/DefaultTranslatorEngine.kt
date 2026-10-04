package com.karas.qwentranslator.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DefaultTranslatorEngine(
    private val providers: List<TranslatorProvider>,
) : TranslatorEngine {
    override suspend fun execute(request: TranslationRequest): TranslationResult {
        require(request.text.isNotBlank()) { "Text must not be blank" }
        if (request.text.length > MAX_INPUT_CHARACTERS) {
            throw TranslationException.Configuration("Текст слишком длинный (максимум $MAX_INPUT_CHARACTERS символов)")
        }

        val errors = mutableListOf<String>()
        val candidates = providers.filter { it.supports(request.operation) }
        if (candidates.isEmpty()) {
            throw TranslationException.NoProvider("Нет провайдера для ${request.operation}")
        }

        for (provider in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                val result = provider.execute(request)
                currentCoroutineContext().ensureActive()
                if (result.outputText.isBlank()) {
                    throw TranslationException.ProviderFailure("Провайдер вернул пустой ответ")
                }
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: TranslationException) {
                errors += "${provider.id}: ${e.message}"
            } catch (e: Exception) {
                errors += "${provider.id}: ошибка подключения или обработки ответа"
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

    companion object {
        const val MAX_INPUT_CHARACTERS = TranslationRequest.MAX_TEXT_LENGTH
    }
}
