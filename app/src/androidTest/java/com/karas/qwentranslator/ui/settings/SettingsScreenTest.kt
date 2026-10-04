package com.karas.qwentranslator.ui.settings

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
}
