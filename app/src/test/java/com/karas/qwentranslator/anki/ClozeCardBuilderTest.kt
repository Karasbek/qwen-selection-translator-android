package com.karas.qwentranslator.anki

import org.junit.Assert.*
import org.junit.Test

class ClozeCardBuilderTest {
    private fun build(word: String, definition: String = "To $word means to do something.", example: String = "They $word today.") =
        ClozeCardBuilder.build(CardContent(word, "verb", definition, example), "/əˈɡriː/")

    @Test fun agreeBuildsExactlyTwoC1InsertionsAndFiveFields() {
        val card = build("agree")
        assertTrue(card.errors.isEmpty())
        assertEquals(listOf("v. To {{c1::agree}} means to do something.<br><br>They {{c1::agree}} today.", "/əˈɡriː/", "", "", ""), card.fields)
        assertEquals(2, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
        assertFalse(card.fields[0].contains("{{c2"))
    }
    @Test fun exactFormsPhrasesContractionsAndUnicodeArePreserved() {
        for (word in listOf("agreed", "abandon", "running", "give up", "don't", "don’t", "café", "你好", "привет", "nai\u0308ve", "👩‍💻", "𐐀")) {
            val card = build(word)
            assertEquals(word, card.errors.joinToString(), "")
            val encoded = ClozeCardBuilder.escape(word)
            assertEquals(2, Regex(Regex.escape("{{c1::$encoded}}"), RegexOption.IGNORE_CASE).findAll(card.fields[0]).count())
        }
    }
    @Test fun caseInsensitivityPreservesActualSpelling() {
        val card = build("agree", "Agree means having the same opinion.", "We AGREE today.")
        assertTrue(card.fields[0].contains("{{c1::Agree}}"))
        assertTrue(card.fields[0].contains("{{c1::AGREE}}"))
    }
    @Test fun refusesLemmaAndSubstringReplacements() {
        for (word in listOf("agree", "run", "don", "cafe")) {
            val text = when (word) { "agree" -> "agreed"; "run" -> "running"; "don" -> "don't"; else -> "cafe\u0301" }
            assertTrue(build(word, "To $text means something.", "They $text today.").fields.isEmpty())
        }
        assertTrue(build("running", "To run means to move fast.", "They are running today.").fields.isEmpty())
        assertTrue(build("run", example = "A run-away horse.").fields.isEmpty())
    }
    @Test fun ambiguousOccurrencesRequireEditing() {
        assertTrue(build("agree", example = "I agree and you agree.").errors.single().contains("несколько"))
        assertTrue(build("agree", definition = "agree", example = "agree").fields.isEmpty())
    }
    @Test fun escapesHtmlQuotesBracesAndClozeInjectionBeforeWrapping() {
        val card = build("don't", "To don't means \"no\" & <script>{x}</script> {{c2::bad}}.", "They don't use a::b or 'quotes'.")
        val front = card.fields[0]
        assertFalse(front.contains("<script>"))
        assertFalse(front.contains("{{c2"))
        assertTrue(front.contains("&lt;script&gt;&#123;x&#125;&lt;/script&gt;"))
        assertTrue(front.contains("{{c1::don&#39;t}}"))
        assertTrue(front.contains("a&#58;&#58;b"))
        assertTrue(front.contains("&quot;no&quot; &amp;"))
    }
    @Test fun arbitraryTargetSpecialCharactersCannotBreakMarkup() {
        for (word in listOf("{x}", "<x>", "a::b", "\"quoted\"")) {
            val card = build(word)
            assertTrue(card.errors.isEmpty())
            assertEquals(2, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
            assertEquals(2, Regex("}}").findAll(card.fields[0]).count())
        }
    }
    @Test fun lineBreaksBecomeSafeHtmlAndUnicodeSurvives() {
        val front = build("café", "café means coffee.\r\nСмысл виден.", "We visited a café.\n世界").fields[0]
        assertTrue(front.contains("<br>Смысл виден.<br><br>"))
        assertTrue(front.endsWith("<br>世界"))
        assertFalse(front.contains('\r'))
    }
    @Test fun missingMandatoryDataAndOversizedInputDoNotProduceFields() {
        for (content in listOf(CardContent(""), CardContent("agree"), CardContent("agree", definition = "agree means yes."),
            CardContent("x".repeat(201)), CardContent("a\u0000"), CardContent("give\nup"))) {
            val card = ClozeCardBuilder.build(content, "")
            assertTrue(card.fields.isEmpty())
            assertTrue(card.errors.isNotEmpty())
        }
    }
    @Test fun blankIpaRemainsBlank() { assertEquals("", ClozeCardBuilder.build(CardContent("agree", "verb", "agree means yes.", "I agree today."), "").fields[1]) }
}
