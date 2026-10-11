package com.karas.qwentranslator.anki

import com.karas.qwentranslator.core.ExplanationLanguage

data class CardGenerationRequest(
    val selection: String,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "ru",
    val explanationLanguage: ExplanationLanguage = ExplanationLanguage.ENGLISH,
    val explanation: String? = null,
    val translation: String? = null,
)
interface CardGenerator { suspend fun generate(request: CardGenerationRequest): CardContent }
data class DictionaryIpa(val text: String, val source: String)
interface IpaLookup { suspend fun lookup(word: String, sourceLanguage: String): DictionaryIpa? }
