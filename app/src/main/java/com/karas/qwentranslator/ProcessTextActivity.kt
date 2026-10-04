package com.karas.qwentranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.karas.qwentranslator.core.Operation
import com.karas.qwentranslator.ui.popup.PopupUiState
import com.karas.qwentranslator.util.Invocation
import com.karas.qwentranslator.ui.popup.PopupScreen
import com.karas.qwentranslator.ui.popup.ProcessTextViewModel
import com.karas.qwentranslator.ui.theme.TranslatorTheme
import com.karas.qwentranslator.util.InvocationParser

class ProcessTextActivity : ComponentActivity() {
    private var initialInvocation: Invocation? = null

    private val viewModel: ProcessTextViewModel by viewModels {
        val app = application as TranslatorApplication
        ProcessTextViewModel.Factory(
            invocation = requireNotNull(initialInvocation),
            engine = app.container.engine,
            settingsRepository = app.container.settings,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = InvocationParser.parse(intent)
        setResult(RESULT_CANCELED)
        if (input == null) {
            finish()
            return
        }

        initialInvocation = input
        setContent {
            TranslatorTheme {
                val currentInput by viewModel.invocation.collectAsStateWithLifecycle()
                val state by viewModel.state.collectAsStateWithLifecycle()
                PopupScreen(
                    sourceText = currentInput.text,
                    state = state,
                    canReplace = currentInput.canReplace,
                    onOperation = viewModel::selectOperation,
                    onCopy = ::copy,
                    onReplace = ::replace,
                    onSettings = ::openSettings,
                    onRetry = viewModel::retry,
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setResult(RESULT_CANCELED)
        val input = InvocationParser.parse(intent)
        if (input == null) finish() else viewModel.acceptInvocation(input)
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun replace(text: String) {
        val input = viewModel.invocation.value
        val result = viewModel.state.value as? PopupUiState.Success ?: return
        if (!input.canReplace || result.operation != Operation.TRANSLATE || result.text != text || text.isBlank()) return
        val replacement = input.replacementText(text) ?: return
        setResult(
            RESULT_OK,
            Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, replacement),
        )
        finish()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
