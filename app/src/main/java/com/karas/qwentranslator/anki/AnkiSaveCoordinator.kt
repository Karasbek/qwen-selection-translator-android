package com.karas.qwentranslator.anki

import com.karas.qwentranslator.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.Locale

/** Only operation IDs, hashes, status and returned note ID are persisted; never note text. */
data class SaveReceipt(val key: String, val fingerprint: String, val word: String, val noteId: Long?, val confirmed: Boolean)
interface SaveReceiptStore {
    fun all(): List<SaveReceipt>
    /** Must durably commit before returning, or throw. */
    fun put(receipt: SaveReceipt)
}
class MemorySaveReceiptStore : SaveReceiptStore {
    private val records = mutableMapOf<String, SaveReceipt>()
    @Synchronized override fun all() = records.values.toList()
    @Synchronized override fun put(receipt: SaveReceipt) { records[receipt.key] = receipt }
}

sealed interface AnkiSaveState {
    data object Ready : AnkiSaveState
    data object Checking : AnkiSaveState
    data object Saving : AnkiSaveState
    data class Saved(val noteId: Long) : AnkiSaveState
    data class FailedBeforeWrite(val message: String) : AnkiSaveState
    data class OutcomeUnknown(val noteId: Long? = null, val detail: String? = null) : AnkiSaveState
    data class Warning(val message: String, val destination: AnkiCardDraft? = null, val metadata: AnkiMetadata? = null) : AnkiSaveState
}
val AnkiSaveState.busy get() = this == AnkiSaveState.Checking || this == AnkiSaveState.Saving
val AnkiSaveState.locked get() = busy || this is AnkiSaveState.Saved || this is AnkiSaveState.OutcomeUnknown

/** Application-owned operation: UI dismissal, ViewModel destruction and rotation cannot cancel insertion.
 * Durable intent is recorded BEFORE crossing the provider boundary. Interrupted intents never retry.
 */
