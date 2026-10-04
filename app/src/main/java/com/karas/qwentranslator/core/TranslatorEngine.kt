package com.karas.qwentranslator.core

interface TranslatorEngine {
    suspend fun execute(request: TranslationRequest): TranslationResult
}

interface TranslatorProvider {
    val id: String
    fun supports(operation: Operation): Boolean
    suspend fun execute(request: TranslationRequest): TranslationResult
}
