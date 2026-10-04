package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.TranslationException
import com.karas.qwentranslator.core.TranslationRequest
import com.karas.qwentranslator.core.TranslationResult
import com.karas.qwentranslator.core.TranslatorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Unofficial public Google Translate web endpoint used only as a best-effort MVP
 * fallback when the configured Qwen endpoint is unavailable. It is intentionally
 * isolated behind TranslatorProvider so it can be removed without touching UI.
 */
class GoogleWebTranslateProvider(
    private val client: OkHttpClient,
) : TranslatorProvider {
    override val id: String = "Google web fallback"

    override fun supports(operation: Operation): Boolean = operation == Operation.TRANSLATE

    override suspend fun execute(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl()
            .newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", request.sourceLanguage)
            .addQueryParameter("tl", request.targetLanguage)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", request.text)
            .build()

        val httpRequest = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw TranslationException.ProviderFailure("Google fallback HTTP ${response.code}")
            }
            parse(body, request)
        }
    }

    internal fun parse(body: String, request: TranslationRequest): TranslationResult {
        try {
            val root = JSONArray(body)
            val segments = root.getJSONArray(0)
            val output = buildString {
                for (index in 0 until segments.length()) {
                    val segment = segments.optJSONArray(index) ?: continue
                    append(segment.optString(0))
                }
            }.trim()
            if (output.isBlank()) {
                throw TranslationException.ProviderFailure("Google fallback вернул пустой перевод")
            }
            return TranslationResult(
                outputText = output,
                providerId = id,
                detectedSourceLanguage = root.optString(2).takeIf { it.isNotBlank() },
                operation = request.operation,
            )
        } catch (e: TranslationException) {
            throw e
        } catch (e: Exception) {
            throw TranslationException.ProviderFailure("Не удалось разобрать Google fallback", e)
        }
    }
}
