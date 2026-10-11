package com.karas.qwentranslator.network

import com.karas.qwentranslator.anki.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Free Dictionary API, English only. Never uses model IPA, transliteration or audio. */
class DictionaryIpaLookup(private val client: OkHttpClient) : IpaLookup {
    override suspend fun lookup(word: String, sourceLanguage: String): DictionaryIpa? {
        if (sourceLanguage !in listOf("auto", "en") || !Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*").matches(word)) return null
        return withTimeoutOrNull(5_000) {
            try {
                val url = "https://api.dictionaryapi.dev/api/v2/entries/en".toHttpUrl().newBuilder().addPathSegment(word).build()
                val payload = client.newCall(Request.Builder().url(url).build()).awaitPayload()
                if (payload.code !in 200..299) null else parse(payload.body, word)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
    }

    internal fun parse(body: String, word: String): DictionaryIpa? = try {
        val entries = JSONArray(body)
        val matching = (0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { it.optString("word").equals(word, ignoreCase = true) }
        val pronunciations = matching.flatMap { entry ->
            val phonetics = entry.optJSONArray("phonetics") ?: JSONArray()
            listOf(entry.optString("phonetic")) + (0 until phonetics.length()).map { phonetics.getJSONObject(it).optString("text") }
        }.map { it.trim().trim('/', '[', ']') }.filter {
            it.length in 1..98 && Regex("[a-zA-Zæðŋœ\\u0250-\\u02FF\\u0300-\\u036F .()\\-]+").matches(it)
        }.distinct()
        pronunciations.singleOrNull()?.let { text ->
            DictionaryIpa("/$text/", "Free Dictionary API")
        }
    } catch (_: Exception) { null }
}
