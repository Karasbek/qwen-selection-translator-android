package com.karas.qwentranslator.ui.popup

import com.karas.qwentranslator.anki.*
import com.karas.qwentranslator.ui.anki.AnkiDraftViewModel
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.karas.qwentranslator.ProcessTextActivity
import com.karas.qwentranslator.TranslatorApplication
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.data.SettingsRepository
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.nio.file.Files
import java.io.File

/** Activity lifecycle checks on the device, with counted fake requests and no credential access. */
class ProcessTextActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as TranslatorApplication
    private val engineField = app.container.javaClass.getDeclaredField("engine").apply { isAccessible = true }
    private val settingsField = app.container.javaClass.getDeclaredField("settings").apply { isAccessible = true }
    private lateinit var originalSettings: SettingsRepository
    private lateinit var temporarySettings: File
    private val settingsJob = SupervisorJob()
    private lateinit var original: TranslatorEngine
    private var scenario: ActivityScenario<ProcessTextActivity>? = null
    private val requests = CopyOnWriteArrayList<TranslationRequest>()
    private val cancelled = CopyOnWriteArrayList<Operation>()
    private val oldReplies = CompletableDeferred<Unit>()
    private var blockRequests = false
    private var ipaCalls = 0
    private var ipaCancelled = false
    private var blockIpa = false
    private val ipaField get() = app.container.javaClass.getDeclaredField("pronunciations").apply { isAccessible = true }
    private val ankiField get() = app.container.javaClass.getDeclaredField("anki").apply { isAccessible = true }
    private lateinit var originalIpa: PronunciationRepository
    private lateinit var originalAnki: AnkiGateway
    private val saverField get() = app.container.javaClass.getDeclaredField("ankiSaver").apply { isAccessible = true }
    private lateinit var originalSaver: AnkiSaveCoordinator
    private val saveJob = SupervisorJob()
    private val writes = java.util.concurrent.atomic.AtomicInteger()
    private val writeGate = java.util.concurrent.CountDownLatch(1)
    private var blockWrite = false

    @Before fun installCountedEngine() {
        originalSaver = app.container.ankiSaver
        originalIpa = app.container.pronunciations
        originalAnki = app.container.anki
        ipaField.set(app.container, object : PronunciationRepository {
            override suspend fun lookup(word: String, sourceLanguage: String): Pronunciations {
                ipaCalls++
                if (blockIpa) try { awaitCancellation() } finally { ipaCancelled = true }
                return Pronunciations(listOf("/əˈɡriː/"))
            }
        })
        ankiField.set(app.container, object : AnkiGateway {
            override fun availability() = AnkiAvailability(AnkiStatus.CONNECTED)
            override fun decks() = listOf(AnkiDeck(42, "Test deck"))
            override fun models() = listOf(AnkiModel(57, "Test cloze", AnkiModel.expectedFields, 1))
            override fun hasExactDuplicate(modelId: Long, front: String) = false
            override fun addNote(draft: AnkiCardDraft, onInserted: (Long) -> Unit): Long {
                writes.incrementAndGet(); onInserted(123)
                if (blockWrite) check(writeGate.await(15, java.util.concurrent.TimeUnit.SECONDS))
                return 123
            }
            override fun verifyNote(noteId: Long, draft: AnkiCardDraft) = NoteVerification(true)
        })
        original = app.container.engine
        originalSettings = app.container.settings
        temporarySettings = Files.createTempDirectory(app.cacheDir.toPath(), "lifecycle-settings-").toFile()
        settingsField.set(app.container, SettingsRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(settingsJob + Dispatchers.IO),
            produceFile = { temporarySettings.resolve("test.preferences_pb") },
        )))
        saverField.set(app.container, AnkiSaveCoordinator(app.container.anki, app.container.settings,
            MemorySaveReceiptStore(), CoroutineScope(saveJob + Dispatchers.IO)))
        // Test-only replacement; persistent user settings and the encrypted key are untouched.
        engineField.set(app.container, object : TranslatorEngine {
            override suspend fun execute(request: TranslationRequest): TranslationResult {
                requests += request
                if (blockRequests) {
                    try { awaitCancellation() } finally { cancelled += request.operation }
                }
                if (request.text == "old") withContext(NonCancellable) { oldReplies.await() }
                return TranslationResult(if (request.operation == Operation.EXPLAIN) explanation(request.text) else request.text, "counted fake", operation = request.operation)
            }
        })
    }

    @After fun restoreEngine() {
        writeGate.countDown()
        runBlocking { saveJob.cancelAndJoin() }
        saverField.set(app.container, originalSaver)
        oldReplies.complete(Unit)
        scenario?.close()
        ipaField.set(app.container, originalIpa)
        ankiField.set(app.container, originalAnki)
        engineField.set(app.container, original)
        settingsField.set(app.container, originalSettings)
        runBlocking { settingsJob.cancelAndJoin() }
        temporarySettings.deleteRecursively()
    }

    private fun explanation(word: String) = if (word == "optimization")
        "Optimization is the process of making something as effective as possible.\n\nExample: Companies optimize their production processes."
        else "To $word means something. Example: They $word today."
    private fun preparation(word: String) = AnkiPreparationRequest(word, "en", explanation(word))

    private fun intent(text: String) = Intent(instrumentation.targetContext, ProcessTextActivity::class.java)
        .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    private fun launch(text: String) {
        val expected = requests.size + 2
        scenario = ActivityScenario.launch(intent(text))
        await { requests.size == expected }
    }

    private fun state(): PopupUiState {
        lateinit var value: PopupUiState
        scenario!!.onActivity { value = ViewModelProvider(it)[ProcessTextViewModel::class.java].state.value }
        return value
    }

    private fun awaitOrientation(expected: Int) {
        await {
            var orientation = 0
            scenario!!.onActivity { orientation = it.resources.configuration.orientation }
            orientation == expected
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue("Lifecycle condition timed out", condition())
    }

    @Test fun recreationKeepsResultsWithoutDuplicateRequests() {
        launch("current")
        await { state().blocks.values.all { it is ResultUiState.Success } }
        val before = state()
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(before, state())
        assertEquals(2, requests.size)
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
        scenario!!.recreate()
        instrumentation.waitForIdleSync()
        assertEquals(before, state())
        assertEquals(2, requests.size)
    }

    @Test fun newIntentRejectsLateOldRepliesAndSurvivesRecreation() {
        launch("old")
        instrumentation.targetContext.startActivity(intent("new").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        await { requests.size == 4 }
        await { state().invocation.text == "new" && state().blocks.values.all { it is ResultUiState.Success } }
        writeGate.countDown()
        runBlocking { saveJob.cancelAndJoin() }
        saverField.set(app.container, originalSaver)
        oldReplies.complete(Unit)
        instrumentation.waitForIdleSync()
        scenario!!.recreate()
        assertEquals("new", state().invocation.text)
        state().blocks.values.forEach { val result = (it as ResultUiState.Success).result; assertEquals(if (result.operation == Operation.EXPLAIN) explanation("new") else "new", result.outputText) }
        assertEquals(4, requests.size)
    }

    @Test fun closingActivityCancelsBothRequests() {
        blockRequests = true
        launch("close while loading")
        scenario!!.close()
        scenario = null
        await { cancelled.size == 2 }
        assertEquals(setOf(Operation.EXPLAIN, Operation.TRANSLATE), cancelled.toSet())
    }
    private fun draftModel(): AnkiDraftViewModel {
        lateinit var value: AnkiDraftViewModel
        scenario!!.onActivity { value = ViewModelProvider(it)[AnkiDraftViewModel::class.java] }
        return value
    }

    @Test fun previewAndEditsSurviveRotationWithoutNewCardOrTranslationRequests() {
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        launch("agree")
        val model = draftModel()
        scenario!!.onActivity { model.open(preparation("agree")) }
        await { !model.state.value.loading }
        assertEquals(1, ipaCalls)
        scenario!!.onActivity { model.edit(model.state.value.draft!!.content.copy(explanation = "We agree on the plan."), "/manual/") }
        scenario!!.onActivity {
            val draft = model.state.value.draft!!
            val body = draft.content.explanation!!
            model.edit(draft.content.copy(selections = ClozeRanges.toggleAt(body, draft.content.selections!!, body.indexOf("plan"))), draft.ipa)
        }
        val before = model.state.value
        assertTrue(before.draft!!.fields[0].contains("{{c1::plan}}"))
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(before, draftModel().state.value)
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
        scenario!!.recreate()
        assertEquals(before, draftModel().state.value)
        assertEquals(1, ipaCalls)
        assertEquals(2, requests.size)
    }
    @Test fun finishingPopupCancelsCardPreparationAsWellAsTranslations() {
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        blockIpa = true
        launch("agree")
        val model = draftModel()
        scenario!!.onActivity { model.open(preparation("agree")) }
        await { ipaCalls == 1 }
        scenario!!.close(); scenario = null
        await { ipaCancelled }
    }

    @Test fun saveSurvivesRecreationAndPopupFinishWithOnlyOneFakeInsertion() {
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        blockWrite = true
        launch("agree")
        val model = draftModel()
        scenario!!.onActivity { model.open(preparation("agree")) }
        await { !model.state.value.loading }
        scenario!!.onActivity { model.save(); model.save() }
        await { writes.get() == 1 && model.state.value.saveState == AnkiSaveState.Saving }
        scenario!!.recreate()
        assertEquals(AnkiSaveState.Saving, draftModel().state.value.saveState)
        scenario!!.onActivity { model.save(); model.close() }
        scenario!!.close(); scenario = null
        writeGate.countDown()
        val draft = model.state.value.draft!!
        await { app.container.ankiSaver.state(draft) is AnkiSaveState.Saved }
        assertEquals(1, writes.get())
        launch("agree")
        val reopened = draftModel()
        scenario!!.onActivity { reopened.open(preparation("agree")) }
        await { !reopened.state.value.loading }
        scenario!!.onActivity { reopened.save() }
        await { reopened.state.value.saveState is AnkiSaveState.Warning }
        assertEquals(1, writes.get())
    }

    @Test fun unchangedSettingsAndPreviewReopenKeepRequestCountsAfterRecreation() {
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        launch("agree")
        await { state().blocks.values.all { it is ResultUiState.Success } }
        scenario!!.onActivity { activity ->
            val popup = ViewModelProvider(activity)[ProcessTextViewModel::class.java]
            repeat(3) { popup.refreshSettings() }
        }
        val model = draftModel()
        scenario!!.onActivity { model.open(preparation("agree")) }
        await { !model.state.value.loading }
        scenario!!.onActivity { model.close(); model.open(preparation("agree")) }
        await { !model.state.value.loading }
        scenario!!.recreate()
        instrumentation.waitForIdleSync()
        assertEquals(2, requests.size)
        assertEquals(1, ipaCalls)
        assertEquals(0, writes.get())
    }

    /** With -e readeraSmoke true, a synthetic book selection is delivered by the actual ReadEra UI. */
    @Test fun readEraTranslateActionUsesOnlyCountedFakes() {
        val physical = InstrumentationRegistry.getArguments().getString("readeraSmoke") == "true"
        val selectedWord = InstrumentationRegistry.getArguments().getString("readeraWord") ?: "abandon"
        if (!physical) {
            scenario = ActivityScenario.launch(Intent(instrumentation.targetContext, ProcessTextActivity::class.java)
                .setAction(Intent.ACTION_TRANSLATE).putExtra(Intent.EXTRA_TEXT, "abandon"))
            await { requests.size == 2 }
            assertEquals(com.karas.qwentranslator.util.InvocationSource.TRANSLATE_ACTION, state().invocation.source)
            assertEquals(0, ipaCalls)
            return
        }
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        ipaField.set(app.container, originalIpa) // Physical smoke uses bundled offline dictionary.
        // The operator selects only the supplied public fixture while these fake dependencies remain installed.
        val deadline = SystemClock.uptimeMillis() + 55_000
        while (requests.size < 2 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.text.trim() == selectedWord })
        var popup: ProcessTextActivity? = null
        instrumentation.runOnMainSync {
            popup = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<ProcessTextActivity>().single()
        }
        try {
            var model: AnkiDraftViewModel? = null
            instrumentation.runOnMainSync { model = ViewModelProvider(popup!!)[AnkiDraftViewModel::class.java] }
            // Operator presses the actual popup button while counted fakes remain installed.
            val previewDeadline = SystemClock.uptimeMillis() + 55_000
            while ((!model!!.state.value.visible || model!!.state.value.loading) && SystemClock.uptimeMillis() < previewDeadline) SystemClock.sleep(50)
            assertTrue(model!!.state.value.visible && !model!!.state.value.loading)
            assertEquals(explanation(selectedWord), model!!.state.value.draft!!.content.explanation)
            assertEquals(0, ipaCalls)
            assertEquals(if (selectedWord == "optimization") "/ɑptəməˈzeɪʃən/" else "/əˈbændən/", model!!.state.value.draft!!.ipa)
            if (selectedWord == "optimization") {
                val draft = model!!.state.value.draft!!
                assertFalse(draft.content.selections!!.headerHidden)
                assertEquals(1, draft.content.selections!!.hidden.size)
                assertEquals(1, ClozeRanges.suggested(draft.content.explanation!!, selectedWord).size)
                assertTrue(draft.fields[0].startsWith("{{c1::Optimization}} is"))
                assertFalse(draft.fields[0].contains("{{c1::optimize}}"))
            }
            assertTrue(model!!.state.value.draft!!.validation.valid)
            assertEquals(0, writes.get())
            assertEquals(2, requests.size)
        } finally { instrumentation.runOnMainSync { popup?.finish() } }
    }

    @Test fun repeatedPopupUsesProcessCacheAndKeepsCopyReplaceAndAnkiPreparation() {
        runBlocking { app.container.settings.save(com.karas.qwentranslator.data.AppSettings(ankiDeckId = 42, ankiModelId = 57)) }
        val networkCalls = java.util.concurrent.atomic.AtomicInteger()
        val tracker = ApiUsageTracker()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            networkCalls.incrementAndGet()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("fake").body(okhttp3.ResponseBody.create(null,
                    """{"usage":{"total_tokens":30},"choices":[{"message":{"content":"fixture result"},"finish_reason":"stop"}]}""")).build()
        }.build()
        val keys = object : com.karas.qwentranslator.data.ApiKeyStore {
            override fun getApiKey() = "fake-only-key"
            override fun setApiKey(value: String) { error("not needed") }
        }
        val provider = com.karas.qwentranslator.network.OpenAiCompatibleProvider(client, app.container.settings, keys, tracker)
        engineField.set(app.container, DefaultTranslatorEngine(listOf(provider)))
        scenario = ActivityScenario.launch(intent("agree"))
        await { state().blocks.values.all { it is ResultUiState.Success } }
        assertEquals(2, networkCalls.get())
        scenario!!.close()
        scenario = ActivityScenario.launch(intent("agree").putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
        await { state().blocks.values.all { it is ResultUiState.Success } }
        assertEquals("fixture result", state().replacementText("fixture result"))
        scenario!!.recreate(); instrumentation.waitForIdleSync()
        assertEquals(2, networkCalls.get())
        for (operation in listOf(ApiOperation.EXPLAIN, ApiOperation.TRANSLATE)) {
            assertEquals(1L, tracker.state.value.operations.getValue(operation).cacheHits)
            assertEquals(TokenTotal(30, 1), tracker.state.value.operations.getValue(operation).total)
        }
        val draft = draftModel()
        scenario!!.onActivity { draft.open(preparation("agree")) }
        await { !draft.state.value.loading }
        assertEquals(1, ipaCalls)
        assertTrue(draft.state.value.draft!!.validation.valid)
        assertEquals(0, writes.get())
    }

}
