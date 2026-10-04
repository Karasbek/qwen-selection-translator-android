package com.karas.qwentranslator.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karas.qwentranslator.core.Operation

@Composable
fun PopupScreen(
    sourceText: String,
    state: PopupUiState,
    canReplace: Boolean,
    onOperation: (Operation) -> Unit,
    onCopy: (String) -> Unit,
    onReplace: (String) -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .heightIn(max = maxHeight * 0.85f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(onClick = {}),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Qwen Перевод", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Настройки",
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onSettings)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    Text(
                        sourceText.take(12_001),
                        modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OperationButton(
                        label = "Перевод",
                        selected = currentOperation(state) == Operation.TRANSLATE,
                        onClick = { onOperation(Operation.TRANSLATE) },
                    )
                    OperationButton(
                        label = "Объяснить",
                        selected = currentOperation(state) == Operation.EXPLAIN,
                        onClick = { onOperation(Operation.EXPLAIN) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                when (state) {
                    is PopupUiState.Loading -> {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is PopupUiState.Success -> {
                        SelectionContainer { Text(state.text, style = MaterialTheme.typography.bodyLarge) }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${state.sourceLanguage} → ${state.targetLanguage} · ${state.provider}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { onCopy(state.text) }) {
                                Text("Копировать")
                            }
                            if (canReplace && state.operation == Operation.TRANSLATE) {
                                Button(onClick = { onReplace(state.text) }) {
                                    Text("Заменить")
                                }
                            }
                            OutlinedButton(onClick = onRetry) { Text("Повторить") }
                        }
                    }
                    is PopupUiState.Error -> {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRetry) { Text("Повторить") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun OperationButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun currentOperation(state: PopupUiState): Operation = state.operation
