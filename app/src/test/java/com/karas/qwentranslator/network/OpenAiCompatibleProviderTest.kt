package com.karas.qwentranslator.network

import com.karas.qwentranslator.FakeSecrets
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class OpenAiCompatibleProviderTest {
    private val request = TranslationRequest("Hello\n世界 👋", Operation.TRANSLATE)
    private fun provider(client: OkHttpClient = OkHttpClient()) = OpenAiCompatibleProvider(client, FakeSettings(), FakeSecrets())

    @Test fun parsesUnicodeAndMultilineContent() {
        val result = provider().parse("""{"choices":[{"message":{"content":"Привет\n世界 👋"},"finish_reason":"stop"}]}""", request)
        assertEquals("Привет\n世界 👋", result.outputText)
        assertEquals(Operation.TRANSLATE, result.operation)
    }

    @Test fun rejectsMissingEmptyNullAndMalformedResponses() {
        for (body in listOf("invalid", "{}", """{"choices":[]}""", """{"choices":[{"message":{"content":null}}]}""", """{"choices":[{"message":{"content":42}}]}""", """{"choices":[{"message":{"content":" "}}]}""")) {
            assertThrows(TranslationException.ProviderFailure::class.java) { provider().parse(body, request) }
        }
    }

    @Test fun rejectsTruncatedOutput() {
        assertThrows(TranslationException.ProviderFailure::class.java) {
            provider().parse("""{"choices":[{"message":{"content":"partial"},"finish_reason":"length"}]}""", request)
        }
    }

    @Test fun configuredModelAndGroqExtensionsAreInRequest() {
        val settings = AppSettings(model = "qwen/custom-test-model")
        val body = provider().buildBody(settings, request)
        assertEquals(settings.model, body.getString("model"))
        assertEquals("none", body.getString("reasoning_effort"))
        assertEquals("hidden", body.getString("reasoning_format"))
        assertFalse(body.getBoolean("stream"))
        assertEquals(1024, body.getInt("max_completion_tokens"))
        assertEquals(4096, provider().buildBody(settings, request.copy(text = "x".repeat(12_000))).getInt("max_completion_tokens"))
    }

    @Test fun genericCompatibleApiDoesNotReceiveGroqExtensions() {
        val body = provider().buildBody(AppSettings(baseUrl = "https://example.org/v1", model = "other"), request)
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("reasoning_format"))
    }

    @Test fun explainRequestUsesOriginalLanguagePrompt() {
        val body = provider().buildBody(AppSettings(), request.copy(operation = Operation.EXPLAIN))
        val prompt = body.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(prompt.contains("simple English"))
        assertFalse(prompt.contains("Translate the text below"))
    }

    @Test fun httpErrorsNeverExposeServerBodyOrKey() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("unauthorized")
                .body("private-header-value".toResponseBody()).build()
        }.build()
        try { provider(client).execute(request); fail("Expected HTTP failure") }
        catch (e: TranslationException.ProviderFailure) {
            assertTrue(e.message.orEmpty().contains("401"))
            assertFalse(e.message.orEmpty().contains("private-header-value"))
            assertFalse(e.stackTraceToString().contains("private-header-value"))
        }
    }

    @Test fun executesExplainThroughHttpContract() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/openai/v1/chat/completions", chain.request().url.encodedPath)
            assertNotNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"choices":[{"message":{"content":"A simple explanation"}}]}""".toResponseBody()).build()
        }.build()
        assertEquals(Operation.EXPLAIN, provider(client).execute(request.copy(operation = Operation.EXPLAIN)).operation)
    }
}
