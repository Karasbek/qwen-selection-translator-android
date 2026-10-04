package com.karas.qwentranslator.core

object PromptBuilder {
    fun build(request: TranslationRequest): String = when (request.operation) {
        Operation.TRANSLATE -> translate(request)
        Operation.EXPLAIN -> explain(request)
    }

    private fun translate(request: TranslationRequest): String = """
        You are a translation engine, not a chat assistant.
        Translate the text below into language code "${request.targetLanguage}".
        Preserve meaning, tone, paragraph breaks, punctuation, names, and technical terminology.
        Do not add notes, quotes, labels, markdown, alternatives, or explanations.
        Return only the translated text.

        TEXT:
        ${request.text}
    """.trimIndent()

    private fun explain(request: TranslationRequest): String = """
        Explain the selected text clearly and concisely.
        Use the same language as the selected text whenever possible.
        For English text, use simple English, not a translation into another language.
        If it is a single word or short phrase, give its contextual meaning and one simple example.
        If it is a sentence, explain the meaning rather than merely translating it.
        Do not use markdown headings. Keep the answer compact.

        SELECTED TEXT:
        ${request.text}
    """.trimIndent()
}
