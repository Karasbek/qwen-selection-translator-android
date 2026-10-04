package com.karas.qwentranslator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Qwen Перевод", style = MaterialTheme.typography.headlineMedium)
        Text(
            "После установки выдели текст в любом приложении и выбери «Перевести». " +
                "Если PROCESS_TEXT недоступен, используй «Поделиться» → Qwen Перевод.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.targetLanguage,
            onValueChange = onTargetChange,
            label = { Text("Язык перевода (код)") },
            supportingText = { Text("Например: ru, en, pl, uk, de") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Groq API key") },
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("OpenAI-compatible base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave) { Text("Сохранить") }
            if (state.saved) {
                Text(
                    "Сохранено",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("MVP", style = MaterialTheme.typography.titleMedium)
        Text(
            "Сейчас: выделение → popup, Translate, Explain, Copy, Replace, Share fallback, Qwen/Groq и Google web fallback. " +
                "История, офлайн-словари и общий Rust/UniFFI core оставлены следующим этапом, интерфейс TranslatorEngine уже изолирует UI от backend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
