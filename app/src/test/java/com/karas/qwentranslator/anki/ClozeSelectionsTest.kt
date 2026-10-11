package com.karas.qwentranslator.anki

import org.junit.Assert.*
import org.junit.Test

class ClozeSelectionsTest {
    private val text = "Optimization is making something effective.\nExample: Companies optimize their production processes."
    private fun card(word: String, text: String, selected: ClozeSelections? = null) = ClozeCardBuilder.build(CardContent(word, explanation = text, selections = selected), "/ipa/")
    @Test fun optimizationIsExactAndOptimizeOnlySuggested() {
        val initial = ClozeRanges.initial(text, "optimization")
        assertFalse(initial.headerHidden); assertEquals(listOf(ClozeRange(0,12)), initial.hidden)
        assertEquals(listOf("optimize"), ClozeRanges.suggested(text,"optimization").map { text.substring(it.start,it.end) })
        assertFalse(card("optimization",text).fields[0].contains("{{c1::optimize}}"))
        val hidden = ClozeRanges.toggle(text,initial,ClozeRanges.suggested(text,"optimization").single())
        assertTrue(card("optimization",text,hidden).fields[0].contains("{{c1::optimize}}"))
        assertEquals(initial,ClozeRanges.toggle(text,hidden,hidden.hidden.last()))
    }
    @Test fun derivationalSuggestionsNeverChangeCard() {
        for ((word,form) in listOf("create" to "creation", "decide" to "decision", "optimize" to "optimized")) {
            val body = "$word is a word. $form is a possible form."
            assertEquals(listOf(form),ClozeRanges.suggested(body,word).map { body.substring(it.start,it.end) })
            assertFalse(card(word,body).fields[0].contains("{{c1::$form}}"))
        }
    }
    @Test fun allExactCasesAreHiddenAndSubstringsExcluded() {
        val body = "Run, RUN and run. running outrun run's pre-run."
        assertEquals(3,ClozeRanges.initial(body,"run").hidden.size)
        assertEquals(3,Regex("\\{\\{c1::").findAll(card("run",body).fields[0]).count())
    }
    @Test fun unicodeAndApostrophesHaveStableOffsets() {
        val body = "𝒶run ruń don't DON'T. 你好!"
        assertTrue(ClozeRanges.exact(body,"run").isEmpty())
        assertEquals(2,ClozeRanges.exact(body,"don't").size)
        assertEquals("你好",body.substring(ClozeRanges.exact(body,"你好").single().start,ClozeRanges.exact(body,"你好").single().end))
        assertFalse(ClozeRanges.valid("𝒶",listOf(ClozeRange(0,1))))
    }
    @Test fun fallbackHeaderOnlyWhenNoExactMatchAndCanBeRemoved() {
        val initial = ClozeRanges.initial("To share an opinion.","agree")
        assertTrue(initial.headerHidden)
        assertTrue(card("agree","To share an opinion.",initial).fields[0].startsWith("{{c1::agree}}"))
        assertTrue(card("agree","To share an opinion.",initial.copy(headerHidden=false)).errors.isNotEmpty())
    }
    @Test fun arbitraryWordAndTapToRevealWholePhrase() {
        val body = "We give up after several attempts."
        val initial = ClozeRanges.initial(body,"give up")
        assertEquals(listOf(ClozeRange(3,10)),initial.hidden)
        val revealed = ClozeRanges.toggleAt(body,initial,8)
        assertTrue(revealed.hidden.isEmpty()); assertTrue(card("give up",body,revealed).errors.isNotEmpty())
        val arbitrary = ClozeRanges.toggleAt(body,revealed,body.indexOf("attempts"))
        assertTrue(card("give up",body,arbitrary).fields[0].contains("{{c1::attempts}}"))
    }
    @Test fun longSelectionSnapsToWholeWordsAndReplacesOverlaps() {
        val body = "We give up after several attempts."
        val phrase = ClozeRanges.phrase(body,4,22)!!
        assertEquals("give up after several",body.substring(phrase.start,phrase.end))
        val initial = ClozeRanges.initial(body,"give up")
        assertEquals(listOf(phrase),ClozeRanges.toggle(body,initial,phrase).hidden)
    }
    @Test fun malformedOverlappingDuplicateAndOutOfBoundsRangesRejected() {
        for (ranges in listOf(listOf(ClozeRange(-1,4)),listOf(ClozeRange(0,999)),listOf(ClozeRange(0,5),ClozeRange(3,8)),listOf(ClozeRange(0,5),ClozeRange(0,5)))) {
            assertTrue(card("optimization",text,ClozeSelections(ranges)).errors.isNotEmpty())
        }
    }
    @Test fun htmlAndAllClozeLikeInputEscapedWithOneOrdinal() {
        val body = "<b>Optimization</b> & optimize {{c2::other}}."
        val selections = ClozeRanges.toggleAt(body,ClozeRanges.initial(body,"optimization"),body.indexOf("optimize"))
        val fields = card("optimization",body,selections).fields
        assertTrue(fields[0].contains("&lt;b&gt;{{c1::Optimization}}&lt;/b&gt;"))
        assertFalse(fields[0].contains("{{c2::")); assertEquals(listOf("/ipa/","","",""),fields.drop(1))
        assertEquals(setOf("1"),Regex("\\{\\{c(\\d+)::").findAll(fields[0]).map { it.groupValues[1] }.toSet())
    }
}
