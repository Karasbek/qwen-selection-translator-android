package com.karas.qwentranslator.network

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DictionaryIpaLookupTest {
    private val lookup = DictionaryIpaLookup(OkHttpClient())
    @Test fun exactDictionaryEntrySuppliesIpa() {
        val result = lookup.parse("""[{"word":"abandon","phonetic":"/əˈbændən/","phonetics":[{"text":"/əˈbændən/"}]}]""", "abandon")
        assertEquals("/əˈbændən/", result?.text)
        assertEquals("Free Dictionary API", result?.source)
    }
    @Test fun wrapsBareDictionaryPhoneticTextWithoutInventingPronunciation() {
        assertEquals("/əˈɡriː/", lookup.parse("""[{"word":"agree","phonetics":[{"text":"əˈɡriː"}]}]""", "agree")?.text)
    }
    @Test fun missingWrongMalformedAndAmbiguousPronunciationsStayEmpty() {
        for (body in listOf("", "oops", "[]", """[{"word":"run","phonetic":"/rʌn/"}]""",
            """[{"word":"running","phonetics":[]}]""", """[{"word":"running","phonetic":"бегать"}]""",
            """[{"word":"running","phonetics":[{"text":"/a/"},{"text":"/b/"}]}]""")) assertNull(lookup.parse(body, "running"))
    }
    @Test fun unsupportedLanguagesAndPhrasesDoNotCallNetwork() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { error("No network") }.build()
        val lookup = DictionaryIpaLookup(client)
        assertNull(lookup.lookup("give up", "en"))
        assertNull(lookup.lookup("你好", "auto"))
        assertNull(lookup.lookup("agree", "fr"))
    }
    @Test fun dictionaryUnavailableLeavesIpaEmpty() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(404).message("Missing").body("{}".toResponseBody()).build() }.build()
        assertNull(DictionaryIpaLookup(client).lookup("agreed", "en"))
    }
}
