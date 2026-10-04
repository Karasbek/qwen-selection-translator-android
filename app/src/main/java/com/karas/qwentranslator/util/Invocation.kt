package com.karas.qwentranslator.util

import android.content.Intent

enum class InvocationSource {
    PROCESS_TEXT,
    SHARE,
}

data class Invocation(
    val text: String,
    val readOnly: Boolean,
    val source: InvocationSource,
)

object InvocationParser {
    fun parse(intent: Intent): Invocation? = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
            text.takeIf { it.isNotBlank() }?.let {
                Invocation(
                    text = it,
                    readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
                    source = InvocationSource.PROCESS_TEXT,
                )
            }
        }

        Intent.ACTION_SEND -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            text.takeIf { it.isNotBlank() }?.let {
                Invocation(text = it, readOnly = true, source = InvocationSource.SHARE)
            }
        }

        else -> null
    }
}
