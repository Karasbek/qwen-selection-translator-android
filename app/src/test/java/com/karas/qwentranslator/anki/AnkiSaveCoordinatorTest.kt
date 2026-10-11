package com.karas.qwentranslator.anki

import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class AnkiSaveCoordinatorTest {
    private class Fake : AnkiGateway {
        var available = AnkiStatus.CONNECTED
        var decks = listOf(AnkiDeck(42, "Target"))
        var models = listOf(AnkiModel(57, "Cloze", AnkiModel.expectedFields, 1))
        var duplicate = false
        var lookupFailure = false
        var writeFailure = false
        var failureAfterInsert = false
        var verificationFailure = false
        var verification = NoteVerification(true)
        var id: Long? = 123
        var calls = 0
        var verified = 0
        var inserted: AnkiCardDraft? = null
        var onLookup: () -> Unit = {}
        override fun availability() = AnkiAvailability(available)
        override fun decks() = decks
        override fun models() = models
        override fun hasExactDuplicate(modelId: Long, front: String): Boolean {
            if (lookupFailure) error("private")
            assertEquals(57, modelId.toInt()); onLookup(); return duplicate
        }
        override fun addNote(draft: AnkiCardDraft, onInserted: (Long) -> Unit): Long? {
            calls++; inserted = draft
            if (writeFailure) error("private")
            id?.takeIf { it > 0 }?.let(onInserted)
            if (failureAfterInsert) error("private")
            return id
        }
        override fun verifyNote(noteId: Long, draft: AnkiCardDraft): NoteVerification {
            verified++; assertEquals(id, noteId)
            if (verificationFailure) error("private")
            return verification
        }
    }
    private val settings = FakeSettings(AppSettings(ankiDeckId = 42, ankiModelId = 57))
    private val gateway = Fake()
    private val receipts = MemorySaveReceiptStore()
    private fun draft() = AnkiCardDraft.build("agree", CardContent("agree", "verb", "To agree means sharing an opinion.", "We agree today."),
        "/əˈɡriː/", 42, 57, AnkiConnection(gateway).load())
    private fun coordinator(scope: CoroutineScope) = AnkiSaveCoordinator(gateway, settings, receipts, scope)

    @Test fun noWriteBeforeExplicitTapAndOneTapOneInsert() = runTest {
        val c = coordinator(this); val d = draft()
        assertEquals(0, gateway.calls); c.save(d); advanceUntilIdle()
        assertEquals(1, gateway.calls); assertEquals(1, gateway.verified)
        assertEquals(AnkiSaveState.Saved(123), c.state(d)); assertEquals(123L, receipts.all().single().noteId)
    }
    @Test fun doubleTapAndRepeatedCallbacksHaveOneInsert() = runTest {
        val c = coordinator(this); val d = draft()
        repeat(10) { c.save(d) }; advanceUntilIdle(); repeat(10) { c.save(d) }
        advanceUntilIdle(); assertEquals(1, gateway.calls)
    }
    @Test fun concurrentCallbacksClaimOneOperation() = runTest {
        val c = coordinator(this); val d = draft(); val executor = Executors.newFixedThreadPool(4)
        try { val latch = CountDownLatch(20)
            repeat(20) { executor.execute { c.save(d); latch.countDown() } }; latch.await()
            advanceUntilIdle(); assertEquals(1, gateway.calls)
        } finally { executor.shutdownNow() }
    }
    @Test fun recreationRecoversReceiptAndDoesNotInsertAgain() = runTest {
        val d = draft(); coordinator(this).save(d); advanceUntilIdle()
        val recreated = coordinator(this); recreated.save(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.Saved(123), recreated.state(d)); assertEquals(1, gateway.calls)
    }
    @Test fun pendingIntentAfterProcessDeathIsUnknownAndCannotRetry() = runTest {
        val d = draft(); receipts.put(SaveReceipt(d.operationId, AnkiSaveCoordinator.fingerprint(d), AnkiSaveCoordinator.wordKey(d), null, false))
        val c = coordinator(this); c.save(d); c.save(draft()); advanceUntilIdle()
        assertTrue(c.state(d) is AnkiSaveState.OutcomeUnknown); assertEquals(0, gateway.calls)
    }
    @Test fun nullIdNeverAutomaticallyRetries() = runTest {
        gateway.id = null; val c = coordinator(this); val d = draft(); c.save(d); advanceUntilIdle()
        c.save(d); c.save(d, true); advanceUntilIdle()
        assertTrue(c.state(d) is AnkiSaveState.OutcomeUnknown); assertEquals(1, gateway.calls); assertEquals(0, gateway.verified)
    }
    @Test fun invalidIdIsUnknown() = runTest {
        gateway.id = -1; val c = coordinator(this); val d = draft(); c.save(d); advanceUntilIdle()
        assertTrue(c.state(d) is AnkiSaveState.OutcomeUnknown)
    }
    @Test fun exceptionBeforeProviderBoundaryFailsWithoutWriteAndPreservesDraft() = runTest {
        gateway.lookupFailure = true; val d = draft(); val c = coordinator(this)
        c.save(d); advanceUntilIdle(); assertTrue(c.state(d) is AnkiSaveState.FailedBeforeWrite)
        assertEquals(0, gateway.calls); gateway.lookupFailure = false
        val corrected = d.copy(content = d.content.copy(example = "They agree now."))
        val rebuilt = AnkiCardDraft.build(corrected.originalSelection, corrected.content, corrected.ipa, 42, 57, AnkiConnection(gateway).load())
        c.save(rebuilt); advanceUntilIdle(); assertEquals(rebuilt.fields, gateway.inserted!!.fields)
    }
    @Test fun exceptionAtProviderBoundaryIsConservativelyUnknownEvenWithoutId() = runTest {
        gateway.writeFailure = true; val d = draft(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.OutcomeUnknown(), c.state(d)); c.save(d); advanceUntilIdle(); assertEquals(1, gateway.calls)
    }
    @Test fun exceptionAfterInsertionPreservesIdAndNeverRetries() = runTest {
        gateway.failureAfterInsert = true; val d = draft(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.OutcomeUnknown(123), c.state(d)); assertEquals(123L, receipts.all().single().noteId)
        c.save(d); advanceUntilIdle(); assertEquals(1, gateway.calls)
    }
    @Test fun verificationExceptionDoesNotClaimNoteAbsent() = runTest {
        gateway.verificationFailure = true; val d = draft(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.OutcomeUnknown(123), c.state(d)); assertFalse(receipts.all().single().confirmed)
    }
    @Test fun deckMismatchDuringVerificationIsUnknownWithDiagnostic() = runTest {
        gateway.verification = NoteVerification(false, "Wrong deck")
        val d = draft(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.OutcomeUnknown(123, "Wrong deck"), c.state(d))
    }
    @Test fun missingDeckPreventsWrite() = runTest {
        val d = draft(); gateway.decks = emptyList(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        assertTrue(c.state(d) is AnkiSaveState.FailedBeforeWrite); assertEquals(0, gateway.calls)
    }
    @Test fun missingModelPreventsWrite() = runTest {
        val d = draft(); gateway.models = emptyList(); val c = coordinator(this); c.save(d); advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun changedFieldOrderPreventsWrite() = runTest {
        val d = draft(); gateway.models = gateway.models.map { it.copy(fields = it.fields.reversed()) }
        coordinator(this).save(d); advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun ordinaryNoteTypePreventsWrite() = runTest {
        val d = draft(); gateway.models = gateway.models.map { it.copy(type = 0) }
        coordinator(this).save(d); advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun revokedPermissionDisabledApiAndUninstalledPreventWrite() = runTest {
        val d = draft()
        listOf(AnkiStatus.PERMISSION_REQUIRED, AnkiStatus.API_UNAVAILABLE, AnkiStatus.NOT_INSTALLED).forEach {
            gateway.available = it; coordinator(this).save(d.copy(operationId = it.name)); advanceUntilIdle()
        }; assertEquals(0, gateway.calls)
    }
    @Test fun filteredDeckFailsBeforeInsertion() = runTest {
        val d = draft(); gateway.decks = listOf(AnkiDeck(42, "Filtered", true)); coordinator(this).save(d); advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun duplicateWarningAndExplicitOverride() = runTest {
        gateway.duplicate = true; val d = draft(); val c = coordinator(this)
        c.save(d, true); advanceUntilIdle() // Boolean without an actual displayed warning cannot bypass it.
        assertTrue(c.state(d) is AnkiSaveState.Warning); assertEquals(0, gateway.calls)
        c.save(d, true); advanceUntilIdle(); assertEquals(1, gateway.calls)
    }
    @Test fun cancellingDuplicateDoesNotWrite() = runTest {
        gateway.duplicate = true; val d = draft(); val c = coordinator(this); c.save(d); advanceUntilIdle()
        c.dismissWarning(d); assertEquals(AnkiSaveState.Ready, c.state(d)); assertEquals(0, gateway.calls)
    }
    @Test fun newOperationSameWordWarnsButCanIntentionallyAddAnotherDefinition() = runTest {
        val c = coordinator(this); c.save(draft()); advanceUntilIdle()
        val next = draft(); c.save(next); advanceUntilIdle(); assertTrue(c.state(next) is AnkiSaveState.Warning)
        c.save(next, true); advanceUntilIdle(); assertEquals(2, gateway.calls)
    }
    @Test fun targetSettingsChangeRequiresNewExplicitConfirmation() = runTest {
        val d = draft(); gateway.decks += AnkiDeck(99, "New deck"); settings.value = settings.value.copy(ankiDeckId = 99)
        val c = coordinator(this); c.save(d); advanceUntilIdle()
        val warning = c.state(d) as AnkiSaveState.Warning
        assertEquals(0, gateway.calls); assertTrue(warning.message.contains("New deck"))
        c.dismissWarning(d); c.save(warning.destination!!.copy(operationId = d.operationId)); advanceUntilIdle()
        assertEquals(99L, gateway.inserted!!.deckId)
    }
    @Test fun metadataChangeDuringDuplicateCheckStillPreventsWrite() = runTest {
        val d = draft(); gateway.onLookup = { gateway.models = emptyList() }
        coordinator(this).save(d); advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun forgedFrontOrC2CannotBeWritten() = runTest {
        val d = draft(); coordinator(this).save(d.copy(fields = listOf("{{c2::agree}}", "", "", "", "")))
        advanceUntilIdle(); assertEquals(0, gateway.calls)
    }
    @Test fun exactFiveFieldsComeFromCorrectedBuilder() = runTest {
        val d = draft(); coordinator(this).save(d); advanceUntilIdle()
        assertEquals(listOf(d.fields[0], d.fields[1], "", "", ""), gateway.inserted!!.fields)
        assertEquals(2, Regex("\\{\\{c1::").findAll(gateway.inserted!!.fields[0]).count())
    }
    @Test fun receiptFailureStopsBeforeWrite() = runTest {
        val failing = object : SaveReceiptStore { override fun all() = emptyList<SaveReceipt>(); override fun put(receipt: SaveReceipt) = error("disk") }
        val c = AnkiSaveCoordinator(gateway, settings, failing, this); val d = draft(); c.save(d); advanceUntilIdle()
        assertTrue(c.state(d) is AnkiSaveState.FailedBeforeWrite); assertEquals(0, gateway.calls)
    }
    @Test fun recheckingKnownIdOnlyReadsAndCanConfirmTransientFailure() = runTest {
        gateway.verificationFailure = true; val d = draft(); val c = coordinator(this)
        c.save(d); advanceUntilIdle(); gateway.verificationFailure = false
        c.verifyAgain(d); c.verifyAgain(d); advanceUntilIdle()
        assertEquals(AnkiSaveState.Saved(123), c.state(d)); assertEquals(1, gateway.calls)
        assertEquals(2, gateway.verified); assertTrue(receipts.all().single().confirmed)
    }
    @Test fun closedAndReopenedEditorSeesPendingOperationAndCannotInsertAgain() = runTest {
        val d = draft(); val c = coordinator(this)
        receipts.put(SaveReceipt(d.operationId, AnkiSaveCoordinator.fingerprint(d), AnkiSaveCoordinator.wordKey(d), 123, false))
        val reopened = draft()
        assertEquals(AnkiSaveState.OutcomeUnknown(123), c.state(reopened)); c.save(reopened); advanceUntilIdle()
        assertEquals(0, gateway.calls)
    }

    @Test fun refreshedDestinationStillRequiresConfirmationBeforeAnyWrite() = runTest {
        val d = draft(); val c = coordinator(this)
        c.requireDestinationConfirmation(d, AnkiConnection(gateway).load())
        assertTrue(c.state(d) is AnkiSaveState.Warning); assertEquals(0, gateway.calls)
        c.dismissWarning(d); c.save(d); advanceUntilIdle(); assertEquals(1, gateway.calls)
    }

}
