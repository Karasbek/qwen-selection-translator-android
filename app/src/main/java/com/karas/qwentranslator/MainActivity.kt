package com.karas.qwentranslator

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.karas.qwentranslator.ui.settings.SettingsScreen
import com.karas.qwentranslator.ui.settings.SettingsViewModel
import com.karas.qwentranslator.ui.theme.TranslatorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        val app = application as TranslatorApplication
        SettingsViewModel.Factory(app.container.settings, app.container.secrets)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            TranslatorTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        state = state,
                        onTargetChange = viewModel::updateTarget,
                        onBaseUrlChange = viewModel::updateBaseUrl,
                        onModelChange = viewModel::updateModel,
                        onApiKeyChange = viewModel::updateApiKey,
                        onSave = viewModel::save,
                    )
                }
            }
        }
    }
}
