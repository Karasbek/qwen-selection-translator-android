package com.karas.qwentranslator.ui.popup

import androidx.lifecycle.ViewModelStore
import com.karas.qwentranslator.FakeSettings
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.*
import com.karas.qwentranslator.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val input = Invocation("hello", true, InvocationSource.PROCESS_TEXT)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun viewModel(engine: TranslatorEngine, settings: SettingsStore = FakeSettings()): ProcessTextViewModel =
        ProcessTextViewModel(input, engine, settings).also { store.put("popup", it) }

    @Test fun duplicateLoadingTabDoesNotDuplicateRequest() = runTest(dispatcher) {
        var calls = 0
        val vm = viewModel(object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest): TranslationResult { calls++; awaitCancellation() }
        })
        runCurrent()
        vm.selectOperation(Operation.TRANSLATE)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun lateCancelledTranslationCannotOverwriteExplanation() = runTest(dispatcher) {
        val vm = viewModel(object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest): TranslationResult {
                if (request.operation == Operation.TRANSLATE) withContext(NonCancellable) { delay(100) }
                return TranslationResult(request.operation.name, "fake", operation = request.operation)
            }
        })
        runCurrent()
        vm.selectOperation(Operation.EXPLAIN)
        advanceUntilIdle()
        assertEquals(Operation.EXPLAIN, vm.state.value.operation)
        assertEquals("EXPLAIN", (vm.state.value as PopupUiState.Success).text)
    }

    @Test fun newIntentReplacesTextAndResetsOperation() = runTest(dispatcher) {
        val texts = mutableListOf<String>()
        val vm = viewModel(object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest): TranslationResult {
                texts += request.text
                return TranslationResult(request.text, "fake", operation = request.operation)
            }
        })
        runCurrent()
        vm.selectOperation(Operation.EXPLAIN)
        runCurrent()
        vm.acceptInvocation(input.copy(text = "new text", readOnly = false))
        runCurrent()
        assertEquals("new text", (vm.state.value as PopupUiState.Success).text)
        assertEquals(Operation.TRANSLATE, vm.state.value.operation)
        assertTrue(vm.invocation.value.canReplace)
        assertEquals(3, texts.size)
    }

    @Test fun settingsReadFailureBecomesUiError() = runTest(dispatcher) {
        val settings = object : SettingsStore {
            override suspend fun snapshot(): AppSettings = error("private-value")
            override suspend fun save(settings: AppSettings) = Unit
        }
        val vm = viewModel(object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest): TranslationResult = error("must not call")
        }, settings)
        runCurrent()
        assertTrue(vm.state.value is PopupUiState.Error)
        assertFalse((vm.state.value as PopupUiState.Error).message.contains("private-value"))
    }
}
