package com.karas.qwentranslator.anki

import org.junit.Assert.*
import org.junit.Test

class FakeAnkiGateway : AnkiGateway {
    var status = AnkiStatus.CONNECTED
    var deckList = listOf(AnkiDeck(1234567890123L, "English"))
    var modelList = listOf(AnkiModel(9876543210123L, "Custom", AnkiModel.expectedFields, 1))
    var reads = 0
    var failure: Exception? = null
    override fun availability() = AnkiAvailability(status, "2.25.1")
    override fun decks(): List<AnkiDeck> { reads++; failure?.let { throw it }; return deckList }
    override fun models(): List<AnkiModel> { reads++; return modelList }
}

class AnkiConnectionTest {
    @Test fun notInstalledDoesNotQueryCollection() {
        val gateway = FakeAnkiGateway().apply { status = AnkiStatus.NOT_INSTALLED }
        assertEquals(AnkiStatus.NOT_INSTALLED, AnkiConnection(gateway).load().availability.status)
        assertEquals(0, gateway.reads)
    }
    @Test fun missingPermissionDoesNotQueryCollection() {
        val gateway = FakeAnkiGateway().apply { status = AnkiStatus.PERMISSION_REQUIRED }
        assertEquals(AnkiStatus.PERMISSION_REQUIRED, AnkiConnection(gateway).load().availability.status)
        assertEquals(0, gateway.reads)
    }
    @Test fun disabledApiDoesNotQueryCollection() {
        val gateway = FakeAnkiGateway().apply { status = AnkiStatus.API_UNAVAILABLE }
        assertEquals(AnkiStatus.API_UNAVAILABLE, AnkiConnection(gateway).load().availability.status)
        assertEquals(0, gateway.reads)
    }
    @Test fun emptyDecksAreSuccessfulMetadata() {
        val result = AnkiConnection(FakeAnkiGateway().apply { deckList = emptyList() }).load()
        assertEquals(AnkiStatus.CONNECTED, result.availability.status)
        assertTrue(result.decks.isEmpty())
    }
    @Test fun preservesLongIdsNamesAndOrderedFields() {
        val result = AnkiConnection(FakeAnkiGateway()).load()
        assertEquals(1234567890123L, result.decks.single().id)
        assertEquals("English", result.decks.single().name)
        assertEquals(9876543210123L, result.models.single().id)
        assertEquals("Custom", result.models.single().name)
        assertEquals(AnkiModel.expectedFields, result.models.single().fields)
        assertTrue(result.models.single().compatible)
    }
    @Test fun wrongFieldsOrderDuplicatesMissingAndExtraAreIncompatible() {
        val fields = AnkiModel.expectedFields
        listOf(fields.reversed(), fields.dropLast(1), fields + "Extra", fields.dropLast(1) + "Front",
            fields.map { if (it == "Pinyin") "IPA" else it }).forEach {
            assertFalse(AnkiModel(1, "Cloze", it, 1).compatible)
        }
    }
    @Test fun clozeUsesTypeRatherThanName() {
        assertTrue(AnkiModel(1, "Custom", AnkiModel.expectedFields, 1).compatible)
        assertFalse(AnkiModel(2, "Cloze", AnkiModel.expectedFields, 0).isCloze)
        assertFalse(AnkiModel(2, "Cloze", AnkiModel.expectedFields, 0).compatible)
        assertFalse(AnkiModel(3, "Future", AnkiModel.expectedFields, 2).compatible)
    }
    @Test fun disappearedDeckAndModelRequireExplicitReselection() {
        val metadata = AnkiConnection(FakeAnkiGateway()).load()
        assertTrue(metadata.deckMissing(99))
        assertTrue(metadata.modelMissing(99))
        assertFalse(metadata.deckMissing(null))
        assertFalse(metadata.modelMissing(null))
        assertFalse(metadata.deckMissing(1234567890123L))
        assertFalse(metadata.modelMissing(9876543210123L))
    }
    @Test fun revokedPermissionAndProviderErrorsAreSafe() {
        val gateway = FakeAnkiGateway().apply { failure = SecurityException("private") }
        assertEquals(AnkiStatus.PERMISSION_REQUIRED, AnkiConnection(gateway).load().availability.status)
        gateway.failure = IllegalStateException("private")
        val result = AnkiConnection(gateway).load()
        assertEquals(AnkiStatus.ERROR, result.availability.status)
        assertFalse(result.toString().contains("private"))
    }
}
