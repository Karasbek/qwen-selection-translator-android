package com.karas.qwentranslator.util

import org.junit.Assert.*
import org.junit.Test

class TranslationLinkParserTest {
    @Test fun legacyLinkPreservesDecodedUnicodeMultilineSlashesAndPlus() {
        val text = "  Hello/世界 👋\nC++  "
        val result = TranslationLinkParser.parse("http", "translate.google.com", "/", null, "auto/en/$text")!!
        assertEquals(text, result.text)
        assertEquals(InvocationSource.TRANSLATION_LINK, result.source)
        assertTrue(result.readOnly)
        assertFalse(result.canReplace)
        assertNull(result.replacementText("translation"))
    }

    @Test fun modernQueryTextTakesPrecedenceOverFragment() {
        assertEquals("Hello world", TranslationLinkParser.parse("https", "translate.google.com", "/",
            "Hello world", "auto/en/other")!!.text)
    }

    @Test fun rejectsMissingEmptyAndMalformedText() {
        for (fragment in listOf(null, "", "auto/en", "auto/en/", "auto/en/ \n", "bogus/en/Hello", "auto//Hello")) {
            assertNull(TranslationLinkParser.parse("https", "translate.google.com", "/", null, fragment))
        }
        assertNull(TranslationLinkParser.parse("https", "translate.google.com", "/", " ", "auto/en/Hello"))
    }

    @Test fun rejectsOtherSchemesHostsAndPaths() {
        for (scheme in listOf(null, "file", "javascript", "qwen")) {
            assertNull(TranslationLinkParser.parse(scheme, "translate.google.com", "/", "Hello", null))
        }
        for (host in listOf(null, "google.com", "translate.google.com.evil.example")) {
            assertNull(TranslationLinkParser.parse("https", host, "/", "Hello", null))
        }
        assertNull(TranslationLinkParser.parse("https", "translate.google.com", "/other", "Hello", null))
    }
}
