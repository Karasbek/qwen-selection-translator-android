package com.karas.qwentranslator.ui.popup

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.karas.qwentranslator.core.*
import com.karas.qwentranslator.util.*

private fun previewState() = PopupUiState(Invocation("The results should be considered together with the study's limitations.", true, InvocationSource.PROCESS_TEXT),
    blocks = mapOf(ResultBlock.EXPLANATION to ResultUiState.Success(TranslationResult(
        "This means the findings may not be completely reliable. Consider what the study could not cover.", "Qwen", operation = Operation.EXPLAIN)),
        ResultBlock.TRANSLATION to ResultUiState.Success(TranslationResult(
            "Результаты следует рассматривать вместе с ограничениями исследования.", "Qwen", "en", Operation.TRANSLATE))))

@Composable private fun PreviewContent(state: PopupUiState, dark: Boolean = false) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        PopupScreen(state, {}, {}, {}, {}, {})
    }
}
@Preview(widthDp = 360, heightDp = 760) @Composable private fun ReadingPopup() = PreviewContent(previewState())
@Preview(widthDp = 320, heightDp = 640) @Composable private fun PartialSuccess() = PreviewContent(previewState().copy(
    blocks = previewState().blocks + (ResultBlock.TRANSLATION to ResultUiState.Loading)))
@Preview(widthDp = 360, heightDp = 760) @Composable private fun TwoErrors() = PreviewContent(previewState().copy(
    blocks = mapOf(ResultBlock.EXPLANATION to ResultUiState.Error(TranslationFailure(FailureKind.AUTHENTICATION, "Groq")),
        ResultBlock.TRANSLATION to ResultUiState.Error(TranslationFailure(FailureKind.NETWORK)))))
@Preview(widthDp = 720, heightDp = 360) @Composable private fun LongLandscapeDark() = PreviewContent(previewState().copy(
    invocation = Invocation("Long source text. ".repeat(60), false, InvocationSource.PROCESS_TEXT),
    blocks = mapOf(ResultBlock.EXPLANATION to ResultUiState.Success(TranslationResult("Long explanation. ".repeat(60), "Qwen", operation = Operation.EXPLAIN)),
        ResultBlock.TRANSLATION to ResultUiState.Success(TranslationResult("Длинный перевод. ".repeat(60), "Qwen", operation = Operation.TRANSLATE)))), true)
@Preview(widthDp = 360, heightDp = 640) @Composable private fun OneBlock() = PreviewContent(previewState().copy(
    invocation = Invocation("Hello", true, InvocationSource.TRANSLATION_LINK),
    blocks = previewState().blocks + (ResultBlock.EXPLANATION to ResultUiState.Disabled)))
