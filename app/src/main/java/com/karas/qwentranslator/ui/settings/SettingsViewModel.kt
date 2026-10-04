package com.karas.qwentranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.qwentranslator.data.AppSettings
import com.karas.qwentranslator.data.SecretStore
import com.karas.qwentranslator.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class SettingsUiState(
    val targetLanguage: String = "ru",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val model: String = "qwen/qwen3.8-27b",
    val apiKey: String = "",
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.snapshot()
            _state.value = SettingsUiState(
                targetLanguage = settings.targetLanguage,
                baseUrl = settings.baseUrl,
                model = settings.model,
                apiKey = secretStore.getApiKey().orEmpty(),
            )
        }
    }

    fun updateTarget(value: String) = mutate { copy(targetLanguage = value, saved = false) }
    fun updateBaseUrl(value: String) = mutate { copy(baseUrl = value, saved = false) }
    fun updateModel(value: String) = mutate { copy(model = value, saved = false) }
    fun updateApiKey(value: String) = mutate { copy(apiKey = value, saved = false) }

    fun save() {
        val snapshot = _state.value
        viewModelScope.launch {
            settingsRepository.save(
                AppSettings(
                    targetLanguage = snapshot.targetLanguage,
                    baseUrl = snapshot.baseUrl,
                    model = snapshot.model,
                ),
            )
            secretStore.setApiKey(snapshot.apiKey)
            _state.value = _state.value.copy(saved = true)
        }
    }

    private fun mutate(block: SettingsUiState.() -> SettingsUiState) {
        _state.value = _state.value.block()
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val secretStore: SecretStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, secretStore) as T
        }
    }
}
