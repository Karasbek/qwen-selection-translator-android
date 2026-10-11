package com.karas.qwentranslator.ui.anki

import androidx.lifecycle.ViewModelStore
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.anki.*
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
    private var lookups = 0
    private val explain = "To leave something behind. Example: They abandon the project."
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vm(lookup: suspend () -> Pronunciations = { Pronunciations(listOf("/əˈbændən/")) }) =
        AnkiDraftViewModel(object : PronunciationRepository {
            override suspend fun lookup(word: String, sourceLanguage: String): Pronunciations { lookups++; return lookup() }
        }, gateway, settings).also { store.put("draft", it) }
    private fun request(text: String? = explain) = AnkiPreparationRequest("abandon", explanation = text)
    private suspend fun complete(vm: AnkiDraftViewModel) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { vm.state.first { !it.loading } }
    }
    @Test fun explicitOpenUsesEntireExplainAndNoGeneratorDependency() = runTest(dispatcher) {
        val vm = vm(); assertEquals(0, lookups)
        vm.open(request()); vm.open(request()); complete(vm)
        assertEquals(1, lookups)
        val draft = vm.state.value.draft!!
        assertEquals(explain, draft.content.explanation)
        assertEquals("", draft.content.partOfSpeech)
        assertTrue(draft.validation.valid)
        assertEquals("{{c1::abandon}}<br><br>To leave something behind. Example&#58; They {{c1::abandon}} the project.", draft.fields[0])
        assertEquals(0, gateway.writes)
    }
    @Test fun editingRebuildsAndClearsIpaOnWordChange() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(CardContent("abandoned", explanation = "They abandoned the project."), "/manual/")
        assertEquals("", vm.state.value.draft!!.ipa)
        assertTrue(vm.state.value.dirty)
        assertTrue(vm.state.value.ipaVariants.isEmpty())
        assertTrue(vm.state.value.draft!!.fields[0].contains("{{c1::abandoned}}"))
    }
    @Test fun preparationRequiresConfirmationAndUsesFreshExplain() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(CardContent("abandon", explanation = "Manual correction."), "/manual/")
        vm.regenerate(request("Fresh explanation without an example."))
        assertTrue(vm.state.value.confirmRegeneration)
        assertEquals("Manual correction.", vm.state.value.draft!!.content.explanation)
        vm.dismissConfirmation(); assertEquals("/manual/", vm.state.value.draft!!.ipa)
        vm.regenerate(request("Fresh explanation without an example.")); vm.confirmRegeneration(); complete(vm)
        assertEquals("Fresh explanation without an example.", vm.state.value.draft!!.content.explanation)
        assertEquals(2, lookups)
    }
    @Test fun closeReopenKeepsManualEditsWithoutRepeatedLookup() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(CardContent("abandon", explanation = "Manual text."), "/manual/")
        vm.close(); vm.open(request()); complete(vm)
        assertEquals(1, lookups); assertEquals("Manual text.", vm.state.value.draft!!.content.explanation)
        assertEquals("/manual/", vm.state.value.draft!!.ipa)
    }
    @Test fun newSelectionWarnsBeforeReplacingDirtyDraft() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(vm.state.value.draft!!.content, "/manual/"); vm.close()
        vm.open(AnkiPreparationRequest("running", explanation = "Moving faster than walking."))
        assertTrue(vm.state.value.confirmRegeneration)
        assertEquals("abandon", vm.state.value.draft!!.originalSelection)
        vm.confirmRegeneration(); complete(vm)
        assertEquals("running", vm.state.value.draft!!.originalSelection)
    }
    @Test fun cancelDuringIpaKeepsPreparedContentAndRejectsLateIpa() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = vm { withContext(NonCancellable) { gate.await() }; Pronunciations(listOf("/late/")) }
        vm.open(request())
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { it.draft?.validation?.valid == true } } }
        vm.close(); gate.complete(Unit); runCurrent()
        vm.open(request()); complete(vm)
        assertEquals(explain, vm.state.value.draft!!.content.explanation)
        assertEquals("", vm.state.value.draft!!.ipa); assertEquals(1, lookups)
    }
    @Test fun viewModelDestructionCancelsLookup() = runTest(dispatcher) {
        var cancelled = false
        val vm = vm { try { awaitCancellation() } finally { cancelled = true } }
        vm.open(request())
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (lookups == 0) yield() } }
        store.clear(); runCurrent(); assertTrue(cancelled)
    }
    @Test fun missingExplainOffersManualEditingForEveryUnavailableState() = runTest(dispatcher) {
        for (status in listOf(ExplainState.MISSING, ExplainState.LOADING, ExplainState.DISABLED, ExplainState.FAILED)) {
            val vm = vm(); vm.open(request(null).copy(explainState = status)); complete(vm)
            assertFalse(vm.state.value.draft!!.validation.valid)
            assertNotNull(vm.state.value.explainNotice)
            vm.edit(CardContent("abandon", explanation = "Text prepared manually."), "")
            assertTrue(vm.state.value.draft!!.validation.valid)
        }
        assertEquals(0, gateway.writes)
    }
    @Test fun dictionaryErrorDoesNotLoseExplanation() = runTest(dispatcher) {
        val vm = vm { error("offline resource unavailable") }
        vm.open(request()); complete(vm)
        assertEquals(explain, vm.state.value.draft!!.content.explanation)
        assertTrue(vm.state.value.draft!!.validation.valid); assertEquals("", vm.state.value.draft!!.ipa)
    }
    @Test fun multipleIpaVariantsNeedSelectionAndAreNeverGuessed() = runTest(dispatcher) {
        val vm = vm { Pronunciations(listOf("/one/", "/two/")) }
        vm.open(request()); complete(vm)
        assertEquals("", vm.state.value.draft!!.ipa); assertEquals(2, vm.state.value.ipaVariants.size)
        vm.edit(vm.state.value.draft!!.content, "/two/")
        assertEquals("/two/", vm.state.value.draft!!.ipa); assertNotNull(vm.state.value.ipaSource)
    }
    @Test fun absentIpaLeavesPinyinEmpty() = runTest(dispatcher) {
        val vm = vm { Pronunciations() }; vm.open(request()); complete(vm)
        assertEquals("", vm.state.value.draft!!.fields[1])
    }
    @Test fun changedWordNeverReusesOldExplanationOnExplicitPreparation() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(CardContent("running", explanation = "Manual new-word explanation."), "")
        vm.regenerate(request()); vm.confirmRegeneration(); complete(vm)
        assertEquals("running", vm.state.value.draft!!.content.word)
        assertEquals("", vm.state.value.draft!!.content.explanation)
        assertFalse(vm.state.value.draft!!.validation.valid)
    }
    @Test fun reopeningRefreshesTargetsAndKeepsEdits() = runTest(dispatcher) {
        val vm = vm(); vm.open(request()); complete(vm)
        vm.edit(vm.state.value.draft!!.content, "/manual/"); vm.close()
        settings.value = settings.value.copy(ankiDeckId = null); gateway.modelList = emptyList()
        vm.open(request()); complete(vm)
        assertEquals("/manual/", vm.state.value.draft!!.ipa)
        assertTrue(vm.state.value.dirty); assertFalse(vm.state.value.draft!!.validation.valid)
        assertEquals(1, lookups)
    }
    @Test fun unavailableAnkiStillAllowsReviewButNeverSavingAutomatically() = runTest(dispatcher) {
        gateway.status = AnkiStatus.NOT_INSTALLED
        val vm = vm(); vm.open(request()); complete(vm)
        assertEquals(explain, vm.state.value.draft!!.content.explanation)
        assertFalse(vm.state.value.draft!!.validation.valid); assertEquals(0, gateway.writes)
    }
}
