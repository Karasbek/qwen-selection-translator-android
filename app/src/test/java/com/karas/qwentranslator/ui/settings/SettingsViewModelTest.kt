package com.karas.qwentranslator.ui.settings

import androidx.lifecycle.ViewModelStore
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.FakeSecrets
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
}
