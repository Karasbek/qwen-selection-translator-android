package com.karas.qwentranslator.anki

import java.util.regex.Pattern

/** Plain text from the generator or the editor; never model-produced HTML or Cloze. */
data class CardContent(val word: String, val partOfSpeech: String = "", val definition: String = "", val example: String = "", val explanation: String? = null, val selections: ClozeSelections? = null)
data class CardBuild(val fields: List<String> = emptyList(), val errors: List<String> = emptyList(), val warnings: List<String> = emptyList())
data class DraftValidation(val errors: List<String>, val warnings: List<String> = emptyList()) { val valid: Boolean get() = errors.isEmpty() }
data class AnkiCardDraft(
    val originalSelection: String,
    val content: CardContent,
    val ipa: String,
    val deckId: Long?,
    val modelId: Long?,
    val fields: List<String>,
    val validation: DraftValidation,
    val operationId: String = java.util.UUID.randomUUID().toString(),
) {
    companion object {
        fun build(selection: String, content: CardContent, ipa: String, deckId: Long?, modelId: Long?, metadata: AnkiMetadata): AnkiCardDraft {
            val card = ClozeCardBuilder.build(content, ipa)
            val errors = card.errors.toMutableList()
            if (selection.isBlank()) errors += "Исходное выделение пустое"
            if (metadata.availability.status != AnkiStatus.CONNECTED) errors += "Нет доступа к AnkiDroid. Проверьте подключение в настройках."
            else {
                when {
                    deckId == null -> errors += "Выберите колоду в настройках Qwen"
                    metadata.decks.any { it.id == deckId && it.filtered } -> errors += "Выберите обычную колоду вместо фильтрованной"
                    metadata.deckMissing(deckId) -> errors += "Выбранная колода больше недоступна. Выберите её заново в настройках."
                }
                val model = metadata.models.firstOrNull { it.id == modelId }
                when {
                    modelId == null -> errors += "Выберите тип заметки в настройках Qwen"
                    model == null -> errors += "Выбранный тип заметки больше недоступен. Выберите его заново в настройках."
                    !model.compatible -> errors += "Нужен Cloze с полями Front, Pinyin, Дополнение оборота, Image, Sound"
                }
            }
            return AnkiCardDraft(selection, content, ipa, deckId, modelId, card.fields, DraftValidation(errors, card.warnings))
        }
    }
}

object ClozeCardBuilder {
    fun build(content: CardContent, ipa: String): CardBuild {
        if (content.explanation != null) return buildExplanation(content.word.trim(), content.explanation, ipa, content.selections)
        val word = content.word.trim()
        val errors = mutableListOf<String>()
        if (word.isBlank()) errors += "Введите изучаемое слово или выражение"
        if (word.length > 200 || content.partOfSpeech.length > 60 || content.definition.length > 4000 || content.example.length > 4000 || ipa.length > 200)
            errors += "Сократите содержимое карточки"
        if (listOf(word, content.partOfSpeech, content.definition, content.example, ipa).any { text ->
                text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } }) errors += "Удалите управляющие символы"
        if (word.any { it == '\n' || it == '\r' || it == '\t' }) errors += "Введите слово или выражение в одной строке"
        if (errors.isNotEmpty()) return CardBuild(errors = errors)
        val definition = cloze(content.definition.trim(), word, "определении", errors)
        val example = cloze(content.example.trim(), word, "примере", errors)
        if (errors.isNotEmpty()) return CardBuild(errors = errors)
        val pos = when (content.partOfSpeech.trim().lowercase()) {
            "verb" -> "v."; "noun" -> "n."; "adjective" -> "adj."; "adverb" -> "adv."
            else -> content.partOfSpeech.trim()
        }
        val front = (if (pos.isBlank()) "" else escape(pos) + " ") + definition + "<br><br>" + example
        return CardBuild(listOf(front, escape(ipa.trim()), "", "", ""))
    }

    private fun buildExplanation(word: String, explanation: String, ipa: String, selected: ClozeSelections?): CardBuild {
        val errors = mutableListOf<String>()
        if (word.isBlank() || word.length > 200 || word.any { it == '\n' || it == '\r' || it == '\t' })
            errors += "Введите слово или выражение в одной строке (до 200 символов)"
        if (explanation.isBlank()) errors += "Добавьте готовое Explain или введите текст карточки вручную"
        if (explanation.length > 12000 || ipa.length > 200) errors += "Сократите содержимое карточки"
        if (listOf(word, explanation, ipa).any { value -> value.any { it.isISOControl() && it !in "\n\r\t" } })
            errors += "Удалите управляющие символы"
        if (errors.isNotEmpty()) return CardBuild(errors = errors)
        val selections = selected ?: ClozeRanges.initial(explanation, word)
        if (!ClozeRanges.valid(explanation, selections.hidden)) return CardBuild(errors = listOf("Некорректные или пересекающиеся выделения. Подготовьте пропуски заново."))
        if (selections.hidden.isEmpty() && !selections.headerHidden) return CardBuild(errors = listOf("Для сохранения нужен хотя бы один пропуск c1"))
        val matches = selections.hidden.sortedBy { it.start }.map { it.start until it.end }
        var visible = explanation
        matches.asReversed().forEach { visible = visible.removeRange(it) }
        if (visible.none { it.isLetterOrDigit() }) return CardBuild(errors = listOf("Добавьте объяснение значения, которое останется видимым"))
        val body = buildString {
            var start = 0
            matches.forEach { match ->
                append(escape(explanation.substring(start, match.first)))
                append("{{c1::").append(escape(explanation.substring(match))).append("}}")
                start = match.last + 1
            }
            append(escape(explanation.substring(start)))
        }
        val front = (if (selections.headerHidden) "{{c1::${escape(word)}}}<br><br>" else "") + body
        return CardBuild(listOf(front, escape(ipa.trim()), "", "", ""), warnings = listOf(
            "Другие словоформы не скрываются автоматически. Проверьте текст и при необходимости отредактируйте его, чтобы не раскрыть ответ."))
    }

    private fun cloze(text: String, word: String, label: String, errors: MutableList<String>): String {
        // Letters, combining marks, numbers, apostrophes and hyphens belong to a word.
        // No stemming, substring replacement, punctuation folding or normalization of spelling.
        val boundary = "[\\p{L}\\p{N}\\p{M}_'’\\-]"
        val matches = Regex("(?<!$boundary)${Pattern.quote(word)}(?!$boundary)", RegexOption.IGNORE_CASE).findAll(text).toList()
        if (matches.size != 1) {
            errors += if (matches.isEmpty()) "В $label нет точной формы «$word». Добавьте её вручную."
                else "В $label слово встречается несколько раз. Оставьте одно вхождение."
            return ""
        }
        val match = matches.single()
        if (text.removeRange(match.range).none { it.isLetterOrDigit() }) {
            errors += "Добавьте смысловой текст в $label — он должен оставаться видимым"
            return ""
        }
        return escape(text.substring(0, match.range.first)) + "{{c1::" + escape(match.value) + "}}" +
            escape(text.substring(match.range.last + 1))
    }

    /** Encode before inserting our two delimiters. Even raw braces/:: cannot inject Cloze. */
    fun escape(text: String): String = buildString {
        text.replace("\r\n", "\n").replace('\r', '\n').forEach { character ->
            append(when (character) {
                '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&#39;"
                '{' -> "&#123;"; '}' -> "&#125;"; ':' -> "&#58;"; '\n' -> "<br>"
                else -> character.toString()
            })
        }
    }
}
