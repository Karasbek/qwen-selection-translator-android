package com.karas.qwentranslator.network

import com.karas.qwentranslator.*
import com.karas.qwentranslator.anki.*
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.InterruptedIOException

class GroqCardGeneratorTest {
    private val request = CardGenerationRequest("abandon", sourceLanguage = "en", explanation = "leave something", translation = "покинуть")
    private val data = JSONObject().put("word", "abandon").put("partOfSpeech", "verb")
        .put("definition", "To abandon means to leave behind.").put("example", "They abandon the project.")
    private fun envelope(content: Any?, reason: String = "stop") = JSONObject().put("choices", JSONArray().put(JSONObject()
        .put("message", JSONObject().put("content", content ?: JSONObject.NULL)).put("finish_reason", reason))).toString()
    private fun provider(client: OkHttpClient = OkHttpClient(), key: String? = "placeholder") = GroqCardGenerator(client, FakeSettings(), FakeSecrets(key))

    @Test fun separatePromptContainsContextSettingsAndPlainTextContract() {
        val body = provider().buildBody(AppSettings(), request.copy(explanationLanguage = ExplanationLanguage.RUSSIAN))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("none", body.getString("reasoning_effort"))
        val messages = body.getJSONArray("messages")
        val instruction = messages.getJSONObject(0).getString("content")
        assertTrue(instruction.contains("Russian"))
        assertTrue(instruction.contains("Never substitute a lemma"))
        val context = JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals(request.selection, context.getString("selection"))
        assertEquals(request.explanation, context.getString("explanation"))
        assertEquals(request.translation, context.getString("translation"))
        assertFalse(instruction == PromptBuilder.build(TranslationRequest("abandon", Operation.EXPLAIN)))
    }
    @Test fun parsesStructuredDataButDiscardsInventedIpa() {
        val content = provider().parse(envelope(data.put("ipa", "/fake/").toString()), "abandon")
        assertEquals("abandon", content.word)
        assertEquals("verb", content.partOfSpeech)
        assertEquals("They abandon the project.", content.example)
    }
    @Test fun preservesSelectionWhenModelReturnsLemma() {
        val content = provider().parse(envelope(data.put("word", "run").toString()), "running")
        assertEquals("running", content.word)
        assertTrue(ClozeCardBuilder.build(content, "").fields.isEmpty())
    }
    @Test fun rejectsEmptyMalformedTruncatedAndWrongTypesWithoutLeakingResponse() {
        for (raw in listOf("", "private-malformed", "{}", "[]", data.put("example", 42).toString(),
            data.put("example", "x").remove("word").let { data.toString() }, "{} trailing")) {
            try { provider().parse(envelope(raw), "abandon"); fail("Must reject") }
            catch (e: TranslationException) { assertEquals(FailureKind.MALFORMED_RESPONSE, e.kind); assertFalse(e.stackTraceToString().contains("private-malformed")) }
        }
        assertThrows(TranslationException::class.java) { provider().parse(envelope(null), "abandon") }
        assertThrows(TranslationException::class.java) { provider().parse(envelope("{}", "length"), "abandon") }
    }
    @Test fun missingKeyDoesNotMakeNetworkRequest() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { error("Network must not run") }.build()
        try { provider(client, "").generate(request); fail("Missing key") }
        catch (e: TranslationException) { assertEquals(FailureKind.CONFIGURATION, e.kind) }
    }
    @Test fun mapsHttpErrorsWithoutServerBodies() = runBlocking {
        for ((status, kind) in listOf(401 to FailureKind.AUTHENTICATION, 403 to FailureKind.ACCESS_DENIED,
            429 to FailureKind.RATE_LIMIT, 500 to FailureKind.UNAVAILABLE)) {
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(status).message("error").body("private-body".toResponseBody()).build() }.build()
            try { provider(client).generate(request); fail("HTTP failure") }
            catch (e: TranslationException) { assertEquals(kind, e.kind); assertFalse(e.stackTraceToString().contains("private-body")) }
        }
    }
    @Test fun mapsTimeoutAndSuccessfulRequest() = runBlocking {
        val timeout = OkHttpClient.Builder().addInterceptor { throw InterruptedIOException("private") }.build()
        try { provider(timeout).generate(request); fail("Timeout") }
        catch (e: TranslationException) { assertEquals(FailureKind.TIMEOUT, e.kind) }
        var calls = 0
        val ok = OkHttpClient.Builder().addInterceptor { chain -> calls++; Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(envelope(data.toString()).toResponseBody()).build() }.build()
        assertEquals("abandon", provider(ok).generate(request).word)
        assertEquals(1, calls)
    }
}
