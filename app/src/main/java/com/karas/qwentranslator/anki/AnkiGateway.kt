package com.karas.qwentranslator.anki

/** Official metadata and narrowly scoped, explicit note insertion. No collection management. */
interface AnkiGateway {
    fun availability(): AnkiAvailability
    fun decks(): List<AnkiDeck>
    fun models(): List<AnkiModel>
    fun hasExactDuplicate(modelId: Long, front: String): Boolean = error("Duplicate API unavailable")
    fun addNote(draft: AnkiCardDraft, onInserted: (Long) -> Unit): Long? = error("Write API unavailable")
    fun verifyNote(noteId: Long, draft: AnkiCardDraft): NoteVerification = error("Verification API unavailable")
}

enum class AnkiStatus { NOT_INSTALLED, API_UNAVAILABLE, PERMISSION_REQUIRED, CONNECTED, ERROR }
data class AnkiAvailability(val status: AnkiStatus, val version: String? = null)
data class AnkiDeck(val id: Long, val name: String, val filtered: Boolean = false)
data class AnkiModel(val id: Long, val name: String, val fields: List<String>, val type: Int) {
    val isCloze: Boolean get() = type == 1
    val compatible: Boolean get() = isCloze && fields == expectedFields
    companion object {
        val expectedFields = listOf("Front", "Pinyin", "Дополнение оборота", "Image", "Sound")
    }
}
data class AnkiMetadata(
    val availability: AnkiAvailability = AnkiAvailability(AnkiStatus.API_UNAVAILABLE),
    val decks: List<AnkiDeck> = emptyList(),
    val models: List<AnkiModel> = emptyList(),
    val loading: Boolean = false,
) {
    fun deckMissing(id: Long?) = id != null && decks.none { it.id == id }
    fun modelMissing(id: Long?) = id != null && models.none { it.id == id }
}

class AnkiConnection(private val gateway: AnkiGateway) {
    fun load(): AnkiMetadata = try {
        val availability = gateway.availability()
        if (availability.status != AnkiStatus.CONNECTED) AnkiMetadata(availability)
        else AnkiMetadata(availability, gateway.decks(), gateway.models())
    } catch (_: SecurityException) {
        AnkiMetadata(AnkiAvailability(AnkiStatus.PERMISSION_REQUIRED))
    } catch (_: Exception) {
        // Provider errors may contain private details. Do not log or display them.
        AnkiMetadata(AnkiAvailability(AnkiStatus.ERROR))
    }
}

data class NoteVerification(val confirmed: Boolean, val message: String? = null)
