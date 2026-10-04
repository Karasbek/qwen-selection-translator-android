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
    private fun viewModel(settings: SettingsStore = FakeSettings(), action: suspend (TranslationRequest) -> TranslationResult): ProcessTextViewModel =
        ProcessTextViewModel(input, object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest) = action(request)
        }, settings).also { store.put("popup", it) }
    private fun result(request: TranslationRequest) = TranslationResult(request.text, "fake", operation = request.operation)
    private fun ProcessTextViewModel.block(block: ResultBlock) = state.value.blocks.getValue(block)

    @Test fun openingStartsBothInFlightWithoutAwaitingAndRetryWhileLoadingDoesNotDuplicate() = runTest(dispatcher) {
        val requests = mutableListOf<TranslationRequest>()
        val vm = viewModel { requests += it; awaitCancellation() }
        runCurrent()
        assertEquals(setOf(Operation.EXPLAIN, Operation.TRANSLATE), requests.map { it.operation }.toSet())
        assertEquals(ResultBlock.defaultOrder, vm.state.value.resultOrder)
        vm.retry(ResultBlock.EXPLANATION)
        vm.retry(ResultBlock.TRANSLATION)
        runCurrent()
        assertEquals(2, requests.size)
        assertTrue(vm.state.value.blocks.values.all { it == ResultUiState.Loading })
    }

    @Test fun explainSuccessRemainsVisibleWhileTranslateLoads() = runTest(dispatcher) {
        val vm = viewModel { if (it.operation == Operation.TRANSLATE) awaitCancellation() else result(it) }
        runCurrent()
        assertTrue(vm.block(ResultBlock.EXPLANATION) is ResultUiState.Success)
        assertEquals(ResultUiState.Loading, vm.block(ResultBlock.TRANSLATION))
    }

    @Test fun translationRemainsSuccessfulWhenExplanationFails() = runTest(dispatcher) {
        val vm = viewModel {
            if (it.operation == Operation.EXPLAIN) throw TranslationException.ProviderFailure("private", kind = FailureKind.RATE_LIMIT)
            result(it)
        }
        runCurrent()
        assertEquals(FailureKind.RATE_LIMIT, (vm.block(ResultBlock.EXPLANATION) as ResultUiState.Error).failure.kind)
        assertTrue(vm.block(ResultBlock.TRANSLATION) is ResultUiState.Success)
    }

    @Test fun retryOnlyFailedBlockAndKeepsOtherResult() = runTest(dispatcher) {
        val calls = mutableMapOf<Operation, Int>()
        val vm = viewModel {
            val count = (calls[it.operation] ?: 0) + 1
            calls[it.operation] = count
            if (it.operation == Operation.EXPLAIN && count == 1) error("private")
            result(it)
        }
        runCurrent()
        val translated = vm.block(ResultBlock.TRANSLATION)
        vm.retry(ResultBlock.EXPLANATION)
        runCurrent()
        assertEquals(2, calls[Operation.EXPLAIN])
        assertEquals(1, calls[Operation.TRANSLATE])
        assertEquals(translated, vm.block(ResultBlock.TRANSLATION))
        assertTrue(vm.block(ResultBlock.EXPLANATION) is ResultUiState.Success)
    }

    @Test fun lateCancelledResponsesCannotOverwriteNewInvocation() = runTest(dispatcher) {
        val vm = viewModel {
            if (it.text == "hello") withContext(NonCancellable) { delay(100) }
            result(it)
        }
        runCurrent()
        vm.acceptInvocation(input.copy(text = "new text", readOnly = false))
        advanceUntilIdle()
        assertEquals("new text", vm.state.value.invocation.text)
        for (block in ResultBlock.defaultOrder) assertEquals("new text", (vm.block(block) as ResultUiState.Success).result.outputText)
    }

    @Test fun settingsReadFailureBecomesSafePerBlockErrorAndLocalRetryRecoversOnlyOne() = runTest(dispatcher) {
        var failRead = true
        val settings = object : SettingsStore {
            override suspend fun snapshot(): AppSettings { if (failRead) error("private-value"); return AppSettings() }
            override suspend fun save(settings: AppSettings) = Unit
        }
        val calls = mutableListOf<Operation>()
        val vm = viewModel(settings) { calls += it.operation; result(it) }
        runCurrent()
        assertTrue(vm.state.value.blocks.values.all { it is ResultUiState.Error })
        assertFalse(vm.state.value.toString().contains("private-value"))
        failRead = false
        vm.retry(ResultBlock.EXPLANATION)
        runCurrent()
        assertEquals(listOf(Operation.EXPLAIN), calls)
        assertTrue(vm.block(ResultBlock.TRANSLATION) is ResultUiState.Error)
    }

    @Test fun disabledBlocksAreNotRequested() = runTest(dispatcher) {
        val calls = mutableListOf<Operation>()
        val vm = viewModel(FakeSettings(AppSettings(enabledBlocks = setOf(ResultBlock.EXPLANATION)))) {
            calls += it.operation; result(it)
        }
        runCurrent()
        vm.retry(ResultBlock.TRANSLATION)
        assertEquals(listOf(Operation.EXPLAIN), calls)
        assertEquals(ResultUiState.Disabled, vm.block(ResultBlock.TRANSLATION))
    }

    @Test fun translationOnlyRespectsSavedOrder() = runTest(dispatcher) {
        val order = ResultBlock.defaultOrder.reversed()
        val vm = viewModel(FakeSettings(AppSettings(resultOrder = order, enabledBlocks = setOf(ResultBlock.TRANSLATION)))) { result(it) }
        runCurrent()
        assertEquals(order, vm.state.value.resultOrder)
        assertEquals(ResultUiState.Disabled, vm.block(ResultBlock.EXPLANATION))
        assertTrue(vm.block(ResultBlock.TRANSLATION) is ResultUiState.Success)
    }

    @Test fun explanationOptionsAndLanguagePairReachEngine() = runTest(dispatcher) {
        val options = ExplanationOptions(ExplanationLanguage.RUSSIAN, ExplanationDetail.DETAILED, false)
        val requests = mutableListOf<TranslationRequest>()
        viewModel(FakeSettings(AppSettings(sourceLanguage = "en", targetLanguage = "de", explanation = options))) {
            requests += it; result(it)
        }
        runCurrent()
        assertEquals(options, requests.single { it.operation == Operation.EXPLAIN }.explanation)
        assertTrue(requests.all { it.sourceLanguage == "en" && it.targetLanguage == "de" })
    }

    @Test fun editableReplaceUsesOnlyCurrentTranslationAndPreservesWhitespace() = runTest(dispatcher) {
        val vm = viewModel { TranslationResult(if (it.operation == Operation.TRANSLATE) "привет" else "explanation", "fake", operation = it.operation) }
        vm.acceptInvocation(input.copy(text = " \nhello\t", readOnly = false))
        runCurrent()
        assertEquals(" \nпривет\t", vm.state.value.replacementText("привет"))
        assertNull(vm.state.value.replacementText("explanation"))
        vm.acceptInvocation(input.copy(text = "next", readOnly = false))
        assertNull(vm.state.value.replacementText("привет"))
    }

    @Test fun readonlyShareTranslateAndLegacyLinksNeverReplace() = runTest(dispatcher) {
        val vm = viewModel { result(it) }
        for (source in InvocationSource.entries) {
            val invocation = if (source == InvocationSource.TRANSLATION_LINK)
                TranslationLinkParser.parse("http", "translate.google.com", "/", null, "auto/en/hello")!!
                else input.copy(source = source)
            vm.acceptInvocation(invocation)
            runCurrent()
            assertNull(vm.state.value.replacementText("hello"))
        }
    }

    @Test fun clearingViewModelCancelsBothOperations() = runTest(dispatcher) {
        var cancelled = 0
        viewModel { try { awaitCancellation() } finally { cancelled++ } }
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(2, cancelled)
    }

    @Test fun settingsReturnRefreshesChangedConfigurationButUnchangedResumeDoesNotDuplicate() = runTest(dispatcher) {
        val settings = FakeSettings()
        val calls = mutableListOf<Operation>()
        val vm = viewModel(settings) { calls += it.operation; result(it) }
        runCurrent()
        vm.refreshSettings()
        runCurrent()
        assertEquals(2, calls.size)
        settings.value = settings.value.copy(enabledBlocks = setOf(ResultBlock.EXPLANATION), resultOrder = ResultBlock.defaultOrder.reversed())
        vm.refreshSettings()
        runCurrent()
        assertEquals(2, calls.size)
        assertEquals(ResultUiState.Disabled, vm.block(ResultBlock.TRANSLATION))
        assertEquals(settings.value.resultOrder, vm.state.value.resultOrder)
    }

    @Test fun enablingNewBlockRequestsOnlyThatBlockAndKeepsExistingResult() = runTest(dispatcher) {
        val settings = FakeSettings(AppSettings(enabledBlocks = setOf(ResultBlock.TRANSLATION)))
        val calls = mutableListOf<Operation>()
        val vm = viewModel(settings) { calls += it.operation; result(it) }
        runCurrent()
        val translated = vm.block(ResultBlock.TRANSLATION)
        settings.value = settings.value.copy(enabledBlocks = ResultBlock.defaultOrder.toSet())
        vm.refreshSettings()
        runCurrent()
        assertEquals(listOf(Operation.TRANSLATE, Operation.EXPLAIN), calls)
        assertEquals(translated, vm.block(ResultBlock.TRANSLATION))
    }
}
