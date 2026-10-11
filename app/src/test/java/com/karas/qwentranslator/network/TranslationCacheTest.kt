package com.karas.qwentranslator.network

import com.karas.qwentranslator.FakeSecrets
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TranslationCacheTest {
    private val request = TranslationRequest("agree", Operation.EXPLAIN)
    private fun response(finish: String = "stop") = """{"usage":{"prompt_tokens":20,"completion_tokens":10,"total_tokens":30},"choices":[{"message":{"content":"fixture result"},"finish_reason":"$finish"}]}"""
    private class Fixture(val settings: FakeSettings = FakeSettings(), val tracker: ApiUsageTracker = ApiUsageTracker(),
        val cache: TranslationMemoryCache = TranslationMemoryCache()) {
        val calls = AtomicInteger()
        var body = """{"usage":{"total_tokens":30},"choices":[{"message":{"content":"fixture result"},"finish_reason":"stop"}]}"""
        var code = 200
        val provider = OpenAiCompatibleProvider(OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fake")
                .code(code).body(body.toResponseBody()).build()
        }.build(), settings, FakeSecrets("fake-only-key"), tracker, cache)
    }
    @Test fun identicalRequestUsesCachedResponseWithoutRecountingUsage() = runBlocking {
        val f = Fixture(); f.body = response()
        val first = f.provider.execute(request)
        assertEquals(first, f.provider.execute(request))
        assertEquals(1, f.calls.get())
        val counts = f.tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
        assertEquals(1L, counts.cacheHits); assertEquals(1L, counts.cacheMisses)
        assertEquals(1L, counts.started); assertEquals(TokenTotal(30, 1), counts.total)
    }
    @Test fun exactTextAndOperationHaveIndependentEntries() = runBlocking {
        val f = Fixture()
        listOf(request, request.copy(text = " agree"), request.copy(text = "Agree"), request.copy(operation = Operation.TRANSLATE))
            .forEach { f.provider.execute(it); f.provider.execute(it) }
        assertEquals(4, f.calls.get())
        assertEquals(3L, f.tracker.state.value.operations.getValue(ApiOperation.EXPLAIN).cacheHits)
        assertEquals(1L, f.tracker.state.value.operations.getValue(ApiOperation.TRANSLATE).cacheHits)
    }
    @Test fun translationLanguagesInvalidateOnlyActualTranslationPrompts() = runBlocking {
        val f = Fixture(); val translate = request.copy(operation = Operation.TRANSLATE)
        f.provider.execute(translate)
        f.provider.execute(translate.copy(targetLanguage = "en"))
        f.provider.execute(translate.copy(sourceLanguage = "en"))
        assertEquals(3, f.calls.get())
        f.provider.execute(request)
        f.provider.execute(request.copy(targetLanguage = "en", sourceLanguage = "en"))
        assertEquals(4, f.calls.get()) // Explain does not send translation languages.
    }
    @Test fun explanationOptionsInvalidateExplainButNotTranslate() = runBlocking {
        val f = Fixture(); f.provider.execute(request)
        f.provider.execute(request.copy(explanation = request.explanation.copy(examples = false)))
        f.provider.execute(request.copy(explanation = request.explanation.copy(language = ExplanationLanguage.RUSSIAN)))
        assertEquals(3, f.calls.get())
        val translate = request.copy(operation = Operation.TRANSLATE)
        f.provider.execute(translate)
        f.provider.execute(translate.copy(explanation = request.explanation.copy(examples = false)))
        assertEquals(4, f.calls.get())
    }
    @Test fun modelEndpointAndWireParametersInvalidateEntries() = runBlocking {
        val f = Fixture(); val initial = f.settings.value
        f.provider.execute(request)
        f.settings.value = initial.copy(model = "different-model")
        f.provider.execute(request)
        f.settings.value = initial.copy(baseUrl = "https://fixture.invalid/v1/")
        f.provider.execute(request)
        f.settings.value = initial
        f.provider.execute(request)
        assertEquals(3, f.calls.get())
        assertNotEquals(TranslationCacheKey("x", Operation.EXPLAIN, "https://fixture.invalid", "temperature=0.2"),
            TranslationCacheKey("x", Operation.EXPLAIN, "https://fixture.invalid", "temperature=0.4"))
    }
    @Test fun ttlIsMeasuredFromInsertionAndHitDoesNotExtendIt() = runBlocking {
        var now = 0L
        val f = Fixture(cache = TranslationMemoryCache(ttlMillis = 100, clockMillis = { now }))
        f.provider.execute(request); now = 99; f.provider.execute(request)
        now = 100; f.provider.execute(request)
        assertEquals(2, f.calls.get())
    }
    @Test fun lruEvictsLeastRecentlyUsedEntry() = runBlocking {
        val f = Fixture(cache = TranslationMemoryCache(capacity = 2))
        f.provider.execute(request); f.provider.execute(request.copy(text = "b"))
        f.provider.execute(request); f.provider.execute(request.copy(text = "c"))
        f.provider.execute(request); f.provider.execute(request.copy(text = "b"))
        assertEquals(4, f.calls.get())
    }
    @Test fun memoryBudgetDoesNotRetainOversizedResults() = runBlocking {
        val f = Fixture(cache = TranslationMemoryCache(maxCharacters = 1))
        repeat(2) { f.provider.execute(request) }
        assertEquals(2, f.calls.get())
    }
    @Test fun retryBypassesCacheAndUpdatesSuccessfulEntry() = runBlocking {
        val f = Fixture(); f.provider.execute(request)
        f.body = response().replace("fixture result", "fresh result")
        assertEquals("fresh result", f.provider.execute(request.copy(bypassCache = true)).outputText)
        assertEquals("fresh result", f.provider.execute(request).outputText)
        assertEquals(2, f.calls.get())
        val counts = f.tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
        assertEquals(1L, counts.cacheBypasses); assertEquals(1L, counts.cacheHits); assertEquals(2L, counts.started)
    }
    @Test fun errorsMalformedAndIncompleteResponsesAreNeverCached() = runBlocking {
        for (body in listOf("broken-json", response("length"), response("content_filter"), response().replace("fixture result", ""))) {
            val f = Fixture(); f.body = body
            repeat(2) { try { f.provider.execute(request) } catch (_: TranslationException) { } }
            assertEquals(2, f.calls.get())
            assertEquals(0L, f.tracker.state.value.operations.getValue(ApiOperation.EXPLAIN).cacheHits)
        }
        val f = Fixture(); f.code = 429
        repeat(2) { try { f.provider.execute(request) } catch (_: TranslationException) { } }
        f.code = 200; f.provider.execute(request); f.provider.execute(request)
        assertEquals(3, f.calls.get())
    }
    private fun key(text: String = "a") = TranslationCacheKey(text, Operation.EXPLAIN, "fixture", "body")
    private fun loaded(text: String = "result") = CacheLoad(TranslationResult(text, "fake", operation = Operation.EXPLAIN))
    @Test fun simultaneousSameKeySharesSuccessAndDifferentKeysRemainIndependent() = runTest {
        val cache = TranslationMemoryCache(); val tracker = ApiUsageTracker()
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val a = async { cache.getOrLoad(key(), usage = tracker) { calls++; gate.await(); loaded() } }
        yield()
        val b = async { cache.getOrLoad(key(), usage = tracker) { calls++; loaded() } }
        val c = async { cache.getOrLoad(key("b")) { calls++; loaded("independent") } }
        assertEquals("independent", c.await().outputText)
        gate.complete(Unit)
        assertEquals(a.await(), b.await()); assertEquals(2, calls)
        assertEquals(1L, tracker.state.value.operations.getValue(ApiOperation.EXPLAIN).cacheHits)
    }
    @Test fun cancelledLoaderDoesNotPopulateCacheOrBlockNextCaller() = runTest {
        val cache = TranslationMemoryCache(); val started = CompletableDeferred<Unit>(); var calls = 0
        val a = launch { cache.getOrLoad(key()) { calls++; started.complete(Unit); awaitCancellation() } }
        started.await()
        val b = async { cache.getOrLoad(key()) { calls++; loaded() } }
        a.cancelAndJoin(); b.await()
        cache.getOrLoad(key()) { calls++; loaded() }
        assertEquals(2, calls)
    }
    @Test fun cancelledWaiterDoesNotEvictAnActiveGateOrStartAnotherLoader() = runTest {
        val cache = TranslationMemoryCache(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val a = async { cache.getOrLoad(key()) { calls++; gate.await(); loaded() } }
        yield()
        val b = launch { cache.getOrLoad(key()) { calls++; loaded() } }; yield(); b.cancelAndJoin()
        val c = async { cache.getOrLoad(key()) { calls++; loaded() } }
        gate.complete(Unit); a.await(); c.await()
        assertEquals(1, calls)
    }
    @Test fun bypassDoesNotReuseInFlightOrCachedSuccess() = runTest {
        val cache = TranslationMemoryCache(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val a = async { cache.getOrLoad(key()) { calls++; gate.await(); loaded() } }; yield()
        val b = async { cache.getOrLoad(key(), bypass = true) { calls++; loaded("fresh") } }
        gate.complete(Unit); a.await()
        assertEquals("fresh", b.await().outputText); assertEquals(2, calls)
    }
    @Test fun nonCancellableLateCancelledSuccessIsNotStored() = runTest {
        val cache = TranslationMemoryCache(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val a = launch { cache.getOrLoad(key()) { calls++; withContext(NonCancellable) { gate.await() }; loaded() } }
        yield(); a.cancel(); gate.complete(Unit); a.join()
        cache.getOrLoad(key()) { calls++; loaded() }
        assertEquals(2, calls)
    }
    @Test fun defaultLimitRetainsAtMostOneHundredEntries() = runBlocking {
        val f = Fixture()
        repeat(101) { f.provider.execute(request.copy(text = "fixture $it")) }
        f.provider.execute(request.copy(text = "fixture 100"))
        f.provider.execute(request.copy(text = "fixture 0"))
        assertEquals(102, f.calls.get())
    }
    @Test fun localWireCounterAndTokensOnlyIncreaseOnMissOrBypass() = runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            repeat(2) { server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(response())) }
            val tracker = ApiUsageTracker()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/fixture")).build())
            }.eventListener(object : EventListener() {
                override fun requestHeadersStart(call: Call) { call.request().tag(ApiOperation::class.java)?.let(tracker::httpAttempt) }
            }).build()
            val provider = OpenAiCompatibleProvider(client, FakeSettings(), FakeSecrets("fake-only-key"), tracker)
            provider.execute(request); provider.execute(request); provider.execute(request.copy(bypassCache = true))
            assertEquals(2, server.requestCount)
            val counts = tracker.state.value.operations.getValue(ApiOperation.EXPLAIN)
            assertEquals(2L, counts.httpAttempts); assertEquals(2L, counts.started)
            assertEquals(1L, counts.cacheHits); assertEquals(1L, counts.cacheMisses); assertEquals(1L, counts.cacheBypasses)
            assertEquals(TokenTotal(60, 2), counts.total)
        }
    }

}
