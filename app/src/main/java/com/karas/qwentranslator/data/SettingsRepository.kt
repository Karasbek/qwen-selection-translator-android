package com.karas.qwentranslator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "translator_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val targetLanguage = stringPreferencesKey("target_language")
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            targetLanguage = prefs[Keys.targetLanguage] ?: "ru",
            baseUrl = prefs[Keys.baseUrl] ?: "https://api.groq.com/openai/v1",
            model = prefs[Keys.model] ?: "qwen/qwen3.8-27b",
        )
    }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.targetLanguage] = settings.targetLanguage.trim().ifBlank { "ru" }
            prefs[Keys.baseUrl] = settings.baseUrl.trim().trimEnd('/').ifBlank {
                "https://api.groq.com/openai/v1"
            }
            prefs[Keys.model] = settings.model.trim().ifBlank { "qwen/qwen3.8-27b" }
        }
    }
}
