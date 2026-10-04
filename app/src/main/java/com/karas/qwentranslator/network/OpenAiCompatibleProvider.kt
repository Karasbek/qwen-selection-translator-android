package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.FailureKind
import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.PromptBuilder
import com.karas.qwentranslator.core.TranslationException
import com.karas.qwentranslator.core.TranslationRequest
import com.karas.qwentranslator.core.TranslationResult
import com.karas.qwentranslator.core.TranslatorProvider
import com.karas.qwentranslator.data.ApiKeyStore
import com.karas.qwentranslator.data.SettingsStore
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleProvider(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsStore,
    private val secretStore: ApiKeyStore,
) : TranslatorProvider {
    override val id: String = "AI"

    override fun supports(operation: Operation): Boolean = true

    override suspend fun execute(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = secretStore.getApiKey()
            ?.takeIf { it.isNotBlank() }
            ?: throw TranslationException.Configuration(
                "Groq API key не задан; перевод попробует fallback, а Explain требует ключ",
            )
        val settings = settingsRepository.snapshot()
        val endpoint = settings.endpoint()
            ?: throw TranslationException.Configuration("Некорректный HTTPS base URL")

        val body = buildBody(settings, request)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).awaitPayload()
        if (response.code !in 200..299) {
            throw TranslationException.ProviderFailure("AI HTTP ${response.code}",
                kind = HttpFailureMapping.status(response.code),
                provider = if (endpoint.host == "api.groq.com") "Groq" else "AI")
        }
        parse(response.body, request).copy(providerId = "${settings.model} · ${endpoint.host}")
    }

    internal fun buildBody(settings: AppSettings, request: TranslationRequest): JSONObject {
        val body = JSONObject()
            .put("model", settings.model)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user").put("content", PromptBuilder.build(request))))
            .put("temperature", if (request.operation == Operation.TRANSLATE) 0.2 else 0.4)
            .put("max_completion_tokens", if (request.operation == Operation.TRANSLATE)
                (request.text.length * 2 + 256).coerceIn(1024, 4096) else 1024)
            .put("stream", false)
        // Groq extensions are only valid for its Qwen models, not arbitrary compatible APIs.
        if (settings.endpoint()?.host == "api.groq.com" && settings.model.startsWith("qwen/")) {
            body.put("reasoning_effort", "none").put("reasoning_format", "hidden")
        }
        return body
    }

    internal fun parse(body: String, request: TranslationRequest): TranslationResult {
        try {
            val choice = JSONObject(body).getJSONArray("choices").getJSONObject(0)
            if (choice.optString("finish_reason") == "length") {
                throw TranslationException.ProviderFailure("Ответ обрезан сервером; выделите меньший фрагмент", kind = FailureKind.MALFORMED_RESPONSE)
            }
            val content = choice.getJSONObject("message").get("content")
            if (content !is String) throw TranslationException.ProviderFailure("Некорректный ответ AI", kind = FailureKind.MALFORMED_RESPONSE)
            val text = content.trim()
            if (text.isBlank()) throw TranslationException.ProviderFailure("AI вернул пустой ответ", kind = FailureKind.MALFORMED_RESPONSE)
            return TranslationResult(
                outputText = text,
                providerId = id,
                operation = request.operation,
            )
        } catch (e: TranslationException) {
            throw e
        } catch (e: Exception) {
            // JSON exceptions can embed raw server data. Never propagate them to diagnostics/UI.
            throw TranslationException.ProviderFailure("Некорректный ответ AI", kind = FailureKind.MALFORMED_RESPONSE)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
