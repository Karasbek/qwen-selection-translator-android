package com.karas.qwentranslator.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DefaultTranslatorEngineTest {
    private fun provider(name: String, operations: Set<Operation> = Operation.entries.toSet(), action: suspend (TranslationRequest) -> TranslationResult) =
        object : TranslatorProvider {
            override val id = name
            override fun supports(operation: Operation) = operation in operations
            override suspend fun execute(request: TranslationRequest) = action(request)
        }

    @Test fun translateFallsBackOnce() = runTest {
        var attempts = 0
        val first = provider("AI") { attempts++; throw TranslationException.Configuration("Нет ключа") }
        val fallback = provider("web", setOf(Operation.TRANSLATE)) { attempts++; TranslationResult("перевод", "web", "en", it.operation) }
        val result = DefaultTranslatorEngine(listOf(first, fallback)).execute(TranslationRequest("text", Operation.TRANSLATE))
        assertEquals("перевод", result.outputText)
        assertEquals(2, attempts)
    }

    @Test fun explainNeverUsesTranslateOnlyFallback() = runTest {
        var used = false
        val first = provider("AI") { throw TranslationException.Configuration("Нет ключа") }
        val fallback = provider("web", setOf(Operation.TRANSLATE)) { used = true; error("unexpected") }
        try {
            DefaultTranslatorEngine(listOf(first, fallback)).execute(TranslationRequest("text", Operation.EXPLAIN))
            fail("Expected failure")
        } catch (e: TranslationException.NoProvider) { assertFalse(used) }
    }

    @Test fun explainPreservesOperation() = runTest {
        val ai = provider("AI") { TranslationResult("simple explanation", "AI", operation = it.operation) }
        assertEquals(Operation.EXPLAIN, DefaultTranslatorEngine(listOf(ai)).execute(TranslationRequest("word", Operation.EXPLAIN)).operation)
    }

    @Test fun cancellationDoesNotFallback() = runTest {
        var used = false
        val first = provider("AI") { throw CancellationException("cancel") }
        val second = provider("web") { used = true; error("unexpected") }
        try {
            DefaultTranslatorEngine(listOf(first, second)).execute(TranslationRequest("text", Operation.TRANSLATE))
            fail("Expected cancellation")
        } catch (e: CancellationException) { assertFalse(used) }
    }

    @Test fun unknownExceptionsAreNotExposed() = runTest {
        val ai = provider("AI") { error("private-header-value") }
        try {
            DefaultTranslatorEngine(listOf(ai)).execute(TranslationRequest("text", Operation.TRANSLATE))
            fail("Expected failure")
        } catch (e: TranslationException.NoProvider) {
            assertFalse(e.message.orEmpty().contains("private-header-value"))
        }
    }

    @Test fun blankAndHugeInputNeverReachProviders() = runTest {
        var used = false
        val ai = provider("AI") { used = true; error("unexpected") }
        for (text in listOf(" \n", "x".repeat(DefaultTranslatorEngine.MAX_INPUT_CHARACTERS + 1))) {
            try { DefaultTranslatorEngine(listOf(ai)).execute(TranslationRequest(text, Operation.TRANSLATE)); fail("Expected rejection") }
            catch (e: Exception) { assertFalse(used) }
        }
    }

    @Test fun emptyProviderResultFallsBack() = runTest {
        val first = provider("AI") { TranslationResult(" ", "AI", operation = it.operation) }
        val second = provider("web") { TranslationResult("ok", "web", operation = it.operation) }
        assertEquals("web", DefaultTranslatorEngine(listOf(first, second)).execute(TranslationRequest("text", Operation.TRANSLATE)).providerId)
    }
}
