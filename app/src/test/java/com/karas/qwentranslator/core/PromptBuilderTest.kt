package com.karas.qwentranslator.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun translationPromptContainsTargetAndText() {
        val prompt = PromptBuilder.build(
            TranslationRequest(
                text = "This is a test.",
                operation = Operation.TRANSLATE,
                targetLanguage = "ru",
            ),
        )
        assertTrue(prompt.contains("ru"))
        assertTrue(prompt.contains("This is a test."))
        assertTrue(prompt.contains("Return only the translated text"))
    }

    @Test
    fun explainPromptDoesNotAskForTranslation() {
        val prompt = PromptBuilder.build(
            TranslationRequest(
                text = "inconclusive",
                operation = Operation.EXPLAIN,
            ),
        )
        assertTrue(prompt.contains("Explain the selected text"))
        assertFalse(prompt.contains("Translate the text below"))
    }

    @Test fun defaultExplanationIsSimpleEnglishWithAtMostOneExample() {
        val prompt = PromptBuilder.build(TranslationRequest("abandon", Operation.EXPLAIN))
        assertTrue(prompt.contains("Write only in English"))
        assertTrue(prompt.contains("one or two short sentences"))
        assertTrue(prompt.contains("at most one short"))
        assertTrue(prompt.contains("Do not include a Russian translation"))
    }
    @Test fun allExplanationSettingsInfluencePromptIndependently() {
        val languages = mapOf(ExplanationLanguage.ENGLISH to "Write only in English",
            ExplanationLanguage.RUSSIAN to "only in Russian", ExplanationLanguage.SAME_AS_SOURCE to "same language")
        val details = mapOf(ExplanationDetail.SIMPLE to "one or two", ExplanationDetail.NORMAL to "two to four",
            ExplanationDetail.DETAILED to "at most six")
        for ((language, languageText) in languages) for ((detail, detailText) in details) for (examples in listOf(true, false)) {
            val prompt = PromptBuilder.build(TranslationRequest("word", Operation.EXPLAIN,
                explanation = ExplanationOptions(language, detail, examples)))
            assertTrue(prompt.contains(languageText))
            assertTrue(prompt.contains(detailText))
            assertTrue(prompt.contains(if (examples) "at most one short" else "Do not include examples"))
        }
    }
    @Test fun explanationOptionsDoNotChangeTranslationPrompt() {
        val request = TranslationRequest("word", Operation.TRANSLATE)
        org.junit.Assert.assertEquals(PromptBuilder.build(request), PromptBuilder.build(request.copy(
            explanation = ExplanationOptions(ExplanationLanguage.RUSSIAN, ExplanationDetail.DETAILED, false))))
    }
}
