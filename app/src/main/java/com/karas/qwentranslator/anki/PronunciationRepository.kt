package com.karas.qwentranslator.anki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Exact dictionary pronunciations only, never a generated or inferred pronunciation. */
data class Pronunciations(val variants: List<String> = emptyList(), val source: String = "ipa-dict · English (US), офлайн")
interface PronunciationRepository { suspend fun lookup(word: String, sourceLanguage: String): Pronunciations }

/** One lazy UTF-8 buffer and packed line offsets, rather than 125k String/map objects. Sorted bundled TSV. */
class OfflinePronunciationRepository(private val load: () -> ByteArray) : PronunciationRepository {
    private class Index(val bytes: ByteArray) {
        val offsets = IntArray(if (bytes.isEmpty()) 0 else bytes.count { it == '\n'.code.toByte() } + if (bytes.lastOrNull() != '\n'.code.toByte()) 1 else 0)
        init {
            var row = 0
            if (offsets.isNotEmpty()) offsets[row++] = 0
            for (i in bytes.indices) if (bytes[i] == '\n'.code.toByte() && i + 1 < bytes.size) offsets[row++] = i + 1
        }
        fun line(row: Int): String {
            val start = offsets[row]
            val end = if (row + 1 < offsets.size) offsets[row + 1] - 1 else bytes.size
            return String(bytes, start, end - start, Charsets.UTF_8).trimEnd('\r', '\n')
        }
        fun word(row: Int) = line(row).substringBefore('\t').lowercase(Locale.ROOT)
    }
    private val index by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Index(load()) }
    override suspend fun lookup(word: String, sourceLanguage: String): Pronunciations = withContext(Dispatchers.IO) {
        val language = sourceLanguage.lowercase(Locale.ROOT).replace('_', '-')
        val exact = word.trim().lowercase(Locale.ROOT)
        if (exact.isBlank() || exact.length > 200 || (language != "auto" && language != "en" && !language.startsWith("en-")))
            return@withContext Pronunciations()
        val data = index
        var low = 0; var high = data.offsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (data.word(mid) < exact) low = mid + 1 else high = mid
        }
        val variants = linkedSetOf<String>()
        var row = low
        while (row < data.offsets.size && data.word(row) == exact) {
            data.line(row++).substringAfter('\t', "").split(',').map(String::trim)
                .filter { it.length <= 200 && Regex("/[^/\\r\\n]+/").matches(it) }.forEach(variants::add)
        }
        Pronunciations(variants.toList())
    }
}
