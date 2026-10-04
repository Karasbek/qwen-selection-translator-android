package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.TranslationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpPayload(val code: Int, val body: String)

/** Read on OkHttp's worker, bound memory, close every response, cancel the socket with the job. */
internal suspend fun Call.awaitPayload(): HttpPayload = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(
                TranslationException.ProviderFailure("Нет сети или истекло время ожидания"),
            )
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val payload = response.use {
                    // Do not read/echo server error bodies: they may contain credentials or input.
                    if (!it.isSuccessful) return@use HttpPayload(it.code, "")
                    val source = it.body.source()
                    val limit = 1_048_576L
                    source.request(limit + 1)
                    if (source.buffer.size > limit) {
                        throw TranslationException.ProviderFailure("Ответ сервера слишком большой")
                    }
                    HttpPayload(it.code, source.readUtf8())
                }
                if (continuation.isActive) continuation.resume(payload)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(
                    if (e is TranslationException) e else
                        TranslationException.ProviderFailure("Не удалось прочитать ответ сервера"),
                )
            }
        }
    })
}
