package com.karas.qwentranslator.ui.anki

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.karas.qwentranslator.anki.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InteractiveClozeTextTest {
    @get:Rule val compose = createComposeRule()
    private val source = "Optimization is making something effective. Companies optimize production."
    private fun content() = CardContent("optimization", explanation=source, selections=ClozeRanges.initial(source,"optimization"))
    private fun position(offset: Int): Offset {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("interactive-cloze").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().getBoundingBox(offset).center
    }
    private fun tap(offset: Int) { val at=position(offset); compose.onNodeWithTag("interactive-cloze").performTouchInput { click(at) } }
    @Test fun tapsHideSuggestedAndArbitraryWordsAndRevealAgain() {
        var current by mutableStateOf(content())
        compose.setContent { MaterialTheme { InteractiveClozeText(current,true) { current=it } } }
        val optimize=source.indexOf("optimize")
        tap(optimize); assertTrue(current.selections!!.hidden.any { it.start==optimize })
        tap(optimize); assertFalse(current.selections!!.hidden.any { it.start==optimize })
        val production=source.indexOf("production")
        tap(production); assertTrue(current.selections!!.hidden.any { it.start==production })
        assertEquals(source,current.explanation)
        assertTrue(ClozeCardBuilder.build(current,"").fields[0].contains("{{c1::production}}"))
    }
    @Test fun longPressDragSelectsWholePhraseAndTapRevealsIt() {
        var current by mutableStateOf(content())
        compose.setContent { MaterialTheme { InteractiveClozeText(current,true) { current=it } } }
        val start=source.indexOf("making"); val end=source.indexOf("effective")+2
        val a=position(start); val b=position(end)
        compose.onNodeWithTag("interactive-cloze").performTouchInput {
            down(a); advanceEventTime(700); moveTo(b,300); up()
        }
        assertTrue(current.selections!!.hidden.any { source.substring(it.start,it.end)=="making something effective" })
        tap(start); assertFalse(current.selections!!.hidden.any { it.start==start })
    }
    @Test fun recompositionUsesCurrentWordAndNeverRestoresStaleContent() {
        var current by mutableStateOf(content())
        compose.setContent { MaterialTheme { InteractiveClozeText(current,true) { current=it } } }
        compose.runOnIdle { current=current.copy(word="production",selections=ClozeRanges.initial(source,"production")) }
        tap(source.indexOf("optimize"))
        assertEquals("production",current.word)
        assertTrue(current.selections!!.hidden.any { it.start==source.indexOf("production") })
    }
    @Test fun disabledEditorCannotChangeHiddenRanges() {
        var current by mutableStateOf(content())
        val initial=current
        compose.setContent { MaterialTheme { InteractiveClozeText(current,false) { current=it } } }
        tap(source.indexOf("optimize")); assertEquals(initial,current)
    }
    @Test fun darkThemeAndHeaderCanRevealAllCloze() {
        var current by mutableStateOf(CardContent("absent",explanation=source,selections=ClozeRanges.initial(source,"absent")))
        compose.setContent { MaterialTheme(colorScheme=darkColorScheme()) { InteractiveClozeText(current,true) { current=it } } }
        compose.onNodeWithText("[c1: absent] — раскрыть отдельный пропуск").performClick()
        assertFalse(current.selections!!.headerHidden)
        assertTrue(ClozeCardBuilder.build(current,"").errors.isNotEmpty())
    }
}
