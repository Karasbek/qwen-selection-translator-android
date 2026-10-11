package com.karas.qwentranslator.ui.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.util.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PopupScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun success(block: ResultBlock, text: String) = ResultUiState.Success(TranslationResult(text, "fake", operation = block.operation))
    private fun state(readonly: Boolean = true, source: InvocationSource = InvocationSource.PROCESS_TEXT) = PopupUiState(
        Invocation("Hello", readonly, source), blocks = mapOf(
            ResultBlock.EXPLANATION to success(ResultBlock.EXPLANATION, "A greeting."),
            ResultBlock.TRANSLATION to success(ResultBlock.TRANSLATION, "Привет")))

    @Test fun defaultOrderAndCopyActionsAreUnambiguous() {
        val copied = mutableListOf<String>()
        compose.setContent { MaterialTheme { PopupScreen(state(), { copied += it }, {}, {}, {}, {}) } }
        val explanation = compose.onNodeWithText("Объяснение").fetchSemanticsNode().boundsInRoot.top
        val translation = compose.onNodeWithText("Перевод").fetchSemanticsNode().boundsInRoot.top
        assertTrue(explanation < translation)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.cacheDir, "qwen-reading-popup.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("Копировать: Объяснение").performClick()
        compose.onNodeWithContentDescription("Копировать: Перевод").performClick()
        assertEquals(listOf("A greeting.", "Привет"), copied)
        compose.onNodeWithText("Заменить выделенный текст").assertDoesNotExist()
    }

    @Test fun partialSuccessShowsExplanationWhileTranslationLoads() {
        compose.setContent { MaterialTheme { PopupScreen(state().copy(blocks = state().blocks +
            (ResultBlock.TRANSLATION to ResultUiState.Loading)), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("A greeting.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Загрузка: Перевод").assertIsDisplayed()
        compose.onNodeWithText("Закрыть").assertIsDisplayed()
    }

    @Test fun localErrorRetryKeepsSuccessfulTranslation() {
        val retried = mutableListOf<ResultBlock>()
        compose.setContent { MaterialTheme { PopupScreen(state().copy(blocks = state().blocks +
            (ResultBlock.EXPLANATION to ResultUiState.Error(TranslationFailure(FailureKind.NETWORK)))), {}, {}, {}, { retried += it }, {}) } }
        compose.onNodeWithText("Привет").assertIsDisplayed()
        compose.onNodeWithContentDescription("Повторить: Объяснение").performClick()
        assertEquals(listOf(ResultBlock.EXPLANATION), retried)
        compose.onNodeWithContentDescription("Повторить: Перевод").assertDoesNotExist()
    }

    @Test fun editableReplacementCopiesOnlyTranslation() {
        var replacement = ""
        compose.setContent { MaterialTheme { PopupScreen(state(false), {}, { replacement = it }, {}, {}, {}) } }
        compose.onNodeWithText("Заменить выделенный текст").performClick()
        assertEquals("Привет", replacement)
    }

    @Test fun legacyLinkCannotReplaceEvenWithFalseReadonlyFlag() {
        compose.setContent { MaterialTheme { PopupScreen(state(false, InvocationSource.TRANSLATION_LINK), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Заменить выделенный текст").assertDoesNotExist()
    }

    @Test fun longContentInNarrowLandscapeDarkKeepsCloseAccessible() {
        var closed = false
        val long = state().copy(invocation = Invocation("Long source. ".repeat(100), true, InvocationSource.SHARE),
            blocks = mapOf(ResultBlock.EXPLANATION to success(ResultBlock.EXPLANATION, "Long explanation. ".repeat(100)),
                ResultBlock.TRANSLATION to success(ResultBlock.TRANSLATION, "Длинный перевод. ".repeat(100))))
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.requiredSize(320.dp, 360.dp)) { PopupScreen(long, {}, {}, {}, {}, { closed = true }) }
        } }
        compose.onNodeWithText("Закрыть").assertIsDisplayed().performClick()
        assertTrue(closed)
    }

    @Test fun disabledBlockDoesNotRenderAndReversedOrderRenders() {
        compose.setContent { MaterialTheme { PopupScreen(state().copy(resultOrder = ResultBlock.defaultOrder.reversed(),
            blocks = state().blocks + (ResultBlock.EXPLANATION to ResultUiState.Disabled)), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Объяснение").assertDoesNotExist()
        compose.onNodeWithText("Перевод").assertIsDisplayed()
    }

    @Test fun twoErrorsAndOverflowSettingsAreAvailable() {
        var settings = false
        compose.setContent { MaterialTheme { PopupScreen(state().copy(blocks = mapOf(
            ResultBlock.EXPLANATION to ResultUiState.Error(TranslationFailure(FailureKind.AUTHENTICATION, "Groq")),
            ResultBlock.TRANSLATION to ResultUiState.Error(TranslationFailure(FailureKind.TIMEOUT)))), {}, {}, { settings = true }, {}, {}) } }
        compose.onNodeWithContentDescription("Повторить: Объяснение").assertIsDisplayed()
        compose.onNodeWithContentDescription("Повторить: Перевод").assertIsDisplayed()
        compose.onNodeWithContentDescription("Меню перевода").performClick()
        compose.onNodeWithText("Настройки").performClick()
        assertTrue(settings)
    }

    @Test fun tappingCardKeepsPopupAndTappingBackgroundCloses() {
        var closed = false
        compose.setContent { MaterialTheme { PopupScreen(state(), {}, {}, {}, {}, { closed = true }) } }
        compose.onNodeWithText("A greeting.").performTouchInput { click(center) }
        assertFalse(closed)
        compose.onRoot().performTouchInput { click(Offset(width / 2f, 20f)) }
        assertTrue(closed)
    }
    @Test fun ankiPreparationRequiresAnExplicitClick() {
        var calls = 0
        compose.setContent { MaterialTheme { PopupScreen(state(), {}, {}, {}, {}, {}, onAnki = { calls++ }) } }
        compose.waitForIdle()
        assertEquals(0, calls)
        compose.onNodeWithText("В Anki").assertIsDisplayed().performClick()
        assertEquals(1, calls)
    }

}
