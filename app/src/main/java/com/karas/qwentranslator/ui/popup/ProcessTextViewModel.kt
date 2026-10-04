package com.karas.qwentranslator.ui.popup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.core.TranslationException
import com.karas.qwentranslator.core.TranslationRequest
import com.karas.qwentranslator.core.TranslatorEngine
import com.karas.qwentranslator.data.SettingsStore
import com.karas.qwentranslator.util.Invocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PopupUiState {
    val operation: Operation
    data class Loading(override val operation: Operation) : PopupUiState
    data class Success(
        override val operation: Operation,
        val text: String,
        val provider: String,
        val sourceLanguage: String,
        val targetLanguage: String,
    ) : PopupUiState
    data class Error(override val operation: Operation, val message: String) : PopupUiState
}

class ProcessTextViewModel(
    initialInvocation: Invocation,
    private val engine: TranslatorEngine,
    private val settingsRepository: SettingsStore,
) : ViewModel() {
    private val _invocation = MutableStateFlow(initialInvocation)
    val invocation = _invocation.asStateFlow()
    private val _state = MutableStateFlow<PopupUiState>(PopupUiState.Loading(Operation.TRANSLATE))
    val state: StateFlow<PopupUiState> = _state.asStateFlow()
    private var requestJob: Job? = null
    private var generation = 0L

    init { run(Operation.TRANSLATE) }

    fun acceptInvocation(input: Invocation) {
        _invocation.value = input
        run(Operation.TRANSLATE)
    }

    fun selectOperation(operation: Operation) {
        if (_state.value.operation == operation && _state.value !is PopupUiState.Error) return
        run(operation)
    }

    fun retry() = run(_state.value.operation)

    private fun run(operation: Operation) {
        requestJob?.cancel()
        val currentGeneration = ++generation
        val input = _invocation.value
        _state.value = PopupUiState.Loading(operation)
        requestJob = viewModelScope.launch {
            val next = try {
                val settings = settingsRepository.snapshot()
                val result = engine.execute(TranslationRequest(
                    text = input.text,
                    operation = operation,
                    targetLanguage = settings.targetLanguage,
                ))
                ensureActive()
                PopupUiState.Success(
                    operation = operation,
                    text = result.outputText,
                    provider = result.providerId,
                    sourceLanguage = result.detectedSourceLanguage ?: "авто",
                    targetLanguage = if (operation == Operation.EXPLAIN) "язык исходного текста" else settings.targetLanguage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PopupUiState.Error(operation, if (e is TranslationException) e.message.orEmpty()
                    else "Не удалось загрузить настройки или обработать текст")
            }
            if (generation == currentGeneration) _state.value = next
        }
    }

    class Factory(
        private val invocation: Invocation,
        private val engine: TranslatorEngine,
        private val settingsRepository: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProcessTextViewModel(invocation, engine, settingsRepository) as T
    }
}
