package com.karas.qwentranslator.ui.anki

import androidx.lifecycle.ViewModelStore
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.anki.*
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AnkiDraftViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val gateway = FakeAnkiGateway()
    private val settings = FakeSettings(AppSettings(ankiDeckId = 1234567890123L, ankiModelId = 9876543210123L))
    private var calls = 0
    private var lookups = 0
    private var cancelled = 0
    private var captured: CardGenerationRequest? = null
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm(action: suspend (CardGenerationRequest) -> CardContent = { content(it.selection) },
        lookup: suspend () -> DictionaryIpa? = { DictionaryIpa("/əˈɡriː/", "dictionary") }): AnkiDraftViewModel =
        AnkiDraftViewModel(object : CardGenerator {
            override suspend fun generate(request: CardGenerationRequest): CardContent { calls++; captured = request; return action(request) }
        }, object : IpaLookup { override suspend fun lookup(word: String, sourceLanguage: String): DictionaryIpa? { lookups++; return lookup() } },
            gateway, settings).also { store.put("draft", it) }
    private fun content(word: String) = CardContent(word, "verb", "To $word means something.", "They $word today.")
    private suspend fun complete(vm: AnkiDraftViewModel) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { vm.state.first { !it.loading } }
    }
    private suspend fun started() = withContext(Dispatchers.Default) { withTimeout(5_000) { while (calls == 0) yield() } }

    @Test fun onlyExplicitOpenGeneratesAndRepeatedOpenDoesNotDuplicate() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(0, calls)
        vm.open(CardGenerationRequest("agree", explanation = "existing explanation", translation = "existing translation"))
        vm.open(CardGenerationRequest("agree"))
        vm.regenerate() // A second explicit click while loading must also be ignored.
        complete(vm)
        assertEquals(1, calls)
        assertEquals(1, lookups)
        assertTrue(vm.state.value.draft!!.validation.valid)
        assertEquals("existing explanation", captured?.explanation)
        assertEquals("existing translation", captured?.translation)
        assertEquals(2, gateway.reads) // Only decks/models. Interface has no write operations.
        assertEquals(1234567890123L, settings.value.ankiDeckId)
    }
    @Test fun editingRebuildsAndClearsIpaWhenWordChanges() = runTest(dispatcher) {
        val vm = vm(); vm.open(CardGenerationRequest("agree")); complete(vm)
        vm.edit(content("agreed"), vm.state.value.draft!!.ipa)
        assertEquals("", vm.state.value.draft!!.ipa)
        assertNull(vm.state.value.ipaSource)
        assertTrue(vm.state.value.draft!!.fields[0].contains("{{c1::agreed}}"))
        assertEquals(1, calls)
    }
    @Test fun regenerationNeedsConfirmationAndFailurePreservesCorrections() = runTest(dispatcher) {
        var fail = false
        val vm = vm({ if (fail) error("private"); content(it.selection) })
        vm.open(CardGenerationRequest("agree")); complete(vm)
        val edited = content("agree").copy(example = "We agree on the plan.")
        vm.edit(edited, "/manual/")
        vm.regenerate()
        assertTrue(vm.state.value.confirmRegeneration)
        assertEquals(1, calls)
        vm.dismissConfirmation(); assertEquals(edited, vm.state.value.draft!!.content)
        fail = true
        vm.regenerate(); vm.confirmRegeneration(); complete(vm)
        assertEquals(edited, vm.state.value.draft!!.content)
        assertTrue(vm.state.value.dirty)
        assertFalse(vm.state.value.toString().contains("private"))
    }
    @Test fun closeAndReopenPreservesEditsWithoutNewRequest() = runTest(dispatcher) {
        val vm = vm(); vm.open(CardGenerationRequest("agree")); complete(vm)
        vm.edit(content("agree"), "/manual/")
        vm.close(); vm.open(CardGenerationRequest("agree")); complete(vm)
        assertEquals("/manual/", vm.state.value.draft!!.ipa)
        assertEquals(1, calls)
    }
    @Test fun newSelectionWarnsBeforeReplacingEditedDraft() = runTest(dispatcher) {
        val vm = vm(); vm.open(CardGenerationRequest("agree")); complete(vm)
        vm.edit(content("agree"), ""); vm.close(); vm.open(CardGenerationRequest("running"))
        assertTrue(vm.state.value.confirmRegeneration)
        assertEquals("agree", vm.state.value.draft!!.originalSelection)
        vm.confirmRegeneration(); complete(vm)
        assertEquals("running", vm.state.value.draft!!.originalSelection)
        assertEquals(2, calls)
    }
    @Test fun cancelledGenerationCannotApplyLateResultOrCallIpa() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val vm = vm({ withContext(NonCancellable) { release.await() }; content(it.selection) })
        vm.open(CardGenerationRequest("agree")); started()
        vm.cancelGeneration(); release.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.draft!!.fields.isEmpty())
        assertEquals(0, lookups)
    }
    @Test fun closingPopupCancelsGenerationThroughViewModelLifecycle() = runTest(dispatcher) {
        val vm = vm({ try { awaitCancellation() } finally { cancelled++ } })
        vm.open(CardGenerationRequest("agree")); started()
        store.clear(); runCurrent()
        assertEquals(1, cancelled)
    }
    @Test fun missingTargetsAndChangedFormStayInvalidWithoutWritingSettings() = runTest(dispatcher) {
        gateway.deckList = emptyList(); gateway.modelList = emptyList()
        val vm = vm({ CardContent("run", "verb", "To run means moving fast.", "They are running today.") })
        vm.open(CardGenerationRequest("running")); complete(vm)
        assertEquals("running", vm.state.value.draft!!.content.word)
        assertFalse(vm.state.value.draft!!.validation.valid)
        assertTrue(vm.state.value.draft!!.fields.isEmpty())
        assertEquals(1234567890123L, settings.value.ankiDeckId)
    }
    @Test fun generationErrorDoesNotPreventManualEditingAndDictionaryErrorIsOptional() = runTest(dispatcher) {
        val vm = vm({ throw TranslationException.ProviderFailure("private", kind = FailureKind.RATE_LIMIT) })
        vm.open(CardGenerationRequest("agree")); complete(vm)
        assertEquals(FailureKind.RATE_LIMIT, vm.state.value.failure?.kind)
        vm.edit(content("agree"), "")
        assertTrue(vm.state.value.draft!!.validation.valid)
        val next = vm(lookup = { error("offline") })
        next.open(CardGenerationRequest("agree")); complete(next)
        assertTrue(next.state.value.draft!!.validation.valid)
        assertEquals("", next.state.value.draft!!.ipa)
    }
    @Test fun reopeningRefreshesSelectedTargetsWithoutRegeneratingOrLosingEdits() = runTest(dispatcher) {
        val vm = vm(); vm.open(CardGenerationRequest("agree")); complete(vm)
        vm.edit(content("agree"), "/manual/"); vm.close()
        settings.value = settings.value.copy(ankiDeckId = null)
        gateway.modelList = emptyList()
        vm.open(CardGenerationRequest("agree")); complete(vm)
        assertEquals(1, calls)
        assertEquals("/manual/", vm.state.value.draft!!.ipa)
        assertTrue(vm.state.value.dirty)
        assertNull(vm.state.value.draft!!.deckId)
        assertFalse(vm.state.value.draft!!.validation.valid)
    }

}
