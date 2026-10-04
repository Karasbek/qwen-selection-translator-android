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
import com.karas.qwentranslator.ui.popup.PopupScreen
import com.karas.qwentranslator.ui.popup.ProcessTextViewModel
import com.karas.qwentranslator.ui.popup.PopupUiState
import com.karas.qwentranslator.ui.theme.TranslatorTheme
import com.karas.qwentranslator.util.InvocationParser
import com.karas.qwentranslator.util.InvocationSource

class ProcessTextActivity : ComponentActivity() {
    private val invocation by lazy { InvocationParser.parse(intent) }

    private val viewModel: ProcessTextViewModel by viewModels {
        val app = application as TranslatorApplication
        ProcessTextViewModel.Factory(
            invocation = requireNotNull(invocation),
            engine = app.container.engine,
            settingsRepository = app.container.settings,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = invocation
        if (input == null) {
            finish()
            return
        }

        setContent {
            TranslatorTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                PopupScreen(
                    sourceText = input.text,
                    state = state,
                    canReplace = input.source == InvocationSource.PROCESS_TEXT && !input.readOnly,
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

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun replace(text: String) {
        val input = invocation ?: return
        if (input.source != InvocationSource.PROCESS_TEXT || input.readOnly) return
        setResult(
            RESULT_OK,
            Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text),
        )
        finish()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
