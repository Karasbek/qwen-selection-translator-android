package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.PromptBuilder
import com.karas.qwentranslator.core.TranslationException
import com.karas.qwentranslator.core.TranslationRequest
import com.karas.qwentranslator.core.TranslationResult
import com.karas.qwentranslator.core.TranslatorProvider
import com.karas.qwentranslator.data.SecretStore
import com.karas.qwentranslator.data.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
) : TranslatorProvider {
    override val id: String = "Qwen/Groq"

    override fun supports(operation: Operation): Boolean = true

    override suspend fun execute(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = secretStore.getApiKey()
            ?.takeIf { it.isNotBlank() }
            ?: throw TranslationException.Configuration(
                "Groq API key не задан; перевод попробует fallback, а Explain требует ключ",
            )
        val settings = settingsRepository.snapshot()
        val endpoint = settings.baseUrl.trimEnd('/') + "/chat/completions"

        val body = JSONObject()
            .put("model", settings.model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", PromptBuilder.build(request)),
                ),
            )
            .put("reasoning_effort", "none")
            .put("reasoning_format", "hidden")
            .put("temperature", if (request.operation == Operation.TRANSLATE) 0.2 else 0.4)
            .put("max_completion_tokens", if (request.operation == Operation.TRANSLATE) 1024 else 768)
            .put("stream", false)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw TranslationException.ProviderFailure(
                    "Groq HTTP ${response.code}${if (message.isBlank()) "" else ": $message"}",
                )
            }

            val text = runCatching {
                JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }.getOrElse {
                throw TranslationException.ProviderFailure("Некорректный ответ Groq", it)
            }

            if (text.isBlank()) {
                throw TranslationException.ProviderFailure("Groq вернул пустой ответ")
            }

            TranslationResult(
                outputText = text,
                providerId = id,
                operation = request.operation,
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
