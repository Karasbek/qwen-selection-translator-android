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
    val ipaVariants: List<String> = emptyList(),
    val explainNotice: String? = null,
    val dirty: Boolean = false,
    val confirmRegeneration: Boolean = false,
    val failure: TranslationFailure? = null,
    val saveState: AnkiSaveState = AnkiSaveState.Ready,
    val destinationChanged: Boolean = false,
) {
    override fun toString() = "AnkiDraftUiState(visible=$visible, loading=$loading, dirty=$dirty)"
}

/** Editor state survives rotation; saving belongs to the application coordinator. */
class AnkiDraftViewModel(
    private val pronunciations: PronunciationRepository,
    private val gateway: AnkiGateway,
    private val settings: SettingsStore,
    private val saver: AnkiSaveCoordinator? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(AnkiDraftUiState())
    val state: StateFlow<AnkiDraftUiState> = _state.asStateFlow()
    init {
        if (saver != null) viewModelScope.launch {
            saver.states.collect {
                _state.update { editor -> editor.copy(saveState = editor.draft?.let { saver.state(it) } ?: AnkiSaveState.Ready) }
            }
        }
        if (saver != null) viewModelScope.launch {
            _state.map { it.draft?.operationId }.distinctUntilChanged().collect {
                _state.update { editor -> editor.copy(saveState = editor.draft?.let { saver.state(it) } ?: AnkiSaveState.Ready) }
            }
        }
    }
    private fun saveLocked() = _state.value.draft?.let { saver?.state(it)?.locked } == true

    fun verifySave() { state.value.draft?.let { saver?.verifyAgain(it) } }
    fun save() {
        val current = _state.value
        if (current.loading) return
        current.draft?.let { if (current.destinationChanged) saver?.requireDestinationConfirmation(it, current.metadata) else saver?.save(it) }
    }
    fun confirmSave() {
        val current = state.value
        val draft = current.draft ?: return
        val warning = current.saveState as? AnkiSaveState.Warning ?: return
        if (current.destinationChanged && warning.destination == null) {
            saver?.requireDestinationConfirmation(draft, current.metadata)
            return
        }
        if (warning.destination != null) {
            val target = warning.destination.copy(operationId = draft.operationId)
            saver?.dismissWarning(draft)
            _state.value = _state.value.copy(draft = target, metadata = requireNotNull(warning.metadata), destinationChanged = false)
            saver?.save(target)
        } else saver?.save(draft, confirmWarning = true)
    }
    fun dismissSaveWarning() { state.value.draft?.let { saver?.dismissWarning(it) } }
    private var request: AnkiPreparationRequest? = null
    private var pendingRequest: AnkiPreparationRequest? = null
    private var job: Job? = null
    private var generation = 0L

    fun open(context: AnkiPreparationRequest) {
        if (_state.value.visible || _state.value.loading) return
        val existing = _state.value.draft
        if (existing != null && existing.originalSelection == context.selection) {
            request = context // Fresh popup results become context for the next explicit regeneration.
            _state.value = _state.value.copy(visible = true)
            if (!saveLocked()) refreshDestination()
            return
        }
        if (existing != null && _state.value.dirty) {
            pendingRequest = context
            _state.value = _state.value.copy(visible = true, confirmRegeneration = true)
            return
        }
        startNew(context)
    }

    private fun startNew(context: AnkiPreparationRequest) {
        request = context
        val draft = AnkiCardDraft.build(context.selection, CardContent(context.selection.trim(), explanation = context.explanation.orEmpty()), "", null, null, AnkiMetadata())
        _state.value = AnkiDraftUiState(visible = true, draft = draft)
        prepare(context)
    }

    fun regenerate(current: AnkiPreparationRequest? = null) {
        if (current != null && !saveLocked() && !_state.value.loading) request = current
        if (_state.value.loading || saveLocked()) return
        if (_state.value.dirty) _state.value = _state.value.copy(confirmRegeneration = true)
        else runRegeneration()
    }
    fun confirmRegeneration() {
        if (saveLocked()) return
        _state.value = _state.value.copy(confirmRegeneration = false)
        val pending = pendingRequest
        pendingRequest = null
        if (pending != null) startNew(pending) else runRegeneration()
    }
    fun dismissConfirmation() { pendingRequest = null; _state.value = _state.value.copy(confirmRegeneration = false) }
    private fun runRegeneration() {
        val context = request ?: return
        val word = _state.value.draft?.content?.word ?: context.selection
        val changed = word.trim() != context.selection.trim()
        prepare(context.copy(selection = word,
            sourceLanguage = if (changed) "auto" else context.sourceLanguage,
            explanation = if (changed) null else context.explanation,
            explainState = if (changed) ExplainState.MISSING else context.explainState))
    }

    fun edit(content: CardContent, ipa: String) {
        val current = _state.value
        if (current.loading || saveLocked()) return
        val previous = current.draft ?: return
        val wordChanged = content.word != previous.content.word
        val nextIpa = if (wordChanged) "" else ipa
        _state.value = current.copy(
            draft = AnkiCardDraft.build(previous.originalSelection, content, nextIpa, previous.deckId, previous.modelId, current.metadata),
            dirty = true, failure = null, saveState = AnkiSaveState.Ready, ipaVariants = if (wordChanged) emptyList() else current.ipaVariants, ipaSource = if (wordChanged) null else if (ipa in current.ipaVariants) "ipa-dict · English (US), офлайн" else if (ipa != previous.ipa) null else current.ipaSource,
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
                    destinationChanged = _state.value.destinationChanged || draft.deckId != configuration.ankiDeckId || draft.modelId != configuration.ankiModelId,
                    draft = AnkiCardDraft.build(draft.originalSelection, draft.content, draft.ipa,
                        configuration.ankiDeckId, configuration.ankiModelId, metadata).copy(operationId = draft.operationId))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) _state.value = _state.value.copy(loading = false, failure = TranslationFailure.from(e))
            }
        }
    }

    private fun prepare(context: AnkiPreparationRequest) {
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
                // Full Explain is data; never split, paraphrase, classify or call an LLM.
                val content = CardContent(context.selection.trim(), explanation = context.explanation.orEmpty())
                val notice = when (context.explainState) {
                    ExplainState.READY -> null
                    ExplainState.LOADING -> "Explain ещё загружается. Дождитесь его и нажмите «Подготовить из текущего Explain» или введите текст вручную."
                    ExplainState.DISABLED -> "Explain выключен. Включите его в настройках или введите текст карточки вручную."
                    ExplainState.FAILED -> "Explain не получен. Вернитесь к переводу и повторите Explain или введите текст вручную."
                    ExplainState.MISSING -> "Готового Explain для этого слова нет. Откройте объяснение нужного слова или введите текст вручную."
                }
                _state.value = _state.value.copy(dirty = false, ipaSource = null, ipaVariants = emptyList(), explainNotice = notice,
                    draft = AnkiCardDraft.build(previous.originalSelection, content, "",
                        configuration.ankiDeckId, configuration.ankiModelId, metadata))
                val prepared = requireNotNull(_state.value.draft)
                val ipa = try {
                    pronunciations.lookup(content.word, if (context.sourceLanguage == "auto") configuration.sourceLanguage else context.sourceLanguage)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { Pronunciations() }
                ensureActive()
                if (generation != currentGeneration) return@launch
                val automatic = ipa.variants.singleOrNull().orEmpty()
                _state.value = _state.value.copy(loading = false, dirty = false,
                    ipaSource = ipa.source.takeIf { automatic.isNotBlank() }, ipaVariants = ipa.variants,
                    draft = AnkiCardDraft.build(previous.originalSelection, content, automatic, configuration.ankiDeckId, configuration.ankiModelId, metadata).copy(operationId = prepared.operationId))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == currentGeneration) _state.value = _state.value.copy(loading = false, failure = TranslationFailure.from(e))
            }
        }
    }

    class Factory(private val pronunciations: PronunciationRepository, private val gateway: AnkiGateway, private val settings: SettingsStore, private val saver: AnkiSaveCoordinator? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnkiDraftViewModel(pronunciations, gateway, settings, saver) as T
    }
}
