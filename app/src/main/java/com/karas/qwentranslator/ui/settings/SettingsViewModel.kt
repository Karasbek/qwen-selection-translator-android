package com.karas.qwentranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val saved: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
) {
    override fun toString(): String = "SettingsUiState(loading=$loading, saving=$saving, saved=$saved, apiKey=<redacted>)"
}

class SettingsViewModel(
    private val settingsRepository: SettingsStore,
    private val secretStore: ApiKeyStore,
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
                        apiKey = secretStore.getApiKey().orEmpty(),
                        loading = false,
                    )
                }
                _state.value = loaded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Не удалось прочитать настройки. Попробуйте сохранить их заново.")
            }
        }
    }

    fun updateTarget(value: String) = mutate { copy(targetLanguage = value, saved = false, error = null) }
    fun updateBaseUrl(value: String) = mutate { copy(baseUrl = value, saved = false, error = null) }
    fun updateModel(value: String) = mutate { copy(model = value, saved = false, error = null) }
    fun updateApiKey(value: String) = mutate { copy(apiKey = value, saved = false, error = null) }

    fun save() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.saving) return
        val settings = AppSettings(snapshot.targetLanguage, snapshot.baseUrl, snapshot.model).normalized()
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

    class Factory(private val settingsRepository: SettingsStore, private val secretStore: ApiKeyStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(settingsRepository, secretStore) as T
    }
}
