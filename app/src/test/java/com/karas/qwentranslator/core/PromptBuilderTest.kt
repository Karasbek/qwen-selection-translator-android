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
}
