package com.karas.qwentranslator.util

import org.junit.Assert.*
import org.junit.Test

class InvocationParserTest {
    @Test fun preservesUnicodeMultilineAndSelectionWhitespace() {
        val text = "  Hello\n世界 👋  "
        val input = InvocationParser.fromValues(InvocationSource.PROCESS_TEXT, "text/plain", text, false)!!
        assertEquals(text, input.text)
        assertTrue(input.canReplace)
    }
    @Test fun missingReadonlyDefaultsToTrue() {
        assertFalse(InvocationParser.fromValues(InvocationSource.PROCESS_TEXT, "text/plain", "text")!!.canReplace)
    }
    @Test fun shareIsAlwaysReadonly() {
        assertFalse(InvocationParser.fromValues(InvocationSource.SHARE, "text/plain", "text", false)!!.canReplace)
    }
    @Test fun rejectsNullBlankAndNonTextMime() {
        for (text in listOf(null, "", " \n\t")) assertNull(InvocationParser.fromValues(InvocationSource.PROCESS_TEXT, "text/plain", text))
        for (mime in listOf(null, "text/html", "image/png")) assertNull(InvocationParser.fromValues(InvocationSource.SHARE, mime, "text"))
    }
    @Test fun acceptsPlainTextWithCharset() {
        assertNotNull(InvocationParser.fromValues(InvocationSource.SHARE, "text/plain; charset=utf-8", "text"))
    }

    @Test fun replacementPreservesSelectionEdgesAndRejectsReadonly() {
        val input = Invocation(" \nhello\t ", false, InvocationSource.PROCESS_TEXT)
        assertEquals(" \nпривет\t ", input.replacementText("привет"))
        assertNull(input.copy(readOnly = true).replacementText("привет"))
        assertNull(input.copy(source = InvocationSource.SHARE).replacementText("привет"))
        assertNull(input.replacementText(" "))
    }
}
