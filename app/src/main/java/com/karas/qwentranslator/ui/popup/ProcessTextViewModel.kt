package com.karas.qwentranslator.ui.popup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import com.karas.qwentranslator.data.SettingsStore
import com.karas.qwentranslator.util.Invocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface ResultUiState {
    data object Disabled : ResultUiState
    data object Loading : ResultUiState
    data class Success(val result: TranslationResult) : ResultUiState
    data class Error(val failure: TranslationFailure) : ResultUiState
}

data class PopupUiState(
    val invocation: Invocation,
    val resultOrder: List<ResultBlock> = ResultBlock.defaultOrder,
    val blocks: Map<ResultBlock, ResultUiState> = ResultBlock.defaultOrder.associateWith { ResultUiState.Loading },
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "ru",
) {
    /** Re-check current output and invocation when Android delivers a click/result. */
    fun replacementText(output: String): String? {
        val success = blocks[ResultBlock.TRANSLATION] as? ResultUiState.Success ?: return null
        if (success.result.operation != Operation.TRANSLATE || success.result.outputText != output) return null
        return invocation.replacementText(output)
    }
}

class ProcessTextViewModel(
    initialInvocation: Invocation,
    private val engine: TranslatorEngine,
    private val settingsRepository: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(PopupUiState(initialInvocation))
    val state: StateFlow<PopupUiState> = _state.asStateFlow()
    private var configurationJob: Job? = null
    private val requestJobs = mutableMapOf<ResultBlock, Job>()
    private val requestIds = mutableMapOf<ResultBlock, Long>()
    private var generation = 0L
    private var settings: AppSettings? = null

    init { acceptInvocation(initialInvocation) }

    fun acceptInvocation(input: Invocation) {
        configurationJob?.cancel()
        requestJobs.values.forEach { it.cancel() }
        requestJobs.clear()
        settings = null
        val currentGeneration = ++generation
        _state.value = PopupUiState(input)
        configurationJob = viewModelScope.launch {
            try {
                val loaded = settingsRepository.snapshot().normalized()
                ensureActive()
                if (generation == currentGeneration) configure(loaded)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) _state.value = _state.value.copy(
                    blocks = ResultBlock.defaultOrder.associateWith {
                        ResultUiState.Error(TranslationFailure(FailureKind.CONFIGURATION))
                    },
                )
            }
        }
    }

    private fun configure(loaded: AppSettings) {
        settings = loaded
        val enabled = loaded.enabledBlocks.takeIf { it.isNotEmpty() } ?: ResultBlock.defaultOrder.toSet()
        _state.value = _state.value.copy(
            sourceLanguage = loaded.sourceLanguage, targetLanguage = loaded.targetLanguage,
            resultOrder = loaded.resultOrder,
            blocks = loaded.resultOrder.associateWith {
                if (it in enabled) ResultUiState.Loading else ResultUiState.Disabled
            },
        )
        // Sibling jobs start without awaiting one another. A block failure cannot cancel another.
        loaded.resultOrder.filter { it in enabled }.forEach { run(it, loaded) }
    }

    fun retry(block: ResultBlock) {
        if (_state.value.blocks[block] !is ResultUiState.Error) return
        val loaded = settings
        if (loaded != null) {
            run(block, loaded)
            return
        }
        // If initial preferences could not be read, recovering one block must still be local.
        val currentGeneration = generation
        setBlock(block, ResultUiState.Loading)
        requestJobs[block] = viewModelScope.launch {
            try {
                val recovered = settingsRepository.snapshot().normalized()
                ensureActive()
                if (generation == currentGeneration) {
                    settings = recovered
                    _state.value = _state.value.copy(resultOrder = recovered.resultOrder,
                        sourceLanguage = recovered.sourceLanguage, targetLanguage = recovered.targetLanguage,
                        blocks = _state.value.blocks.mapValues { (key, value) ->
                            if (key in recovered.enabledBlocks) value else ResultUiState.Disabled
                        })
                    if (block in recovered.enabledBlocks) {
                        requestJobs.remove(block)
                        run(block, recovered)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) setBlock(block, ResultUiState.Error(TranslationFailure(FailureKind.CONFIGURATION)))
            }
        }
    }

    /** Called only on return from Settings, never by recomposition or normal resume. */
    fun refreshSettings() {
        configurationJob?.cancel()
        val currentGeneration = generation
        configurationJob = viewModelScope.launch {
            try {
                val loaded = settingsRepository.snapshot().normalized()
                ensureActive()
                if (currentGeneration != generation) return@launch
                val previous = settings
                when {
                    loaded == previous -> {
                        // A key-only save does not change nonsecret preferences. Recover errors on return.
                        loaded.resultOrder.filter { _state.value.blocks[it] is ResultUiState.Error }.forEach { run(it, loaded) }
                    }
                    previous != null && previous.copy(resultOrder = loaded.resultOrder, enabledBlocks = loaded.enabledBlocks,
                        ankiDeckId = loaded.ankiDeckId, ankiModelId = loaded.ankiModelId) == loaded -> {
                        // Reordering/turning a block off must not spend another request on retained results.
                        val oldBlocks = _state.value.blocks
                        loaded.resultOrder.filter { it !in loaded.enabledBlocks }.forEach { block ->
                            requestJobs.remove(block)?.cancel()
                            requestIds[block] = (requestIds[block] ?: 0L) + 1
                        }
                        settings = loaded
                        _state.value = _state.value.copy(resultOrder = loaded.resultOrder,
                            blocks = loaded.resultOrder.associateWith { block ->
                                if (block !in loaded.enabledBlocks) ResultUiState.Disabled
                                else oldBlocks[block] ?: ResultUiState.Disabled
                            })
                        loaded.resultOrder.filter { it in loaded.enabledBlocks && _state.value.blocks[it] == ResultUiState.Disabled }
                            .forEach { run(it, loaded) }
                    }
                    else -> {
                        requestJobs.values.forEach { it.cancel() }
                        ++generation
                        configure(loaded)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { /* Keep already available results; Settings presents its own save error. */ }
        }
    }

    private fun run(block: ResultBlock, loaded: AppSettings) {
        requestJobs[block]?.cancel()
        val currentGeneration = generation
        val requestId = (requestIds[block] ?: 0L) + 1
        requestIds[block] = requestId
        val input = _state.value.invocation
        setBlock(block, ResultUiState.Loading)
        requestJobs[block] = viewModelScope.launch {
            val next = try {
                val result = engine.execute(TranslationRequest(
                    text = input.text, operation = block.operation,
                    sourceLanguage = loaded.sourceLanguage, targetLanguage = loaded.targetLanguage,
                    explanation = loaded.explanation,
                ))
                ensureActive()
                ResultUiState.Success(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ResultUiState.Error(TranslationFailure.from(e)) }
            if (generation == currentGeneration && requestIds[block] == requestId) setBlock(block, next)
        }
    }

    private fun setBlock(block: ResultBlock, value: ResultUiState) {
        _state.value = _state.value.copy(blocks = _state.value.blocks + (block to value))
    }

    class Factory(
        private val invocation: Invocation, private val engine: TranslatorEngine,
        private val settingsRepository: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProcessTextViewModel(invocation, engine, settingsRepository) as T
    }
}
