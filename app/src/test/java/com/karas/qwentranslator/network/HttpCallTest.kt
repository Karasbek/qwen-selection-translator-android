package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.TranslationException
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpCallTest {
    @Test fun readsBodyAndHttpStatus() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("response"))
            val result = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build()).awaitPayload()
            assertEquals(200, result.code)
            assertEquals("response", result.body)
        }
    }
    @Test fun cancellingJobCancelsSocket() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late response").setBodyDelay(5, TimeUnit.SECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val job = launch(Dispatchers.IO) { call.awaitPayload() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(call.isCanceled())
        }
    }
    @Test fun rejectsOversizedServerBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1_048_577)))
            try {
                OkHttpClient().newCall(Request.Builder().url(server.url("/")).build()).awaitPayload()
                fail("Expected oversized-body failure")
            } catch (e: TranslationException.ProviderFailure) { assertTrue(e.message.orEmpty().contains("слишком большой")) }
        }
    }
    @Test fun timeoutIsAReadableFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late").setHeadersDelay(2, TimeUnit.SECONDS))
            val client = OkHttpClient.Builder().callTimeout(100, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            try { client.newCall(Request.Builder().url(server.url("/")).build()).awaitPayload(); fail("Expected timeout") }
            catch (e: TranslationException.ProviderFailure) { assertFalse(e.stackTraceToString().contains("private-header-value")) }
        }
    }
}
