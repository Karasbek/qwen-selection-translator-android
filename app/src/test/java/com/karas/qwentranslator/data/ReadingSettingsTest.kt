package com.karas.qwentranslator.data

import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.karas.qwentranslator.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ReadingSettingsTest {
    @Test fun defaultsAreExplanationThenTranslationBothEnabled() {
        val settings = AppSettings()
        assertEquals(listOf(ResultBlock.EXPLANATION, ResultBlock.TRANSLATION), settings.resultOrder)
        assertEquals(settings.resultOrder.toSet(), settings.enabledBlocks)
        assertEquals("auto", settings.sourceLanguage)
        assertEquals("ru", settings.targetLanguage)
        assertEquals(ExplanationOptions(ExplanationLanguage.ENGLISH, ExplanationDetail.SIMPLE, true), settings.explanation)
    }
    @Test fun oldPreferencesMigrateWithoutClearingExistingProviderSettings() {
        val old = mutablePreferencesOf(stringPreferencesKey("model") to "custom-model", stringPreferencesKey("target_language") to "de")
        val loaded = SettingsPreferences.decode(old)
        assertEquals("custom-model", loaded.model)
        assertEquals("de", loaded.targetLanguage)
        assertEquals(ResultBlock.defaultOrder, loaded.resultOrder)
        assertEquals(ExplanationOptions(), loaded.explanation)
    }
    @Test fun corruptOrFutureBlockValuesAndEmptyEnabledSetHaveSafeDefaults() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("result_order") to "UNKNOWN,TRANSLATION,TRANSLATION",
            stringSetPreferencesKey("enabled_blocks") to setOf("UNKNOWN"),
            stringPreferencesKey("explanation_language") to "FUTURE")
        val loaded = SettingsPreferences.decode(prefs)
        assertEquals(ResultBlock.defaultOrder.reversed(), loaded.resultOrder)
        assertEquals(ResultBlock.defaultOrder.toSet(), loaded.enabledBlocks)
        assertEquals(ExplanationLanguage.ENGLISH, loaded.explanation.language)
        assertNotNull(AppSettings(enabledBlocks = emptySet()).validationError())
    }
    @Test fun actualDataStoreSurvivesRepositoryAndStoreRestart() = runBlocking {
        val directory = Files.createTempDirectory("reading-settings").toFile()
        val file = directory.resolve("settings.preferences_pb")
        val changed = AppSettings(resultOrder = ResultBlock.defaultOrder.reversed(), enabledBlocks = setOf(ResultBlock.TRANSLATION),
            sourceLanguage = "en", targetLanguage = "de",
            explanation = ExplanationOptions(ExplanationLanguage.SAME_AS_SOURCE, ExplanationDetail.DETAILED, false))
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
            first.save(changed)
            firstJob.cancelAndJoin()
            val restarted = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(changed, restarted.snapshot())
            try { restarted.save(changed.copy(enabledBlocks = emptySet())); fail("Must reject all disabled") }
            catch (_: IllegalArgumentException) { assertEquals(changed, restarted.snapshot()) }
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
