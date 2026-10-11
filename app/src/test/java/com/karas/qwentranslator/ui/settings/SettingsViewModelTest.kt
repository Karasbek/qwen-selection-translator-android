package com.karas.qwentranslator.ui.settings

import com.karas.qwentranslator.anki.*
import androidx.lifecycle.ViewModelStore
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.FakeSecrets
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.ApiKeyStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private suspend fun loaded(vm: SettingsViewModel) {
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.firstReady() } }
    }
    private suspend fun kotlinx.coroutines.flow.StateFlow<SettingsUiState>.firstReady() =
        first { !it.loading && !it.saving }

    @Test fun rejectsInsecureUrlWithoutWritingCredentials() = runTest(dispatcher) {
        val settings = FakeSettings()
        val secrets = FakeSecrets()
        val vm = SettingsViewModel(settings, secrets).also { store.put("settings", it) }
        loaded(vm)
        vm.updateBaseUrl("http://example.org")
        vm.updateApiKey("new-placeholder")
        vm.save()
        assertNotNull(vm.state.value.error)
        assertEquals("test-placeholder", secrets.value)
    }

    @Test fun secretSaveFailureIsHandledWithoutExposingDetails() = runTest(dispatcher) {
        val secrets = object : ApiKeyStore {
            override fun getApiKey() = ""
            override fun setApiKey(value: String) { error("private-value") }
        }
        val vm = SettingsViewModel(FakeSettings(), secrets).also { store.put("settings", it) }
        loaded(vm)
        vm.save()
        loaded(vm)
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.error.orEmpty().contains("private-value"))
    }

    @Test fun savesAndStateDiagnosticsRedactKey() = runTest(dispatcher) {
        val settings = FakeSettings()
        val secrets = FakeSecrets()
        val vm = SettingsViewModel(settings, secrets).also { store.put("settings", it) }
        loaded(vm)
        vm.updateTarget("en")
        vm.updateApiKey("")
        vm.save()
        loaded(vm)
        assertTrue(vm.state.value.saved)
        assertEquals("en", settings.value.targetLanguage)
        assertEquals("", secrets.value)
        assertFalse(SettingsUiState(apiKey = "private-value").toString().contains("private-value"))
    }

    @Test fun reordersAndPersistsReadingOptionsWithoutLosingKey() = runTest(dispatcher) {
        val settings = FakeSettings()
        val secrets = FakeSecrets()
        val vm = SettingsViewModel(settings, secrets).also { store.put("settings", it) }
        loaded(vm)
        vm.moveBlock(ResultBlock.TRANSLATION, -1)
        vm.setBlockEnabled(ResultBlock.EXPLANATION, false)
        val options = ExplanationOptions(ExplanationLanguage.RUSSIAN, ExplanationDetail.NORMAL, false)
        vm.updateExplanation(options)
        vm.updateSource("en")
        vm.save()
        loaded(vm)
        assertEquals(ResultBlock.defaultOrder.reversed(), settings.value.resultOrder)
        assertEquals(setOf(ResultBlock.TRANSLATION), settings.value.enabledBlocks)
        assertEquals(options, settings.value.explanation)
        assertEquals("en", settings.value.sourceLanguage)
        assertEquals("test-placeholder", secrets.value)
        val restarted = SettingsViewModel(settings, secrets).also { store.put("restarted", it) }
        loaded(restarted)
        assertEquals(settings.value, restarted.state.value.settings())
    }

    @Test fun cannotDisableLastBlockAndOutOfRangeMoveDoesNothing() = runTest(dispatcher) {
        val vm = SettingsViewModel(FakeSettings(), FakeSecrets()).also { store.put("settings", it) }
        loaded(vm)
        vm.setBlockEnabled(ResultBlock.TRANSLATION, false)
        vm.setBlockEnabled(ResultBlock.EXPLANATION, false)
        assertEquals(setOf(ResultBlock.EXPLANATION), vm.state.value.enabledBlocks)
        assertNotNull(vm.state.value.error)
        vm.moveBlock(ResultBlock.EXPLANATION, -1)
        assertEquals(ResultBlock.defaultOrder, vm.state.value.resultOrder)
    }
    private suspend fun ankiLoaded(vm: SettingsViewModel) {
        withContext(Dispatchers.Default) { withTimeout(5_000) {
            vm.state.first { !it.loading && !it.anki.loading && it.anki.availability.status == AnkiStatus.CONNECTED }
        } }
    }

    @Test fun savesAnkiSelectionAndPreservesMissingIdsAfterRestart() = runTest(dispatcher) {
        val settings = FakeSettings()
        val gateway = FakeAnkiGateway()
        val vm = SettingsViewModel(settings, FakeSecrets(), gateway).also { store.put("anki", it) }
        ankiLoaded(vm)
        assertNull(vm.state.value.ankiDeckId)
        assertNull(vm.state.value.ankiModelId)
        vm.selectAnkiDeck(1234567890123L)
        vm.selectAnkiModel(9876543210123L)
        vm.save()
        loaded(vm)
        assertEquals(1234567890123L, settings.value.ankiDeckId)
        assertEquals(9876543210123L, settings.value.ankiModelId)
        gateway.deckList = listOf(AnkiDeck(55, "Another"))
        gateway.modelList = emptyList()
        val restarted = SettingsViewModel(settings, FakeSecrets(), gateway).also { store.put("restartedAnki", it) }
        ankiLoaded(restarted)
        assertEquals(1234567890123L, restarted.state.value.ankiDeckId)
        assertEquals(9876543210123L, restarted.state.value.ankiModelId)
        assertTrue(restarted.state.value.anki.deckMissing(restarted.state.value.ankiDeckId))
        assertTrue(restarted.state.value.anki.modelMissing(restarted.state.value.ankiModelId))
        restarted.selectAnkiDeck(999)
        assertEquals(1234567890123L, restarted.state.value.ankiDeckId)
        restarted.selectAnkiDeck(null)
        restarted.selectAnkiModel(null)
        restarted.save()
        loaded(restarted)
        assertNull(settings.value.ankiDeckId)
        assertNull(settings.value.ankiModelId)
    }

}
