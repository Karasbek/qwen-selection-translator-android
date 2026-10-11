package com.karas.qwentranslator.anki

enum class ExplainState { READY, LOADING, DISABLED, FAILED, MISSING }
/** Current popup text only. Preparation has no generator/provider dependency. */
data class AnkiPreparationRequest(
    val selection: String,
    val sourceLanguage: String = "auto",
    val explanation: String? = null,
    val explainState: ExplainState = if (explanation.isNullOrBlank()) ExplainState.MISSING else ExplainState.READY,
)
