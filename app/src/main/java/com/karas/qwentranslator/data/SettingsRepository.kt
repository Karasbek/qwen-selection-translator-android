package com.karas.qwentranslator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.karas.qwentranslator.core.*
import java.io.IOException
import kotlinx.coroutines.flow.*

private val Context.settingsDataStore by preferencesDataStore(name = "translator_settings")

class SettingsRepository(private val store: DataStore<Preferences>) : SettingsStore {
    constructor(context: Context) : this(context.settingsDataStore)

    val settings: Flow<AppSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(SettingsPreferences::decode)

    override suspend fun snapshot(): AppSettings = settings.first()

    override suspend fun save(settings: AppSettings) {
        val normalized = settings.normalized()
        require(normalized.validationError() == null) { "Некорректные настройки" }
        store.edit { SettingsPreferences.encode(normalized, it) }
    }
}

/** Stable enum names; old installs and unknown future values receive safe defaults. No secrets here. */
internal object SettingsPreferences {
    private val target = stringPreferencesKey("target_language")
    private val source = stringPreferencesKey("source_language")
    private val base = stringPreferencesKey("base_url")
    private val model = stringPreferencesKey("model")
    private val order = stringPreferencesKey("result_order")
    private val enabled = stringSetPreferencesKey("enabled_blocks")
    private val language = stringPreferencesKey("explanation_language")
    private val detail = stringPreferencesKey("explanation_detail")
    private val examples = booleanPreferencesKey("explanation_examples")
    private inline fun <reified T : Enum<T>> value(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

    fun decode(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val blocks = prefs[enabled]?.mapNotNull { value<ResultBlock>(it) }?.toSet()
        return AppSettings(
            targetLanguage = prefs[target] ?: defaults.targetLanguage,
            baseUrl = prefs[base] ?: defaults.baseUrl,
            model = prefs[model] ?: defaults.model,
            sourceLanguage = prefs[source] ?: defaults.sourceLanguage,
            resultOrder = prefs[order]?.split(',')?.mapNotNull { value<ResultBlock>(it) } ?: defaults.resultOrder,
            enabledBlocks = blocks?.takeIf { it.isNotEmpty() } ?: defaults.enabledBlocks,
            explanation = ExplanationOptions(
                language = value<ExplanationLanguage>(prefs[language]) ?: defaults.explanation.language,
                detail = value<ExplanationDetail>(prefs[detail]) ?: defaults.explanation.detail,
                examples = prefs[examples] ?: defaults.explanation.examples,
            ),
        ).normalized()
    }

    fun encode(settings: AppSettings, prefs: MutablePreferences) {
        prefs[target] = settings.targetLanguage
        prefs[source] = settings.sourceLanguage
        prefs[base] = settings.baseUrl
        prefs[model] = settings.model
        prefs[order] = settings.resultOrder.joinToString(",") { it.name }
        prefs[enabled] = settings.enabledBlocks.map { it.name }.toSet()
        prefs[language] = settings.explanation.language.name
        prefs[detail] = settings.explanation.detail.name
        prefs[examples] = settings.explanation.examples
    }
}
