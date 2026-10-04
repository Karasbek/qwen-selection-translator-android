package com.karas.qwentranslator.ui.popup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.TranslationRequest
import com.karas.qwentranslator.core.TranslatorEngine
import com.karas.qwentranslator.data.SettingsRepository
import com.karas.qwentranslator.util.Invocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PopupUiState {
    data class Loading(val operation: Operation) : PopupUiState
    data class Success(
        val operation: Operation,
        val text: String,
        val provider: String,
    ) : PopupUiState
    data class Error(
        val operation: Operation,
        val message: String,
    ) : PopupUiState
}

class ProcessTextViewModel(
    private val invocation: Invocation,
    private val engine: TranslatorEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PopupUiState>(PopupUiState.Loading(Operation.TRANSLATE))
    val state: StateFlow<PopupUiState> = _state.asStateFlow()

    init {
        run(Operation.TRANSLATE)
    }

    fun selectOperation(operation: Operation) {
        val current = _state.value
        if ((current as? PopupUiState.Success)?.operation == operation) return
        run(operation)
    }

    fun retry() {
        val operation = when (val current = _state.value) {
            is PopupUiState.Loading -> current.operation
            is PopupUiState.Success -> current.operation
            is PopupUiState.Error -> current.operation
        }
        run(operation)
    }

    private fun run(operation: Operation) {
        _state.value = PopupUiState.Loading(operation)
        viewModelScope.launch {
            val settings = settingsRepository.snapshot()
            _state.value = runCatching {
                engine.execute(
                    TranslationRequest(
                        text = invocation.text,
                        operation = operation,
                        targetLanguage = settings.targetLanguage,
                    ),
                )
            }.fold(
                onSuccess = { result ->
                    PopupUiState.Success(
                        operation = operation,
                        text = result.outputText,
                        provider = result.providerId,
                    )
                },
                onFailure = { error ->
                    PopupUiState.Error(
                        operation = operation,
                        message = error.message ?: "Неизвестная ошибка",
                    )
                },
            )
        }
    }

    class Factory(
        private val invocation: Invocation,
        private val engine: TranslatorEngine,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProcessTextViewModel(invocation, engine, settingsRepository) as T
        }
    }
}
