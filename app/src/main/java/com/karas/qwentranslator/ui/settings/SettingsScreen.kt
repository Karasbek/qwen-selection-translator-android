package com.karas.qwentranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTargetChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Qwen Перевод", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Выдели текст и выбери «Qwen перевод». Пункт может находиться в меню ⋮: " +
                "прокрути список действий вниз. Если приложение не показывает переводчики, " +
                "используй «Поделиться» → «Qwen перевод».",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Можно добавить плитку «Qwen перевод» через редактирование быстрых настроек. " +
                "Скопируй текст и нажми плитку — откроется перевод. Буфер читается только после нажатия.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.targetLanguage,
            onValueChange = onTargetChange,
            label = { Text("Язык перевода (код)") },
            supportingText = { Text("Например: ru, en, pl, uk, de") },
            singleLine = true,
            enabled = !state.loading && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Groq API key") },
            singleLine = true,
            enabled = !state.loading && !state.saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("Хранится зашифрованно через Android Keystore. Для Translate есть web-fallback; Explain требует Qwen.") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = onModelChange,
            label = { Text("Модель") },
            singleLine = true,
            enabled = !state.loading && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("OpenAI-compatible base URL") },
            singleLine = true,
            enabled = !state.loading && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, enabled = !state.loading && !state.saving) {
                Text(if (state.saving) "Сохранение…" else "Сохранить")
            }
            if (state.saved) {
                Text(
                    "Сохранено",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) Text("Загрузка настроек…")
        Text(
            "Explain объясняет текст на его исходном языке. Пустой API key удаляет сохранённый ключ. " +
                "Выделенный текст передаётся выбранному AI сервису; при отказе Translate использует Google web fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
