package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.ui.popup.FailurePresentation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FailureMappingTest {
    @Test fun httpStatusHasDistinctStructuredCategories() {
        val expected = mapOf(401 to FailureKind.AUTHENTICATION, 403 to FailureKind.ACCESS_DENIED,
            429 to FailureKind.RATE_LIMIT, 408 to FailureKind.TIMEOUT, 504 to FailureKind.TIMEOUT,
            503 to FailureKind.UNAVAILABLE, 404 to FailureKind.CONFIGURATION, 418 to FailureKind.UNKNOWN)
        expected.forEach { (code, kind) -> assertEquals(kind, HttpFailureMapping.status(code)) }
    }
    @Test fun timeoutAndNoNetworkAreDistinct() {
        assertEquals(FailureKind.TIMEOUT, HttpFailureMapping.network(SocketTimeoutException("private")))
        assertEquals(FailureKind.NETWORK, HttpFailureMapping.network(UnknownHostException("private")))
        assertEquals(FailureKind.UNKNOWN, TranslationFailure.from(IllegalStateException("private")).kind)
    }
    @Test fun presentationNeverUsesRawExceptionOrServerMessage() {
        val error = TranslationException.ProviderFailure("<html>private credential</html>", kind = FailureKind.AUTHENTICATION, provider = "Groq")
        val message = FailurePresentation.message(TranslationFailure.from(error))
        assertTrue(message.contains("Groq"))
        assertFalse(message.contains("private"))
        for (kind in FailureKind.entries) assertTrue(FailurePresentation.message(TranslationFailure(kind)).isNotBlank())
    }
    @Test fun enginePreservesFailureKindWhenAllProvidersFail() = runTest {
        val engine = DefaultTranslatorEngine(listOf(object : TranslatorProvider {
            override val id = "AI"
            override fun supports(operation: Operation) = true
            override suspend fun execute(request: TranslationRequest): TranslationResult =
                throw TranslationException.ProviderFailure("private", kind = FailureKind.AUTHENTICATION, provider = "Groq")
        }))
        try { engine.execute(TranslationRequest("Hello", Operation.EXPLAIN)); fail("Expected error") }
        catch (error: TranslationException.NoProvider) {
            assertEquals(FailureKind.AUTHENTICATION, error.kind)
            assertEquals("Groq", error.provider)
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }
}