class AnkiSaveCoordinator(
    private val gateway: AnkiGateway,
    private val settings: SettingsStore,
    private val receipts: SaveReceiptStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _states = MutableStateFlow<Map<String, AnkiSaveState>>(emptyMap())
    val states = _states.asStateFlow()
    private var active = false
    fun state(draft: AnkiCardDraft): AnkiSaveState = _states.value[key(draft)] ?: recovered(draft)
    private fun recovered(draft: AnkiCardDraft): AnkiSaveState = try {
        val records = receipts.all()
        records.firstOrNull { it.key == key(draft) }?.let {
            if (it.confirmed && it.noteId != null) AnkiSaveState.Saved(it.noteId) else AnkiSaveState.OutcomeUnknown(it.noteId)
        } ?: records.firstOrNull { !it.confirmed && it.word == wordKey(draft) }?.let {
            _states.value[it.key] ?: AnkiSaveState.OutcomeUnknown(it.noteId)
        } ?: AnkiSaveState.Ready
    } catch (_: Exception) { AnkiSaveState.FailedBeforeWrite("Не удалось прочитать защиту от повторного сохранения") }
    private fun publish(key: String, value: AnkiSaveState) { _states.update { it + (key to value) } }

    @Synchronized fun requireDestinationConfirmation(draft: AnkiCardDraft, metadata: AnkiMetadata) {
        if (active || state(draft).locked) return
        val deck = metadata.decks.firstOrNull { it.id == draft.deckId }?.name ?: "недоступна"
        val model = metadata.models.firstOrNull { it.id == draft.modelId }?.name ?: "недоступен"
        publish(key(draft), AnkiSaveState.Warning("Цель изменилась. Колода: $deck. Тип заметки: $model. Подтвердите новую цель.", draft, metadata))
    }

    @Synchronized fun save(draft: AnkiCardDraft, confirmWarning: Boolean = false) {
        val key = key(draft)
        val previous = state(draft)
        if (active || previous.locked) return
        val acceptedDuplicate = confirmWarning && previous is AnkiSaveState.Warning && previous.destination == null
        active = true
        publish(key, AnkiSaveState.Checking)
        scope.launch {
            var crossedWriteBoundary = false
            var noteId: Long? = null
            try {
                val configuration = settings.snapshot()
                val metadata = AnkiConnection(gateway).load()
                val fresh = AnkiCardDraft.build(draft.originalSelection, draft.content, draft.ipa,
                    configuration.ankiDeckId, configuration.ankiModelId, metadata)
                if (fresh.deckId != draft.deckId || fresh.modelId != draft.modelId) {
                    val deck = metadata.decks.firstOrNull { it.id == fresh.deckId }?.name ?: "недоступна"
                    val model = metadata.models.firstOrNull { it.id == fresh.modelId }?.name ?: "недоступен"
                    publish(key, AnkiSaveState.Warning("Цель изменилась. Колода: $deck. Тип заметки: $model. Подтвердите новую цель.", fresh, metadata))
                    return@launch
                }
                require(fresh.validation.valid && draft.validation.valid && fresh.fields == draft.fields &&
                    fresh.deckId != null && fresh.deckId > 0 && fresh.modelId != null && fresh.modelId > 0)
                val history = receipts.all()
                // An unresolved prior attempt is a hard stop, even in a newly opened popup/process.
                history.firstOrNull { (it.fingerprint == fingerprint(fresh) || it.word == wordKey(fresh)) && !it.confirmed }?.let {
                    publish(key, AnkiSaveState.OutcomeUnknown(it.noteId)); return@launch
                }
                val exact = gateway.hasExactDuplicate(requireNotNull(fresh.modelId), fresh.fields[0])
                val local = history.any { it.confirmed && it.word == wordKey(fresh) }
                if ((exact || local) && !acceptedDuplicate) {
                    publish(key, AnkiSaveState.Warning(if (exact) "Такой Front уже есть в Anki. Добавить ещё одну заметку?"
                        else "Это слово уже сохранялось через Qwen. Другое определение может быть полезным. Добавить ещё одну заметку?"))
                    return@launch
                }
                // Recheck target metadata after duplicate lookup immediately before insertion.
                val finalMetadata = AnkiConnection(gateway).load()
                val finalSettings = settings.snapshot()
                require(finalSettings.ankiDeckId == fresh.deckId && finalSettings.ankiModelId == fresh.modelId)
                require(AnkiCardDraft.build(fresh.originalSelection, fresh.content, fresh.ipa,
                    fresh.deckId, fresh.modelId, finalMetadata).validation.valid)
                receipts.put(SaveReceipt(key, fingerprint(fresh), wordKey(fresh), null, false))
                publish(key, AnkiSaveState.Saving)
                crossedWriteBoundary = true
                val returnedId = gateway.addNote(fresh) { insertedId ->
                    require(insertedId > 0)
                    noteId = insertedId
                    receipts.put(SaveReceipt(key, fingerprint(fresh), wordKey(fresh), insertedId, false))
                }
                require(returnedId != null && returnedId > 0)
                noteId = returnedId
                // Record returned ID before any further provider call, even with a gateway without callback.
                receipts.put(SaveReceipt(key, fingerprint(fresh), wordKey(fresh), noteId, false))
                val verification = gateway.verifyNote(requireNotNull(noteId), fresh)
                if (!verification.confirmed) {
                    publish(key, AnkiSaveState.OutcomeUnknown(noteId, verification.message)); return@launch
                }
                receipts.put(SaveReceipt(key, fingerprint(fresh), wordKey(fresh), noteId, true))
                publish(key, AnkiSaveState.Saved(requireNotNull(noteId)))
            } catch (_: Exception) {
                publish(key, if (crossedWriteBoundary) AnkiSaveState.OutcomeUnknown(noteId)
                    else AnkiSaveState.FailedBeforeWrite("Сохранение не началось. Проверьте доступ к AnkiDroid, выбранную колоду, Cloze и порядок пяти полей."))
            } finally { synchronized(this@AnkiSaveCoordinator) { active = false } }
        }
    }
    /** Retry confirmation is read-only; it never calls addNote or moves a card. */
    @Synchronized fun verifyAgain(draft: AnkiCardDraft) {
        val previous = state(draft) as? AnkiSaveState.OutcomeUnknown ?: return
        val id = previous.noteId ?: return
        if (active) return
        active = true
        scope.launch {
            try {
                val result = gateway.verifyNote(id, draft)
                if (result.confirmed) {
                    receipts.all().filter { !it.confirmed && it.noteId == id && it.fingerprint == fingerprint(draft) }
                        .forEach { receipts.put(it.copy(confirmed = true)) }
                    receipts.put(SaveReceipt(key(draft), fingerprint(draft), wordKey(draft), id, true))
                    publish(key(draft), AnkiSaveState.Saved(id))
                } else publish(key(draft), AnkiSaveState.OutcomeUnknown(id, result.message))
            } catch (_: Exception) { publish(key(draft), previous) }
            finally { synchronized(this@AnkiSaveCoordinator) { active = false } }
        }
    }

    fun dismissWarning(draft: AnkiCardDraft) {
        val key = key(draft)
        if (_states.value[key] is AnkiSaveState.Warning) publish(key, AnkiSaveState.Ready)
    }
    companion object {
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun key(draft: AnkiCardDraft) = draft.operationId
        fun fingerprint(draft: AnkiCardDraft) = hash("${draft.deckId}\u001f${draft.modelId}\u001f${draft.fields.joinToString("\u001f")}")
        fun wordKey(draft: AnkiCardDraft) = hash(draft.content.word.trim().lowercase(Locale.ROOT))
    }
}
