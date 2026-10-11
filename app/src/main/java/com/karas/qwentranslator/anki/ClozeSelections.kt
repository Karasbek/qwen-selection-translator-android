package com.karas.qwentranslator.anki

import java.util.Locale
import java.util.regex.Pattern

/** UTF-16 offsets in the unchanged Explain, end exclusive. Never offsets in rendered HTML. */
data class ClozeRange(val start: Int, val end: Int)
data class ClozeSelections(val hidden: List<ClozeRange>, val headerHidden: Boolean = false)

object ClozeRanges {
    private val words = Regex("[\\p{L}\\p{N}\\p{M}_]+(?:['’\\-][\\p{L}\\p{N}\\p{M}_]+)*")
    fun words(text: String): List<ClozeRange> = words.findAll(text).map { ClozeRange(it.range.first, it.range.last + 1) }.toList()
    fun exact(text: String, word: String): List<ClozeRange> {
        if (word.isBlank()) return emptyList()
        val interiors = BooleanArray(text.length + 1)
        words(text).forEach { token -> for (i in token.start + 1 until token.end) interiors[i] = true }
        return Regex(Pattern.quote(word.trim()), RegexOption.IGNORE_CASE).findAll(text).map {
            ClozeRange(it.range.first, it.range.last + 1)
        }.filter { range -> !interiors[range.start] && !interiors[range.end] }.toList()
    }
    fun initial(text: String, word: String): ClozeSelections = exact(text, word).let { ClozeSelections(it, it.isEmpty()) }
    fun valid(text: String, ranges: List<ClozeRange>): Boolean {
        val sorted = ranges.sortedBy { it.start }
        return sorted.all { it.start >= 0 && it.end <= text.length && it.start < it.end &&
            !text.substring(it.start, it.end).isBlank() && boundary(text, it.start) && boundary(text, it.end) } &&
            sorted.zipWithNext().all { (a, b) -> a.end <= b.start }
    }
    private fun boundary(text: String, offset: Int): Boolean = offset == 0 || offset == text.length ||
        !(Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) &&
        Character.getType(text.codePointAt(offset)) !in listOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())
    fun at(text: String, offset: Int): ClozeRange? = words(text).firstOrNull { offset in it.start until it.end }
    fun phrase(text: String, anchor: Int, focus: Int): ClozeRange? {
        val a = at(text, anchor) ?: return null
        val b = at(text, focus) ?: return null
        return ClozeRange(minOf(a.start, b.start), maxOf(a.end, b.end))
    }
    fun toggle(text: String, selections: ClozeSelections, range: ClozeRange): ClozeSelections {
        if (!valid(text, listOf(range)) || !valid(text, selections.hidden)) return selections
        if (range in selections.hidden) return selections.copy(hidden = selections.hidden - range)
        // A phrase deliberately replaces intersecting ranges; the result never overlaps.
        return selections.copy(hidden = (selections.hidden.filterNot { it.start < range.end && range.start < it.end } + range).sortedBy { it.start })
    }
    fun toggleAt(text: String, selections: ClozeSelections, offset: Int): ClozeSelections {
        val range = selections.hidden.firstOrNull { offset in it.start until it.end } ?: at(text, offset) ?: return selections
        return toggle(text, selections, range)
    }
    /** Suggestions only. Common English derivational endings, not a linguistic assertion. */
    fun suggested(text: String, word: String): List<ClozeRange> {
        fun stem(value: String): String {
            var v = value.lowercase(Locale.ROOT)
            if (v.endsWith("ision") && v.length >= 8) return v.dropLast(5) + "id"
            for (suffix in listOf("ization", "isation", "ation", "ition", "ision", "ized", "ised", "izing", "ising", "ize", "ise", "ion", "ing", "ed", "e", "s")) {
                if (v.endsWith(suffix) && v.length - suffix.length >= 4) { v = v.dropLast(suffix.length); break }
            }
            return v
        }
        if (!Regex("[a-zA-Z]+").matches(word)) return emptyList()
        val base = stem(word)
        return words(text).filter { val token = text.substring(it.start, it.end)
            !token.equals(word, true) && Regex("[a-zA-Z]+").matches(token) && stem(token) == base }
    }
}
