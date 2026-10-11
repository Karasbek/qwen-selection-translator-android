package com.karas.qwentranslator.ui.settings

import com.karas.qwentranslator.anki.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.ui.popup.title

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTargetChange: (String) -> Unit, onSourceChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit, onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit, onExplanationChange: (ExplanationOptions) -> Unit,
    onBlockEnabled: (ResultBlock, Boolean) -> Unit, onMoveBlock: (ResultBlock, Int) -> Unit,
    onSave: () -> Unit,
    onAnkiRefresh: () -> Unit = {},
    onAnkiPermission: () -> Unit = {},
    onAnkiDeck: (Long?) -> Unit = {},
    onAnkiModel: (Long?) -> Unit = {},
    diagnostics: ApiDiagnostics = ApiDiagnostics(),
) {
    val editable = !state.loading && !state.saving
    var showDiagnostics by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
        Text("Настройки чтения", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSection("Результаты") {
                Text("Появляются автоматически в выбранном порядке. Оставьте хотя бы один блок включённым.",
                    style = MaterialTheme.typography.bodyMedium)
                state.resultOrder.forEachIndexed { index, block ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(block.title(), style = MaterialTheme.typography.titleSmall)
                            Text("${index + 1} в списке", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onMoveBlock(block, -1) }, enabled = editable && index > 0,
                            modifier = Modifier.semantics { contentDescription = "Вверх: ${block.title()}" }) { Text("↑") }
                        TextButton(onClick = { onMoveBlock(block, 1) }, enabled = editable && index < state.resultOrder.lastIndex,
                            modifier = Modifier.semantics { contentDescription = "Вниз: ${block.title()}" }) { Text("↓") }
                        Switch(checked = block in state.enabledBlocks,
                            onCheckedChange = { onBlockEnabled(block, it) },
                            enabled = editable && (block !in state.enabledBlocks || state.enabledBlocks.size > 1),
                            modifier = Modifier.semantics { contentDescription = "Включить: ${block.title()}" })
                    }
                }
            }
            SettingsSection("Anki") {
                val anki = state.anki
                val status = anki.availability.status
                Text(if (anki.loading) "Проверка подключения…" else when (status) {
                    AnkiStatus.NOT_INSTALLED -> "AnkiDroid не установлен"
                    AnkiStatus.API_UNAVAILABLE -> "Подключение недоступно. Проверьте, включён ли доступ для других приложений в AnkiDroid."
                    AnkiStatus.PERMISSION_REQUIRED -> "Нужно разрешить доступ к AnkiDroid"
                    AnkiStatus.CONNECTED -> "Подключено к AnkiDroid ${anki.availability.version.orEmpty()}"
                    AnkiStatus.ERROR -> "Не удалось прочитать коллекцию. Откройте AnkiDroid и повторите проверку."
                })
                Text("Qwen читает только названия колод, типов заметок и полей. Заметки и шаблоны не изменяются.",
                    style = MaterialTheme.typography.bodySmall)
                if (status == AnkiStatus.PERMISSION_REQUIRED) {
                    Text("AnkiDroid использует общее разрешение на чтение и запись коллекции даже для чтения. На этом этапе Qwen ничего не записывает.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onAnkiPermission, enabled = editable && !anki.loading) { Text("Разрешить доступ к AnkiDroid") }
                }
                TextButton(onClick = onAnkiRefresh, enabled = editable && !anki.loading) { Text("Проверить подключение") }
                if (status == AnkiStatus.CONNECTED && !anki.loading) {
                    if (anki.decks.isEmpty()) Text("В коллекции нет доступных колод")
                    if (anki.deckMissing(state.ankiDeckId)) Text("Ранее выбранная колода недоступна. Выберите колоду вручную.")
                    Choice("Колода", state.ankiDeckId,
                        listOf(null to "Не выбрана") + anki.decks.map { it.id to it.name }, editable, onAnkiDeck)
                    if (anki.models.isEmpty()) Text("В коллекции нет доступных типов заметок")
                    if (anki.modelMissing(state.ankiModelId)) Text("Ранее выбранный тип заметки недоступен. Выберите тип вручную.")
                    Choice("Тип заметки", state.ankiModelId,
                        listOf(null to "Не выбран") + anki.models.map { it.id to it.name }, editable, onAnkiModel)
                    anki.models.firstOrNull { it.id == state.ankiModelId }?.let { model ->
                        Text("Поля: " + model.fields.joinToString(", "))
                        Text(when {
                            !model.isCloze -> "Несовместимо: нужен тип заметки Cloze"
                            !model.compatible -> "Cloze: поля не соответствуют шаблону. Ожидаются: " + AnkiModel.expectedFields.joinToString(", ")
                            else -> "Совместимо с вашим Cloze: все пять полей совпадают"
                        })
                    }
                    Text("Выбор применяется кнопкой «Сохранить».", style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsSection("Объяснение") {
                Choice("Язык объяснения", state.explanation.language, listOf(
                    ExplanationLanguage.ENGLISH to "English",
                    ExplanationLanguage.SAME_AS_SOURCE to "Как в исходном тексте",
                    ExplanationLanguage.RUSSIAN to "Русский"), editable) {
                    onExplanationChange(state.explanation.copy(language = it))
                }
                Choice("Подробность", state.explanation.detail, listOf(
                    ExplanationDetail.SIMPLE to "Просто и коротко",
                    ExplanationDetail.NORMAL to "Обычная",
                    ExplanationDetail.DETAILED to "Подробнее"), editable) {
                    onExplanationChange(state.explanation.copy(detail = it))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Пример")
                        Text("Не более одного короткого примера", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.explanation.examples,
                        onCheckedChange = { onExplanationChange(state.explanation.copy(examples = it)) }, enabled = editable,
                        modifier = Modifier.semantics { contentDescription = "Пример в объяснении" })
                }
            }
            SettingsSection("Перевод") {
                Choice("Исходный язык", state.sourceLanguage,
                    (listOf("auto" to "Автоопределение", "en" to "English", "ru" to "Русский") +
                        if (state.sourceLanguage !in listOf("auto", "en", "ru")) listOf(state.sourceLanguage to state.sourceLanguage) else emptyList()),
                    editable, onSourceChange)
                OutlinedTextField(state.targetLanguage, onTargetChange, label = { Text("Язык перевода") },
                    supportingText = { Text("Код языка: ru — русский, en — английский, de — немецкий") },
                    singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
            }
            SettingsSection("AI сервис") {
                Text("Groq / Qwen", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(state.apiKey, onApiKeyChange, label = { Text("API key") },
                    singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Ключ хранится зашифрованно. Пустое поле удаляет ключ.") })
                OutlinedTextField(state.model, onModelChange, label = { Text("Модель") },
                    singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Скрыть адрес сервиса" else "Адрес сервиса…") }
                if (advanced) OutlinedTextField(state.baseUrl, onBaseUrlChange, label = { Text("HTTPS base URL") },
                    singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
                Text("Выделенный текст отправляется выбранному сервису. При отказе AI перевод пробует Google web; объяснение требует AI ключ.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSection("Диагностика") {
                TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Text(if (showDiagnostics) "Скрыть расход Groq" else "Расход Groq")
                }
                if (showDiagnostics) {
                    Text("С начала процесса приложения. Только числа; после перезапуска счётчики сбрасываются.", style = MaterialTheme.typography.bodySmall)
                    diagnostics.operations.forEach { (operation, counts) ->
                        Text(operation.label, style = MaterialTheme.typography.titleSmall)
                        Text("Начато: ${counts.started} · HTTP попытки: ${counts.httpAttempts} · В работе: ${counts.pending}")
                        Text("Успешно: ${counts.success} · Ошибки: ${counts.errors} · Отменено: ${counts.cancelled}")
                        Text("Input: ${counts.prompt.display()} · Output: ${counts.completion.display()}")
                        Text("Всего: ${counts.total.display()} · Cached: ${counts.cached.display()}")
                    }
                    Text("Токены суммируются только там, где сервис вернул usage. Неизвестный расход не считается нулевым.", style = MaterialTheme.typography.bodySmall)
                }
            }
            SettingsSection("Выделение текста") {
                Text("Выделите текст → Qwen перевод. Если пункта нет в первом ряду, откройте ⋮ и прокрутите список действий.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Дополнительные способы: Поделиться → Qwen перевод; или явно скопировать текст и нажать плитку Qwen в быстрых настройках.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, enabled = editable) { Text(if (state.saving) "Сохранение…" else "Сохранить") }
            if (state.saved) Text("Сохранено", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun <T> Choice(label: String, selected: T, options: List<Pair<T, String>>, enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium)
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "Недоступен — выберите вручную")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}
