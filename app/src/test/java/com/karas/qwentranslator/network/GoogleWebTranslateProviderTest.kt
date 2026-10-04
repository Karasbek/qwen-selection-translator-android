package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.TranslationRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import com.karas.qwentranslator.core.TranslationException
import org.junit.Test

class GoogleWebTranslateProviderTest {
    @Test
    fun parsesMultipleSegments() {
        val provider = GoogleWebTranslateProvider(OkHttpClient())
        val result = provider.parse(
            body = "[[[\"Привет \"],[\"мир\"]],null,\"en\"]",
            request = TranslationRequest("Hello world", Operation.TRANSLATE, targetLanguage = "ru"),
        )
        assertEquals("Привет мир", result.outputText)
        assertEquals("en", result.detectedSourceLanguage)
    }

    @Test
    fun rejectsMalformedNullAndEmptySegments() {
        val provider = GoogleWebTranslateProvider(OkHttpClient())
        for (body in listOf("invalid", "[]", "[[],null,null]", "[[[null]],null,null]", "[[[42]],null,null]")) {
            assertThrows(TranslationException.ProviderFailure::class.java) {
                provider.parse(body, TranslationRequest("text", Operation.TRANSLATE))
            }
        }
    }
}
