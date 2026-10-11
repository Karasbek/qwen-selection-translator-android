package com.karas.qwentranslator.anki

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Research gates, not a converter. Expected outcomes and candidates are manually labelled before implementation. */
class ExplainReuseFeasibilityTest {
    private fun corpus(): List<JSONObject> {
        val raw = JSONArray(File("../docs/fixtures/explain-card-quality-gate.json").readText())
        return (0 until raw.length()).map(raw::getJSONObject)
    }
    private fun candidate(case: JSONObject): CardContent {
        val annotated = case.getJSONObject("manualCandidate")
        return CardContent(case.getString("selection"), definition = annotated.getString("definition"),
            example = annotated.getString("example"))
    }
    @Test fun predefinedCorpusHasTwentyFourCasesAndEightManualLocalCandidates() {
        val cases = corpus()
        assertEquals(24, cases.size)
        assertEquals(24, cases.map { it.getString("id") }.toSet().size)
        assertEquals(8, cases.count { it.getString("expected") == "LOCAL" })
        assertEquals(16, cases.count { it.getString("expected") == "FALLBACK" })
    }
    @Test fun manuallyApprovedCandidatesProduceTwoC1InsertionsAndFiveFields() {
        corpus().filter { it.getString("expected") == "LOCAL" }.forEach { case ->
            val card = ClozeCardBuilder.build(candidate(case), "")
            assertTrue(case.getString("id"), card.errors.isEmpty())
            assertEquals(5, card.fields.size)
            assertEquals(2, Regex("\\{\\{c1::").findAll(card.fields[0]).count())
            assertFalse(card.fields[0].contains("{{c2::"))
            assertTrue(card.fields.drop(1).all { it.isEmpty() })
        }
    }
    @Test fun structuralValidatorCannotRejectWrongLanguageGrammarOrMeaning() {
        val ids = setOf("11-foreign-example", "18-wrong-grammar", "20-different-sense")
        val negatives = corpus().filter { it.getString("id") in ids }
        assertEquals(3, negatives.size)
        negatives.forEach { case ->
            assertEquals("FALLBACK", case.getString("expected"))
            // Intended counterexample: mechanically valid Cloze is insufficient for safe automatic reuse.
            assertTrue(case.getString("id"), ClozeCardBuilder.build(candidate(case), "").errors.isEmpty())
        }
    }
}
