package com.karas.qwentranslator.ui.settings

import com.karas.qwentranslator.anki.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import com.karas.qwentranslator.data.ApiKeyStore
import com.karas.qwentranslator.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val targetLanguage: String = AppSettings().targetLanguage,
    val baseUrl: String = AppSettings().baseUrl,
    val model: String = AppSettings().model,
    val apiKey: String = "",
    val sourceLanguage: String = AppSettings().sourceLanguage,
    val resultOrder: List<ResultBlock> = ResultBlock.defaultOrder,
    val enabledBlocks: Set<ResultBlock> = ResultBlock.defaultOrder.toSet(),
    val explanation: ExplanationOptions = ExplanationOptions(),
    val ankiDeckId: Long? = null,
    val ankiModelId: Long? = null,
    val anki: AnkiMetadata = AnkiMetadata(),
    val saved: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    fun settings() = AppSettings(targetLanguage, baseUrl, model, sourceLanguage, resultOrder, enabledBlocks, explanation, ankiDeckId, ankiModelId)
    override fun toString(): String = "SettingsUiState(loading=$loading, saving=$saving, saved=$saved, apiKey=<redacted>)"
}

class SettingsViewModel(
    private val settingsRepository: SettingsStore,
    private val secretStore: ApiKeyStore,
    private val ankiGateway: AnkiGateway? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val settings = settingsRepository.snapshot()
                    SettingsUiState(
                        targetLanguage = settings.targetLanguage,
                        baseUrl = settings.baseUrl,
                        model = settings.model,
                        sourceLanguage = settings.sourceLanguage,
                        resultOrder = settings.resultOrder,
                        enabledBlocks = settings.enabledBlocks,
                        explanation = settings.explanation,
                        ankiDeckId = settings.ankiDeckId,
                        ankiModelId = settings.ankiModelId,
                        apiKey = secretStore.getApiKey().orEmpty(),
                        loading = false,
                    )
                }
                _state.value = loaded
                refreshAnki()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Не удалось прочитать настройки. Попробуйте сохранить их заново.")
            }
        }
    }

    fun refreshAnki() {
        val gateway = ankiGateway ?: return
        if (_state.value.loading || _state.value.anki.loading) return
        _state.value = _state.value.copy(anki = _state.value.anki.copy(loading = true))
        viewModelScope.launch {
            val metadata = withContext(Dispatchers.IO) { AnkiConnection(gateway).load() }
            _state.value = _state.value.copy(anki = metadata)
        }
    }

    fun selectAnkiDeck(id: Long?) = mutate {
        if (id == null || anki.decks.any { it.id == id }) copy(ankiDeckId = id, saved = false) else this
    }
    fun selectAnkiModel(id: Long?) = mutate {
        if (id == null || anki.models.any { it.id == id }) copy(ankiModelId = id, saved = false) else this
    }

    fun updateTarget(value: String) = mutate { copy(targetLanguage = value, saved = false, error = null) }
    fun updateBaseUrl(value: String) = mutate { copy(baseUrl = value, saved = false, error = null) }
    fun updateModel(value: String) = mutate { copy(model = value, saved = false, error = null) }
    fun updateApiKey(value: String) = mutate { copy(apiKey = value, saved = false, error = null) }

    fun updateSource(value: String) = mutate { copy(sourceLanguage = value, saved = false, error = null) }
    fun updateExplanation(value: ExplanationOptions) = mutate { copy(explanation = value, saved = false, error = null) }
    fun setBlockEnabled(block: ResultBlock, enabled: Boolean) = mutate {
        if (!enabled && enabledBlocks == setOf(block)) copy(error = "Оставьте хотя бы один результат включённым")
        else copy(enabledBlocks = if (enabled) enabledBlocks + block else enabledBlocks - block, saved = false, error = null)
    }
    fun moveBlock(block: ResultBlock, direction: Int) = mutate {
        val index = resultOrder.indexOf(block)
        val destination = index + direction.coerceIn(-1, 1)
        if (index < 0 || destination !in resultOrder.indices) this else {
            val order = resultOrder.toMutableList()
            order[index] = order[destination]
            order[destination] = block
            copy(resultOrder = order, saved = false, error = null)
        }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.saving) return
        val settings = snapshot.settings().normalized()
        val error = settings.validationError() ?: if (snapshot.apiKey.trim().any { it.isISOControl() } || snapshot.apiKey.length > 4096)
            "Некорректный API key" else null
        if (error != null) {
            _state.value = snapshot.copy(error = error)
            return
        }
        _state.value = snapshot.copy(saving = true, saved = false, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    secretStore.setApiKey(snapshot.apiKey)
                    settingsRepository.save(settings)
                }
                _state.value = _state.value.copy(
                    targetLanguage = settings.targetLanguage,
                    baseUrl = settings.baseUrl,
                    model = settings.model,
                    sourceLanguage = settings.sourceLanguage,
                    resultOrder = settings.resultOrder,
                    saving = false, saved = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = "Не удалось сохранить настройки или ключ. Повторите попытку.")
            }
        }
    }

    private fun mutate(block: SettingsUiState.() -> SettingsUiState) {
        if (!_state.value.loading && !_state.value.saving) _state.value = _state.value.block()
    }

    class Factory(private val settingsRepository: SettingsStore, private val secretStore: ApiKeyStore, private val ankiGateway: AnkiGateway? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(settingsRepository, secretStore, ankiGateway) as T
    }
}
