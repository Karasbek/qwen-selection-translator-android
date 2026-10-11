package com.karas.qwentranslator.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ApiOperation(val label: String) { EXPLAIN("Explain"), TRANSLATE("Translate"), ANKI_DRAFT("Anki Draft") }
enum class ApiOutcome { SUCCESS, ERROR, CANCELLED }
data class ApiTokens(val prompt: Long? = null, val completion: Long? = null, val total: Long? = null, val cached: Long? = null)
data class TokenTotal(val known: Long = 0, val samples: Long = 0) {
    fun add(value: Long?) = if (value == null) this else TokenTotal(known + value, samples + 1)
    fun display() = if (samples == 0L) "неизвестно" else "$known ($samples ответов)"
}
data class ApiCounters(
    val started: Long = 0, val httpAttempts: Long = 0, val success: Long = 0,
    val errors: Long = 0, val cancelled: Long = 0,
    val cacheHits: Long = 0, val cacheMisses: Long = 0, val cacheBypasses: Long = 0,
    val prompt: TokenTotal = TokenTotal(), val completion: TokenTotal = TokenTotal(),
    val total: TokenTotal = TokenTotal(), val cached: TokenTotal = TokenTotal(),
) {
    val pending get() = started - success - errors - cancelled
}
data class ApiDiagnostics(val operations: Map<ApiOperation, ApiCounters> = ApiOperation.entries.associateWith { ApiCounters() })

/** Aggregate numbers only, application-session lifetime. No text, endpoint, credentials or logging. */
class ApiUsageTracker {
    private val mutable = MutableStateFlow(ApiDiagnostics())
    val state = mutable.asStateFlow()
    private fun change(operation: ApiOperation, update: (ApiCounters) -> ApiCounters) {
        mutable.update { snapshot -> snapshot.copy(operations = snapshot.operations + (operation to update(snapshot.operations.getValue(operation)))) }
    }
    fun start(operation: ApiOperation): Attempt {
        change(operation) { it.copy(started = it.started + 1) }
        return Attempt(operation)
    }
    fun cacheHit(operation: ApiOperation) = change(operation) { it.copy(cacheHits = it.cacheHits + 1) }
    fun cacheMiss(operation: ApiOperation) = change(operation) { it.copy(cacheMisses = it.cacheMisses + 1) }
    fun cacheBypass(operation: ApiOperation) = change(operation) { it.copy(cacheBypasses = it.cacheBypasses + 1) }
    fun httpAttempt(operation: ApiOperation) = change(operation) { it.copy(httpAttempts = it.httpAttempts + 1) }
    inner class Attempt internal constructor(private val operation: ApiOperation) {
        private var finished = false
        @Synchronized fun finish(outcome: ApiOutcome, tokens: ApiTokens = ApiTokens()) {
            if (finished) return
            finished = true
            change(operation) { it.copy(
                success = it.success + if (outcome == ApiOutcome.SUCCESS) 1 else 0,
                errors = it.errors + if (outcome == ApiOutcome.ERROR) 1 else 0,
                cancelled = it.cancelled + if (outcome == ApiOutcome.CANCELLED) 1 else 0,
                prompt = it.prompt.add(tokens.prompt), completion = it.completion.add(tokens.completion),
                total = it.total.add(tokens.total), cached = it.cached.add(tokens.cached),
            ) }
        }
    }
}
