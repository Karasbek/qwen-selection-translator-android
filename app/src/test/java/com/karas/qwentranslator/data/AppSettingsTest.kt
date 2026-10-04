package com.karas.qwentranslator.data

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    @Test fun defaultsAndTrailingSlashGiveCorrectEndpoint() {
        assertNull(AppSettings().validationError())
        assertEquals("https://api.groq.com/openai/v1/chat/completions", AppSettings(baseUrl = "https://api.groq.com/openai/v1/").endpoint().toString())
    }
    @Test fun rejectsUnsafeUrls() {
        for (url in listOf("bad", "http://example.org/v1", "https://user:password@example.org/v1", "https://example.org/v1?key=abc", "https://example.org/v1#secret")) {
            assertNotNull(AppSettings(baseUrl = url).validationError())
        }
    }
    @Test fun validatesLanguageAndNormalizesEmptyFields() {
        assertNotNull(AppSettings(targetLanguage = "Russian").validationError())
        assertNull(AppSettings(targetLanguage = "zh-CN").validationError())
        assertEquals(AppSettings(), AppSettings(" ", " ", " ").normalized())
    }
}
