package com.karas.qwentranslator.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karas.qwentranslator.core.*

fun ResultBlock.title(): String = when (this) {
    ResultBlock.EXPLANATION -> "Объяснение"
    ResultBlock.TRANSLATION -> "Перевод"
}
fun languageLabel(code: String): String = when (code.lowercase()) {
    "auto" -> "Авто"
    "en" -> "English"
    "ru" -> "Русский"
    else -> code
}

@Composable
fun PopupScreen(
    state: PopupUiState,
    onCopy: (String) -> Unit,
    onReplace: (String) -> Unit,
    onSettings: () -> Unit,
    onRetry: (ResultBlock) -> Unit,
    onDismiss: () -> Unit,
    onAnki: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f))
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                .widthIn(max = 620.dp).fillMaxWidth()
                .heightIn(max = (maxHeight * 0.85f).coerceAtMost(640.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SelectionContainer {
                            Text(state.invocation.text.take(12_001),
                                modifier = Modifier.heightIn(max = 96.dp).verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        val detected = (state.blocks[ResultBlock.TRANSLATION] as? ResultUiState.Success)
                            ?.result?.detectedSourceLanguage ?: state.sourceLanguage
                        Text("${languageLabel(detected)} → ${languageLabel(state.targetLanguage)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "Меню перевода" }) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Настройки") }, onClick = { menuOpen = false; onSettings() })
                            DropdownMenuItem(text = { Text(if (showInfo) "Скрыть сервис" else "Информация о сервисе") },
                                onClick = { menuOpen = false; showInfo = !showInfo })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.resultOrder.forEach { block ->
                        val result = state.blocks[block] ?: ResultUiState.Disabled
                        if (result != ResultUiState.Disabled) ResultCard(block, result, showInfo,
                            state.invocation.canReplace, onCopy, onReplace, { onRetry(block) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAnki, modifier = Modifier.fillMaxWidth()) { Text("В Anki") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun ResultCard(
    block: ResultBlock, state: ResultUiState, showInfo: Boolean, canReplace: Boolean,
    onCopy: (String) -> Unit, onReplace: (String) -> Unit, onRetry: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.title(), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
                if (state is ResultUiState.Success) {
                    TextButton(onClick = { onCopy(state.result.outputText) },
                        modifier = Modifier.semantics { contentDescription = "Копировать: ${block.title()}" }) { Text("Копировать") }
                }
            }
            when (state) {
                ResultUiState.Loading -> {
                    Text("Загрузка…", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Загрузка: ${block.title()}" })
                }
                is ResultUiState.Success -> {
                    SelectionContainer { Text(state.result.outputText, style = MaterialTheme.typography.bodyLarge) }
                    if (showInfo) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.result.providerId, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (block == ResultBlock.TRANSLATION && canReplace && state.result.operation == Operation.TRANSLATE) {
                        TextButton(onClick = { onReplace(state.result.outputText) }) { Text("Заменить выделенный текст") }
                    }
                }
                is ResultUiState.Error -> {
                    Text(FailurePresentation.message(state.failure), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = "Повторить: ${block.title()}" }) {
                        Text("Повторить")
                    }
                }
                ResultUiState.Disabled -> Unit
            }
        }
    }
}
