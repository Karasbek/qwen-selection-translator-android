package com.karas.qwentranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.karas.qwentranslator.anki.CardGenerationRequest
import com.karas.qwentranslator.core.ResultBlock
import com.karas.qwentranslator.ui.popup.ResultUiState
import com.karas.qwentranslator.ui.anki.AnkiDraftViewModel
import com.karas.qwentranslator.ui.anki.AnkiDraftPreview
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.karas.qwentranslator.util.Invocation
import com.karas.qwentranslator.ui.popup.PopupScreen
import com.karas.qwentranslator.ui.popup.ProcessTextViewModel
import com.karas.qwentranslator.ui.theme.TranslatorTheme
import com.karas.qwentranslator.util.InvocationParser

class ProcessTextActivity : ComponentActivity() {
    private var initialInvocation: Invocation? = null
    private var returningFromSettings = false

    private val viewModel: ProcessTextViewModel by viewModels {
        val app = application as TranslatorApplication
        ProcessTextViewModel.Factory(
            invocation = requireNotNull(initialInvocation),
            engine = app.container.engine,
            settingsRepository = app.container.settings,
        )
    }

    private val ankiDraft: AnkiDraftViewModel by viewModels {
        val container = (application as TranslatorApplication).container
        AnkiDraftViewModel.Factory(container.cardGenerator, container.ipaLookup, container.anki, container.settings, container.ankiSaver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = InvocationParser.parse(intent)
        setResult(RESULT_CANCELED)
        if (input == null) {
            finish()
            return
        }

        returningFromSettings = savedInstanceState?.getBoolean("returningFromSettings") ?: false
        initialInvocation = input
        setContent {
            TranslatorTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val draftState by ankiDraft.state.collectAsStateWithLifecycle()
                PopupScreen(
                    state = state,
                    onCopy = ::copy,
                    onReplace = ::replace,
                    onSettings = ::openSettings,
                    onRetry = viewModel::retry,
                    onDismiss = { finish() },
                    onAnki = {
                        val current = viewModel.state.value
                        val explanation = (current.blocks[ResultBlock.EXPLANATION] as? ResultUiState.Success)?.result
                        val translation = (current.blocks[ResultBlock.TRANSLATION] as? ResultUiState.Success)?.result
                        ankiDraft.open(CardGenerationRequest(current.invocation.text,
                            sourceLanguage = translation?.detectedSourceLanguage ?: current.sourceLanguage,
                            targetLanguage = current.targetLanguage,
                            explanation = explanation?.outputText, translation = translation?.outputText))
                    },
                )
                AnkiDraftPreview(draftState, ankiDraft::edit, ankiDraft::regenerate,
                    ankiDraft::confirmRegeneration, ankiDraft::dismissConfirmation,
                    ankiDraft::cancelGeneration, ankiDraft::close, ankiDraft::save, ankiDraft::confirmSave, ankiDraft::dismissSaveWarning, ankiDraft::verifySave)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setResult(RESULT_CANCELED)
        ankiDraft.close()
        val input = InvocationParser.parse(intent)
        if (input == null) finish() else viewModel.acceptInvocation(input)
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun replace(text: String) {
        val replacement = viewModel.state.value.replacementText(text) ?: return
        setResult(
            RESULT_OK,
            Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, replacement),
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (initialInvocation != null && returningFromSettings) {
            returningFromSettings = false
            viewModel.refreshSettings()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("returningFromSettings", returningFromSettings)
        super.onSaveInstanceState(outState)
    }

    private fun openSettings() {
        returningFromSettings = true
        startActivity(Intent(this, MainActivity::class.java))
    }
}
