package com.karas.qwentranslator.util

import android.content.Intent

enum class InvocationSource { PROCESS_TEXT, SHARE }

data class Invocation(val text: String, val readOnly: Boolean, val source: InvocationSource) {
    val canReplace: Boolean get() = source == InvocationSource.PROCESS_TEXT && !readOnly

    fun replacementText(output: String): String? {
        if (!canReplace || output.isBlank()) return null
        return text.takeWhile { it.isWhitespace() } + output.trim() + text.takeLastWhile { it.isWhitespace() }
    }
}

object InvocationParser {
    fun parse(intent: Intent): Invocation? = try {
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> fromValues(
                InvocationSource.PROCESS_TEXT, intent.type,
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
                intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
            )
            Intent.ACTION_SEND -> fromValues(
                InvocationSource.SHARE, intent.type, intent.getCharSequenceExtra(Intent.EXTRA_TEXT), true,
            )
            else -> null
        }
    } catch (e: RuntimeException) {
        // Exported activity: malformed extras must not crash or expose raw input.
        null
    }

    /** Android-free validation shared by both adapters; preserve whitespace in editable selections. */
    fun fromValues(source: InvocationSource, mimeType: String?, text: CharSequence?, readOnly: Boolean = true): Invocation? {
        if (mimeType?.substringBefore(';')?.trim()?.lowercase() != "text/plain") return null
        val value = text?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return Invocation(value, readOnly || source == InvocationSource.SHARE, source)
    }
}
