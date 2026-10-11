package com.karas.qwentranslator.ui.anki

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import com.karas.qwentranslator.anki.*

/** Render positions match UTF-16 source offsets even when hidden characters are masked. */
@Composable
fun InteractiveClozeText(content: CardContent, enabled: Boolean, onChange: (CardContent) -> Unit) {
    val source = content.explanation ?: return
    val selections = content.selections ?: ClozeRanges.initial(source, content.word)
    val hasExact = remember(source, content.word) { ClozeRanges.exact(source, content.word).isNotEmpty() }
    val suggestions = remember(source, content.word) { ClozeRanges.suggested(source, content.word) }
    var layout by remember(source) { mutableStateOf<TextLayoutResult?>(null) }
    var anchor by remember(source) { mutableStateOf<Int?>(null) }
    var focus by remember(source) { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val pending = if (anchor != null && focus != null) ClozeRanges.phrase(source, anchor!!, focus!!) else null
    val validHidden = selections.hidden.filter { ClozeRanges.valid(source, listOf(it)) }
    val colors = MaterialTheme.colorScheme
    val shown = buildAnnotatedString {
        val display = source.toCharArray()
        validHidden.forEach { range ->
            if (ClozeRanges.valid(source, listOf(range))) for (i in range.start until range.end) if (!display[i].isWhitespace()) display[i] = '•'
        }
        append(String(display))
        suggestions.forEach { addStyle(SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer), it.start, it.end) }
        validHidden.forEach {
            addStyle(SpanStyle(background = colors.primaryContainer, color = colors.onPrimaryContainer, textDecoration = TextDecoration.Underline), it.start, it.end)
        }
        pending?.let { addStyle(SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer), it.start, it.end) }
    }
    // Mask only the presentation; source and all range offsets remain unchanged.
    val latestContent by rememberUpdatedState(content)
    val latestChange by rememberUpdatedState(onChange)
    val latestSelections by rememberUpdatedState(selections)
    if (selections.headerHidden || !hasExact) {
        androidx.compose.material3.TextButton(enabled = enabled, onClick = {
            latestChange(latestContent.copy(selections = selections.copy(headerHidden = !selections.headerHidden)))
        }) { Text(if (selections.headerHidden) "[c1: ${content.word}] — раскрыть отдельный пропуск" else "Скрыть отдельное слово: ${content.word}") }
    }
    Text(shown, style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it }, modifier = Modifier.fillMaxWidth().testTag("interactive-cloze")
            .pointerInput(source, enabled) {
                if (enabled) detectTapGestures { position ->
                    layout?.let {
                        val offset = it.getOffsetForPosition(position)
                        if (offset in source.indices && it.getBoundingBox(offset).contains(position))
                            latestChange(latestContent.copy(selections = ClozeRanges.toggleAt(source, latestSelections, offset)))
                    }
                }
            }
            .pointerInput(source, enabled) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        dragPosition = position
                        anchor = layout?.getOffsetForPosition(position)
                        focus = anchor
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragPosition += amount
                        focus = layout?.getOffsetForPosition(dragPosition)
                    },
                    onDragEnd = {
                        val a = anchor; val b = focus
                        if (a != null && b != null) ClozeRanges.phrase(source, a, b)?.let {
                            latestChange(latestContent.copy(selections = ClozeRanges.toggle(source, latestSelections, it)))
                        }
                        anchor = null; focus = null
                    },
                    onDragCancel = { anchor = null; focus = null },
                )
            })
}
