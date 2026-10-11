package com.karas.qwentranslator.ui.settings

import com.karas.qwentranslator.anki.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karas.qwentranslator.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun content(state: SettingsUiState = SettingsUiState(loading = false),
        onExplanation: (ExplanationOptions) -> Unit = {}, onMove: (ResultBlock, Int) -> Unit = { _, _ -> }) {
        compose.setContent { MaterialTheme {
            SettingsScreen(state, {}, {}, {}, {}, {}, onExplanation, { _, _ -> }, onMove, {})
        } }
    }
    @Test fun lastEnabledBlockSwitchIsDisabledAndSaveIsReachable() {
        content(SettingsUiState(loading = false, enabledBlocks = setOf(ResultBlock.EXPLANATION)))
        compose.onNodeWithContentDescription("Включить: Объяснение").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Включить: Перевод").assertIsEnabled()
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
    }
    @Test fun moveControlsChooseBlockAndDirection() {
        val moves = mutableListOf<Pair<ResultBlock, Int>>()
        content(onMove = { block, direction -> moves += block to direction })
        compose.onNodeWithContentDescription("Вверх: Объяснение").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Вверх: Перевод").performClick()
        assertEquals(listOf(ResultBlock.TRANSLATION to -1), moves)
    }
    @Test fun explanationLanguageSelectionHasRealCallback() {
        var changed: ExplanationOptions? = null
        content(onExplanation = { changed = it })
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText("Русский").performClick()
        assertEquals(ExplanationLanguage.RUSSIAN, changed?.language)
    }
    @Test fun examplesSwitchPassesOption() {
        var changed: ExplanationOptions? = null
        content(onExplanation = { changed = it })
        compose.onNodeWithContentDescription("Пример в объяснении").performScrollTo().performClick()
        assertEquals(false, changed?.examples)
    }
    @Test fun ankiSelectorsShowFieldsAndCallCallbacks() {
        val metadata = AnkiMetadata(AnkiAvailability(AnkiStatus.CONNECTED, "2.25.1"),
            listOf(AnkiDeck(42, "Test deck")),
            listOf(AnkiModel(57, "Test cloze", AnkiModel.expectedFields, 1)))
        var deck: Long? = null
        var model: Long? = null
        compose.setContent { MaterialTheme {
            SettingsScreen(SettingsUiState(loading = false, anki = metadata, ankiModelId = 57),
                {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {},
                onAnkiDeck = { deck = it }, onAnkiModel = { model = it })
        } }
        compose.onNodeWithText("Не выбрана").performScrollTo().performClick()
        compose.onNodeWithText("Test deck").performClick()
        assertEquals(42L, deck)
        compose.onNodeWithText("Test cloze").performScrollTo().performClick()
        compose.onAllNodesWithText("Test cloze")[1].performClick()
        assertEquals(57L, model)
        compose.onNodeWithText("Совместимо с вашим Cloze: все пять полей совпадают").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingSelectionDoesNotDisplayAnotherDeckAsSelected() {
        content(SettingsUiState(loading = false, ankiDeckId = 99, ankiModelId = 98,
            anki = AnkiMetadata(AnkiAvailability(AnkiStatus.CONNECTED), listOf(AnkiDeck(42, "Another")))))
        compose.onNodeWithText("Ранее выбранная колода недоступна. Выберите колоду вручную.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ранее выбранный тип заметки недоступен. Выберите тип вручную.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Another").assertDoesNotExist()
    }

    @Test fun diagnosticsShowKnownTokensAndUnknownUsageWithoutSensitiveData() {
        val counters = ApiCounters(started = 2, httpAttempts = 2, success = 1, cancelled = 1,
            prompt = TokenTotal(20, 1), completion = TokenTotal(10, 1), total = TokenTotal(30, 1))
        compose.setContent { MaterialTheme {
            SettingsScreen(SettingsUiState(loading = false), {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {},
                diagnostics = ApiDiagnostics(ApiOperation.entries.associateWith { counters }))
        } }
        compose.onNodeWithText("Расход Groq").performScrollTo().performClick()
        compose.onAllNodesWithText("Input: 20 (1 ответов) · Output: 10 (1 ответов)", substring = true)[0]
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Cached: неизвестно", substring = true)[0].assertExists()
    }

}
