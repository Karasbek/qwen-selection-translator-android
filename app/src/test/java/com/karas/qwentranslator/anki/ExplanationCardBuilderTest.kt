package com.karas.qwentranslator.anki

import org.junit.Assert.*
import org.junit.Test

class ExplanationCardBuilderTest {
    private fun build(word: String, text: String) = ClozeCardBuilder.build(CardContent(word, explanation = text), "")
    @Test fun preservesMeaningAndExistingExampleAsOneBody() {
        val card = build("abandon", "To leave something behind.\n\nExample: They abandon the project.")
        assertEquals("To leave something behind.<br><br>Example&#58; They {{c1::abandon}} the project.", card.fields[0])
        assertEquals(5, card.fields.size); assertTrue(card.fields.drop(1).all { it.isEmpty() })
    }
    @Test fun explanationWithoutExampleNeedsNoGeneratedExample() {
        assertEquals("{{c1::agree}}<br><br>To share the same opinion.", build("agree", "To share the same opinion.").fields[0])
    }
    @Test fun hidesEveryExactRepeatRegardlessOfCaseAndQuotes() {
        val card = build("agree", "'Agree' means sharing an opinion. We AGREE; they agree.")
        assertEquals(3, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
        assertTrue(card.fields[0].contains("{{c1::AGREE}}"))
        assertFalse(card.fields[0].contains("{{c2::"))
    }
    @Test fun wordsAndInflectionsAreNotReplacedMechanically() {
        val card = build("run", "We run. They are running. A runner arrived. We outrun him.")
        assertTrue(card.fields[0].contains("{{c1::run}}")); assertTrue(card.fields[0].contains("running"))
        assertTrue(card.fields[0].contains("runner")); assertTrue(card.fields[0].contains("outrun"))
        assertTrue(card.warnings.isNotEmpty())
    }
    @Test fun apostrophesPossessivesAndHyphensRespectWordBoundaries() {
        val card = build("don't", "'don't' means do not. I DON'T like it. don't-care is a compound.")
        assertEquals(2, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
        assertTrue(card.fields[0].contains("don&#39;t-care"))
        assertTrue(build("agree", "We agree. agree's spelling and pre-agree are visible variants.").warnings.isNotEmpty())
    }
    @Test fun phrasesAreHiddenAsExactUnits() {
        val card = build("give up", "To stop trying. They GIVE UP. We give up later.")
        assertEquals(2, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
    }
    @Test fun unicodeAndCombiningMarksArePreserved() {
        assertTrue(build("你好", "Приветствие. 你好!").fields[0].contains("{{c1::你好}}"))
        val card = build("run", "An example: 𝒶run and ruń are larger words; run is separate.")
        assertEquals(1, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
    }
    @Test fun markupAndClozeInjectionAreEscapedBeforeInsertion() {
        val card = build("agree", "<b>agree</b> & {{c2::meaning}} : \"quotes\"")
        assertTrue(card.fields[0].contains("&lt;b&gt;{{c1::agree}}&lt;/b&gt;"))
        assertFalse(card.fields[0].contains("{{c2::")); assertTrue(card.fields[0].contains("&#123;"))
    }
    @Test fun missingMeaningEmptyExplainAndBadWordRemainInvalid() {
        assertTrue(build("agree", "agree AGREE").errors.isNotEmpty())
        assertTrue(build("agree", "").errors.isNotEmpty())
        assertTrue(build("a\nb", "Meaning.").errors.isNotEmpty())
        assertTrue(build("agree", "bad\u0000text").errors.isNotEmpty())
    }
}
