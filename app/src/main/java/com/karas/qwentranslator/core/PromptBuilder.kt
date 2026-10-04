package com.karas.qwentranslator.core

object PromptBuilder {
    fun build(request: TranslationRequest): String = when (request.operation) {
        Operation.TRANSLATE -> translate(request)
        Operation.EXPLAIN -> explain(request)
    }

    private fun translate(request: TranslationRequest): String = """
        You are a translation engine, not a chat assistant.
        Translate the text below into language code "${request.targetLanguage}".
        ${if (request.sourceLanguage == "auto") "Detect the source language." else "Source language code: ${request.sourceLanguage}."}
        Preserve meaning, tone, paragraph breaks, punctuation, names, and technical terminology.
        Do not add notes, quotes, labels, markdown, alternatives, or explanations.
        Return only the translated text.

        TEXT:
        ${request.text}
    """.trimIndent()

    private fun explain(request: TranslationRequest): String {
        val options = request.explanation
        val language = when (options.language) {
            ExplanationLanguage.ENGLISH -> "Write only in English. Explain English text using simple English. Do not include a Russian translation."
            ExplanationLanguage.SAME_AS_SOURCE -> "Use the same language as the selected text. For English text, use simple English."
            ExplanationLanguage.RUSSIAN -> "Write the explanation only in Russian, using clear everyday language."
        }
        val detail = when (options.detail) {
            ExplanationDetail.SIMPLE -> "Use familiar words. Give the main meaning in one or two short sentences."
            ExplanationDetail.NORMAL -> "Explain the meaning and relevant context in two to four short sentences."
            ExplanationDetail.DETAILED -> "Explain meaning, nuance and relevant usage in one compact paragraph of at most six sentences."
        }
        val examples = if (options.examples) "Add at most one short, useful example in the explanation language."
            else "Do not include examples."
        return """
            Explain the selected text to someone learning the language.
            $language
            $detail
            $examples
            For a word or phrase, give its contextual meaning. For a sentence, explain what it means.
            Do not merely translate, repeat the original text, write an essay, or add meta commentary.
            Return only the explanation, without headings or markdown.

            SELECTED TEXT:
            ${request.text}
        """.trimIndent()
    }
}
