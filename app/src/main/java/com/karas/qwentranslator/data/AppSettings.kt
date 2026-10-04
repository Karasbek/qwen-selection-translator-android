package com.karas.qwentranslator.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AppSettings(
    val targetLanguage: String = "ru",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "qwen/qwen3.8-27b",
) {
    fun normalized(): AppSettings {
        val defaults = AppSettings()
        return copy(
            targetLanguage = targetLanguage.trim().ifBlank { defaults.targetLanguage },
            baseUrl = baseUrl.trim().trimEnd('/').ifBlank { defaults.baseUrl },
            model = model.trim().ifBlank { defaults.model },
        )
    }

    fun validationError(): String? = when {
        !Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*").matches(targetLanguage) ->
            "Введите код языка, например ru, en или zh-CN"
        model.isBlank() || model.length > 200 || model.any { it.isISOControl() } ->
            "Введите корректное имя модели"
        endpoint() == null -> "Base URL должен быть HTTPS без логина, пароля, query и fragment"
        else -> null
    }

    fun endpoint(): HttpUrl? {
        val url = baseUrl.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null) return null
        return url.newBuilder().addPathSegment("chat").addPathSegment("completions").build()
    }
}

interface SettingsStore {
    suspend fun snapshot(): AppSettings
    suspend fun save(settings: AppSettings)
}

interface ApiKeyStore {
    fun getApiKey(): String?
    fun setApiKey(value: String)
}
