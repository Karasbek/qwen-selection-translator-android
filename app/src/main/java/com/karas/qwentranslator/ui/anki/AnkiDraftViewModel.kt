package com.karas.qwentranslator.ui.anki

import androidx.lifecycle.*
import com.karas.qwentranslator.anki.*
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AnkiDraftUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val draft: AnkiCardDraft? = null,
    val metadata: AnkiMetadata = AnkiMetadata(),
    val ipaSource: String? = null,
    val dirty: Boolean = false,
    val confirmRegeneration: Boolean = false,
    val failure: TranslationFailure? = null,
) {
    override fun toString() = "AnkiDraftUiState(visible=$visible, loading=$loading, dirty=$dirty)"
}

/** Activity-scoped state survives rotation. No draft is persisted and no collection is written. */
class AnkiDraftViewModel(
    private val generator: CardGenerator,
    private val ipaLookup: IpaLookup,
    private val gateway: AnkiGateway,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AnkiDraftUiState())
    val state: StateFlow<AnkiDraftUiState> = _state.asStateFlow()
    private var request: CardGenerationRequest? = null
    private var pendingRequest: CardGenerationRequest? = null
    private var job: Job? = null
    private var generation = 0L

    fun open(context: CardGenerationRequest) {
        if (_state.value.visible || _state.value.loading) return
        val existing = _state.value.draft
        if (existing != null && existing.originalSelection == context.selection) {
            _state.value = _state.value.copy(visible = true)
            refreshDestination()
            return
        }
        if (existing != null && _state.value.dirty) {
            pendingRequest = context
            _state.value = _state.value.copy(visible = true, confirmRegeneration = true)
            return
        }
        startNew(context)
    }

    private fun startNew(context: CardGenerationRequest) {
        request = context
        val draft = AnkiCardDraft.build(context.selection, CardContent(context.selection.trim()), "", null, null, AnkiMetadata())
        _state.value = AnkiDraftUiState(visible = true, draft = draft)
        generate(context)
    }

    fun regenerate() {
        if (_state.value.loading) return
        if (_state.value.dirty) _state.value = _state.value.copy(confirmRegeneration = true)
        else runRegeneration()
    }
    fun confirmRegeneration() {
        _state.value = _state.value.copy(confirmRegeneration = false)
        val pending = pendingRequest
        pendingRequest = null
        if (pending != null) startNew(pending) else runRegeneration()
    }
    fun dismissConfirmation() { pendingRequest = null; _state.value = _state.value.copy(confirmRegeneration = false) }
    private fun runRegeneration() {
        val context = request ?: return
        generate(context.copy(selection = _state.value.draft?.content?.word ?: context.selection))
    }

    fun edit(content: CardContent, ipa: String) {
        val current = _state.value
        if (current.loading) return
        val previous = current.draft ?: return
        val wordChanged = content.word != previous.content.word
        val nextIpa = if (wordChanged) "" else ipa
        _state.value = current.copy(
            draft = AnkiCardDraft.build(previous.originalSelection, content, nextIpa, previous.deckId, previous.modelId, current.metadata),
            dirty = true, failure = null, ipaSource = if (wordChanged || ipa != previous.ipa) null else current.ipaSource,
        )
    }

    /** Closing the preview cancels work but keeps edits for reopening within the same popup. */
    fun close() {
        ++generation
        job?.cancel()
        job = null
        pendingRequest = null
        _state.value = _state.value.copy(visible = false, loading = false, confirmRegeneration = false)
    }
    fun cancelGeneration() {
        ++generation
        job?.cancel()
        job = null
        _state.value = _state.value.copy(loading = false)
    }

    private fun refreshDestination() {
        val currentGeneration = ++generation
        _state.value = _state.value.copy(loading = true)
        job = viewModelScope.launch {
            try {
                val configuration = settings.snapshot()
                val metadata = withContext(Dispatchers.IO) { AnkiConnection(gateway).load() }
                ensureActive()
                if (generation != currentGeneration) return@launch
                val draft = requireNotNull(_state.value.draft)
                _state.value = _state.value.copy(loading = false, metadata = metadata,
                    draft = AnkiCardDraft.build(draft.originalSelection, draft.content, draft.ipa,
                        configuration.ankiDeckId, configuration.ankiModelId, metadata))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) _state.value = _state.value.copy(loading = false, failure = TranslationFailure.from(e))
            }
        }
    }

    private fun generate(context: CardGenerationRequest) {
        if (_state.value.loading) return
        val currentGeneration = ++generation
        _state.value = _state.value.copy(loading = true, failure = null)
        job = viewModelScope.launch {
            try {
                val configuration = settings.snapshot()
                val metadata = withContext(Dispatchers.IO) { AnkiConnection(gateway).load() }
                ensureActive()
                if (generation != currentGeneration) return@launch
                val previous = requireNotNull(_state.value.draft)
                _state.value = _state.value.copy(metadata = metadata, draft = AnkiCardDraft.build(
                    previous.originalSelection, previous.content, previous.ipa, configuration.ankiDeckId, configuration.ankiModelId, metadata))
                val data = generator.generate(context.copy(
                    sourceLanguage = if (context.sourceLanguage == "auto") configuration.sourceLanguage else context.sourceLanguage,
                    targetLanguage = configuration.targetLanguage, explanationLanguage = configuration.explanation.language))
                ensureActive()
                // Never accept a provider's implicit lemma as the selected form.
                val content = data.copy(word = context.selection.trim())
                val ipa = try {
                    ipaLookup.lookup(content.word, if (context.sourceLanguage == "auto") configuration.sourceLanguage else context.sourceLanguage)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                ensureActive()
                if (generation != currentGeneration) return@launch
                _state.value = _state.value.copy(loading = false, dirty = false, ipaSource = ipa?.source,
                    draft = AnkiCardDraft.build(previous.originalSelection, content, ipa?.text.orEmpty(), configuration.ankiDeckId, configuration.ankiModelId, metadata))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) _state.value = _state.value.copy(loading = false, failure = TranslationFailure.from(e))
            }
        }
    }

    class Factory(private val generator: CardGenerator, private val ipa: IpaLookup, private val gateway: AnkiGateway, private val settings: SettingsStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnkiDraftViewModel(generator, ipa, gateway, settings) as T
    }
}
