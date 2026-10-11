package com.karas.qwentranslator.ui.anki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karas.qwentranslator.anki.CardContent
import com.karas.qwentranslator.ui.popup.FailurePresentation

@Composable
fun AnkiDraftPreview(
    state: AnkiDraftUiState,
    onEdit: (CardContent, String) -> Unit,
    onRegenerate: () -> Unit,
    onConfirmRegeneration: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onCancelGeneration: () -> Unit,
    onClose: () -> Unit,
) {
    if (!state.visible) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("Предпросмотр Anki", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Черновик хранится до закрытия перевода. Запись в Anki появится на следующем этапе.",
                            style = MaterialTheme.typography.bodySmall)
                        val draft = state.draft
                        if (draft != null) {
                            Text("Колода: " + (state.metadata.decks.firstOrNull { it.id == draft.deckId }?.name ?: "не выбрана или недоступна"))
                            Text("Тип заметки: " + (state.metadata.models.firstOrNull { it.id == draft.modelId }?.name ?: "не выбран или недоступен"))
                            val content = draft.content
                            @Composable fun field(label: String, value: String, update: (String) -> Unit, singleLine: Boolean = false) {
                                OutlinedTextField(value = value, onValueChange = update, label = { Text(label) },
                                    enabled = !state.loading, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
                            }
                            field("Слово или выражение", content.word, { onEdit(content.copy(word = it), draft.ipa) }, true)
                            field("Часть речи", content.partOfSpeech, { onEdit(content.copy(partOfSpeech = it), draft.ipa) }, true)
                            field("Определение", content.definition, { onEdit(content.copy(definition = it), draft.ipa) })
                            field("Пример", content.example, { onEdit(content.copy(example = it), draft.ipa) })
                            field("IPA (поле Pinyin)", draft.ipa, { onEdit(content, it) }, true)
                            if (!state.loading) Text(state.ipaSource?.let { "IPA: $it (словарный вариант)" }
                                ?: if (draft.ipa.isBlank()) "Надёжный IPA не найден. Можно ввести вручную." else "IPA введён вручную",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Front", style = MaterialTheme.typography.titleSmall)
                            SelectionContainer { Text(draft.fields.firstOrNull() ?: if (state.loading) "Front появится после подготовки" else "Исправьте данные для формирования Front") }
                            if (!state.loading) {
                                Text(if (draft.validation.valid) "Карточка корректна: две вставки c1, одна карточка" else "Нужно исправить:",
                                    color = if (draft.validation.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                draft.validation.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        state.failure?.let { Text(FailurePresentation.message(it), color = MaterialTheme.colorScheme.error) }
                        if (state.loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Подготовка карточки…")
                            TextButton(onClick = onCancelGeneration) { Text("Отменить подготовку") }
                        }
                        OutlinedButton(onClick = onRegenerate, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Сгенерировать заново")
                        }
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к переводу") }
                }
            }
        }
    }
    if (state.confirmRegeneration) AlertDialog(
        onDismissRequest = onDismissConfirmation,
        title = { Text("Заменить исправления?") },
        text = { Text("Новая генерация заменит ваши изменения в черновике.") },
        confirmButton = { TextButton(onClick = onConfirmRegeneration) { Text("Заменить") } },
        dismissButton = { TextButton(onClick = onDismissConfirmation) { Text("Оставить исправления") } },
    )
}
