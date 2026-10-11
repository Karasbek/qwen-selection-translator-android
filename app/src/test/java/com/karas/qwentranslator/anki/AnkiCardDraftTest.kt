package com.karas.qwentranslator.anki

import org.junit.Assert.*
import org.junit.Test

class AnkiCardDraftTest {
    private val metadata = AnkiConnection(FakeAnkiGateway()).load()
    private val content = CardContent("abandon", "verb", "To abandon means to leave behind.", "They abandon the project.")
    private fun draft(deck: Long? = 1234567890123L, model: Long? = 9876543210123L, meta: AnkiMetadata = metadata) =
        AnkiCardDraft.build("abandon", content, "", deck, model, meta)
    @Test fun validatedDraftStoresOriginalIdsAndFields() {
        val draft = draft()
        assertTrue(draft.validation.valid)
        assertEquals("abandon", draft.originalSelection)
        assertEquals(content, draft.content)
        assertEquals(1234567890123L, draft.deckId)
        assertEquals(9876543210123L, draft.modelId)
        assertEquals(5, draft.fields.size)
        assertEquals(listOf("", "", "", ""), draft.fields.drop(1))
    }
    @Test fun missingSelectionCannotBecomeReady() {
        assertFalse(draft(deck = null).validation.valid)
        assertFalse(draft(model = null).validation.valid)
    }
    @Test fun disappearedDeckOrModelDoesNotFallBack() {
        val disappeared = metadata.copy(decks = listOf(AnkiDeck(1, "Other")), models = emptyList())
        val draft = draft(meta = disappeared)
        assertFalse(draft.validation.valid)
        assertEquals(1234567890123L, draft.deckId)
        assertEquals(9876543210123L, draft.modelId)
        assertEquals(2, draft.validation.errors.size)
    }
    @Test fun normalOrWrongFieldModelIsRejected() {
        val model = metadata.models.single()
        assertFalse(draft(meta = metadata.copy(models = listOf(model.copy(type = 0)))).validation.valid)
        assertFalse(draft(meta = metadata.copy(models = listOf(model.copy(fields = listOf("Front"))))).validation.valid)
    }
    @Test fun disconnectedMetadataNeverProducesReadyDraft() {
        for (status in AnkiStatus.entries.filter { it != AnkiStatus.CONNECTED })
            assertFalse(draft(meta = metadata.copy(availability = AnkiAvailability(status))).validation.valid)
    }
    @Test fun editingRebuildsInsteadOfKeepingStaleFront() {
        val edited = AnkiCardDraft.build("running", content, "/əˈbændən/", 1234567890123L, 9876543210123L, metadata)
        assertEquals("running", edited.originalSelection)
        assertTrue(edited.validation.valid)
        assertTrue(edited.fields[0].contains("{{c1::abandon}}"))
    }
}
