package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.FakeSecrets
import com.karas.qwentranslator.anki.CardGenerationRequest
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*

class ApiUsageTest {
    private val data = """{"usage":{"prompt_tokens":20,"completion_tokens":10,"total_tokens":30,"prompt_tokens_details":{"cached_tokens":0}},"choices":[{"message":{"content":"result"},"finish_reason":"stop"}]}"""
    private fun client(body: String, code: Int = 200) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
    }.build()
    private fun provider(body: String, tracker: ApiUsageTracker, code: Int = 200) =
        OpenAiCompatibleProvider(client(body, code), FakeSettings(), FakeSecrets("fixture-key"), tracker)
    @Test fun successfulRequestsAreSeparatedAndDeterministicUsageIsSummed() = runBlocking {
        val tracker = ApiUsageTracker(); val provider = provider(data, tracker)
        provider.execute(TranslationRequest("fixture", Operation.EXPLAIN))
        provider.execute(TranslationRequest("fixture", Operation.TRANSLATE))
        val cardJson = data.replace("result", "{\\\"word\\\":\\\"agree\\\",\\\"partOfSpeech\\\":\\\"verb\\\",\\\"definition\\\":\\\"To agree means sharing an opinion.\\\",\\\"example\\\":\\\"We agree today.\\\"}")
        GroqCardGenerator(client(cardJson), FakeSettings(), FakeSecrets("fixture-key"), tracker).generate(CardGenerationRequest("agree"))
        for (op in ApiOperation.entries) {
            val counts = tracker.state.value.operations.getValue(op)
            assertEquals(1, counts.started.toInt()); assertEquals(1, counts.success.toInt()); assertEquals(0, counts.pending.toInt())
            assertEquals(TokenTotal(20, 1), counts.prompt); assertEquals(TokenTotal(10, 1), counts.completion)
            assertEquals(TokenTotal(30, 1), counts.total); assertEquals(TokenTotal(0, 1), counts.cached)
        }
    }
    @Test fun missingAndPartialUsageRemainUnknownInsteadOfZero() = runBlocking {
        val tracker = ApiUsageTracker()
        provider("""{"choices":[{"message":{"content":"result"}}]}""", tracker).execute(TranslationRequest("fixture", Operation.EXPLAIN))
        val counts = tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
        assertEquals("неизвестно", counts.total.display()); assertEquals(0, counts.total.samples.toInt())
        assertEquals(ApiTokens(prompt = 7), parseApiTokens("""{"usage":{"prompt_tokens":7}}"""))
        assertEquals(ApiTokens(), parseApiTokens("""{"usage":{"prompt_tokens":-1,"completion_tokens":"20","total_tokens":1.2}}"""))
        assertEquals(ApiTokens(), parseApiTokens("private-invalid"))
    }
    @Test fun malformedSuccessCountsErrorButRetainsReturnedTokenUsage() = runBlocking {
        val tracker = ApiUsageTracker()
        try { provider("""{"usage":{"prompt_tokens":9,"completion_tokens":8,"total_tokens":17}}""", tracker).execute(TranslationRequest("fixture", Operation.EXPLAIN)); fail() }
        catch (_: TranslationException) { }
        val counts = tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
        assertEquals(1, counts.errors.toInt()); assertEquals(0, counts.success.toInt()); assertEquals(TokenTotal(17, 1), counts.total)
    }
    @Test fun httpFailureCountsOnceAndDoesNotTreatDiscardedBodyAsKnownUsage() = runBlocking {
        val tracker = ApiUsageTracker()
        try { provider(data, tracker, 429).execute(TranslationRequest("fixture", Operation.TRANSLATE)); fail() }
        catch (_: TranslationException) { }
        val counts = tracker.state.value.operations.getValue(ApiOperation.TRANSLATE)
        assertEquals(1, counts.started.toInt()); assertEquals(1, counts.errors.toInt()); assertEquals(0, counts.total.samples.toInt())
    }
    @Test fun missingKeyDoesNotCountAsAnEnqueuedRequest() = runBlocking {
        val tracker = ApiUsageTracker()
        try { OpenAiCompatibleProvider(client(data), FakeSettings(), FakeSecrets(""), tracker).execute(TranslationRequest("fixture", Operation.EXPLAIN)); fail() }
        catch (_: TranslationException) { }
        assertEquals(0, tracker.state.value.operations.getValue(ApiOperation.EXPLAIN).started.toInt())
    }
    @Test fun cancelledCallCountsCancellationAndUnknownTokens() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(data).setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS))
            val tracker = ApiUsageTracker()
            val countsClient = OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url("/fixture")).build()) }
                .eventListener(object : EventListener() {
                    override fun requestHeadersStart(call: Call) { call.request().tag(ApiOperation::class.java)?.let(tracker::httpAttempt) }
                }).build()
            val action = launch { OpenAiCompatibleProvider(countsClient, FakeSettings(), FakeSecrets("fixture-key"), tracker).execute(TranslationRequest("fixture", Operation.EXPLAIN)) }
            withContext(Dispatchers.IO) { checkNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)) }
            action.cancelAndJoin()
            val counts = tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
            assertEquals(1, counts.started.toInt()); assertEquals(1, counts.httpAttempts.toInt()); assertEquals(1, counts.cancelled.toInt())
            assertEquals(0, counts.errors.toInt()); assertEquals(0, counts.total.samples.toInt()); assertEquals(0, counts.pending.toInt())
        }
    }
    @Test fun duplicateFinishCannotDoubleCountAndPartialTotalsShowSampleCounts() {
        val tracker = ApiUsageTracker(); val attempt = tracker.start(ApiOperation.ANKI_DRAFT)
        attempt.finish(ApiOutcome.SUCCESS, ApiTokens(total = 10)); attempt.finish(ApiOutcome.ERROR, ApiTokens(total = 10))
        tracker.start(ApiOperation.ANKI_DRAFT).finish(ApiOutcome.CANCELLED)
        val counts = tracker.state.value.operations.getValue(ApiOperation.ANKI_DRAFT)
        assertEquals(TokenTotal(10, 1), counts.total); assertEquals(1, counts.success.toInt()); assertEquals(0, counts.errors.toInt())
        assertEquals(1, counts.cancelled.toInt()); assertEquals(0, counts.pending.toInt())
    }
}
