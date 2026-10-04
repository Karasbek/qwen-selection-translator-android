package com.karas.qwentranslator.data

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "translator_settings")

class SettingsRepository(private val context: Context) : SettingsStore {
    private val defaults = AppSettings()
    private object Keys {
        val targetLanguage = stringPreferencesKey("target_language")
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { prefs ->
        AppSettings(
            targetLanguage = prefs[Keys.targetLanguage] ?: defaults.targetLanguage,
            baseUrl = prefs[Keys.baseUrl] ?: defaults.baseUrl,
            model = prefs[Keys.model] ?: defaults.model,
        )
    }

    override suspend fun snapshot(): AppSettings = settings.first()

    override suspend fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        require(normalized.validationError() == null) { "Некорректные настройки" }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.targetLanguage] = normalized.targetLanguage
            prefs[Keys.baseUrl] = normalized.baseUrl
            prefs[Keys.model] = normalized.model
        }
    }
}
