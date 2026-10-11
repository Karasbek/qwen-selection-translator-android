package com.karas.qwentranslator.ui.anki

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karas.qwentranslator.anki.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AnkiDraftPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val metadata = AnkiMetadata(AnkiAvailability(AnkiStatus.CONNECTED), listOf(AnkiDeck(42, "Test deck")),
        listOf(AnkiModel(57, "Test cloze", AnkiModel.expectedFields, 1)))
    private val content = CardContent("agree", "verb", "To agree means to have the same opinion.", "We agree on this plan.")
    private fun state() = AnkiDraftUiState(visible = true, metadata = metadata,
        draft = AnkiCardDraft.build("agree", content, "/əˈɡriː/", 42, 57, metadata), ipaSource = "Free Dictionary API")

    @Test fun editablePreviewRebuildsFrontAndKeepsCloseReachableWithKeyboard() {
        var current by mutableStateOf(state())
        var closed = false
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(current, { data, ipa -> current = current.copy(draft = AnkiCardDraft.build("agree", data, ipa, 42, 57, metadata)) },
                {}, {}, {}, {}, { closed = true })
        } }
        compose.onNodeWithText("Колода: Test deck").assertIsDisplayed()
        compose.onNodeWithText("Тип заметки: Test cloze").assertIsDisplayed()
        compose.onNodeWithText("Пример").performScrollTo().performTextReplacement("They agree today.")
        compose.onNodeWithText("v. To {{c1::agree}} means to have the same opinion.<br><br>They {{c1::agree}} today.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Сохранить в Anki").assertIsEnabled()
        compose.onNodeWithText("Вернуться к переводу").assertIsDisplayed().performClick()
        assertTrue(closed)
    }
    @Test fun malformedWordFormShowsValidationAndNeverStaleFront() {
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(draft = AnkiCardDraft.build("running", CardContent("running", "verb", "To run means moving fast.", "They are running today."), "", 42, 57, metadata)),
                { _, _ -> }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Исправьте данные для формирования Front").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("В определении нет точной формы «running». Добавьте её вручную.").performScrollTo().assertIsDisplayed()
    }
    @Test fun regenerationConfirmationCanKeepCorrections() {
        var kept = false
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(confirmRegeneration = true), { _, _ -> }, {}, {}, { kept = true }, {}, {})
        } }
        compose.onNodeWithText("Заменить исправления?").assertIsDisplayed()
        compose.onNodeWithText("Оставить исправления").performClick()
        assertTrue(kept)
    }
    @Test fun loadingCanBeCancelledAndEditControlsAreDisabled() {
        var cancelled = false
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(loading = true), { _, _ -> }, {}, {}, {}, { cancelled = true }, {})
        } }
        compose.onNodeWithText("Слово или выражение").assertIsNotEnabled()
        compose.onNodeWithText("Отменить подготовку").performScrollTo().performClick()
        assertTrue(cancelled)
    }
    @Test fun saveRequiresValidDraftAndShowsProgressWithoutRecompositionCallbacks() {
        var current by mutableStateOf(state())
        var calls = 0
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(current, { _, _ -> }, {}, {}, {}, {}, {}, onSave = {
                calls++; current = current.copy(saveState = AnkiSaveState.Saving)
            })
        } }
        assertEquals(0, calls)
        compose.onNodeWithText("Сохранить в Anki").performClick()
        compose.onNodeWithText("Сохранить в Anki").assertIsNotEnabled()
        compose.onNodeWithText("Сохранение в Anki…").assertIsDisplayed()
        compose.onNodeWithText("Слово или выражение").assertIsNotEnabled()
        compose.runOnIdle { current = current.copy(dirty = true) }
        assertEquals(1, calls)
    }
    @Test fun invalidDraftCannotSave() {
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(draft = AnkiCardDraft.build("agree", content, "", null, 57, metadata)),
                { _, _ -> }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Сохранить в Anki").assertIsNotEnabled()
    }
    @Test fun duplicateRequiresExplicitConfirmationAndCanCancel() {
        var confirmed = false; var cancelled = false
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(saveState = AnkiSaveState.Warning("Такой Front уже есть")),
                { _, _ -> }, {}, {}, {}, {}, {}, onConfirmSave = { confirmed = true }, onDismissSaveWarning = { cancelled = true })
        } }
        compose.onNodeWithText("Возможный дубликат").assertIsDisplayed()
        assertFalse(confirmed)
        compose.onNodeWithText("Отмена").performClick(); assertTrue(cancelled); assertFalse(confirmed)
    }
    @Test fun unknownOutcomeBlocksInsertionButAllowsReadOnlyRecheck() {
        var checked = false
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(saveState = AnkiSaveState.OutcomeUnknown(123)),
                { _, _ -> }, {}, {}, {}, {}, {}, onVerifySave = { checked = true })
        } }
        compose.onNodeWithText("Не удалось подтвердить сохранение. Проверь AnkiDroid перед повторной попыткой").assertIsDisplayed()
        compose.onNodeWithText("Сохранить в Anki").assertIsNotEnabled()
        compose.onNodeWithText("Проверить сохранение снова").performClick(); assertTrue(checked)
    }
    @Test fun successfulSaveShowsConfirmationAndBlocksAnotherTap() {
        compose.setContent { MaterialTheme {
            AnkiDraftPreview(state().copy(saveState = AnkiSaveState.Saved(123)), { _, _ -> }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Добавлено в Anki. Для других устройств выполните обычную синхронизацию AnkiDroid.").assertIsDisplayed()
        compose.onNodeWithText("Сохранить в Anki").assertIsNotEnabled()
    }

}
