package com.karas.qwentranslator.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Complete wire body includes prompt, model, temperature, token limits and provider extensions. No headers/keys. */
data class TranslationCacheKey(val text: String, val operation: Operation, val endpoint: String, val body: String) {
    override fun toString() = "TranslationCacheKey(operation=$operation)"
}

data class CacheLoad(val result: TranslationResult, val complete: Boolean = true)

/** Process-only LRU. Same-key calls serialize; cancellation releases the gate without caching a partial result. */
class TranslationMemoryCache(
    private val capacity: Int = 100,
    private val ttlMillis: Long = 30 * 60 * 1000L,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val maxCharacters: Long = 2 * 1024 * 1024L,
) {
    init { require(capacity > 0 && ttlMillis > 0 && maxCharacters > 0) }
    private data class Entry(val result: TranslationResult, val created: Long, val weight: Long)
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val guard = Any()
    private val entries = LinkedHashMap<TranslationCacheKey, Entry>(16, .75f, true)
    private val gates = mutableMapOf<TranslationCacheKey, Gate>()
    private var characters = 0L

    suspend fun getOrLoad(key: TranslationCacheKey, bypass: Boolean = false,
        usage: ApiUsageTracker? = null, loader: suspend () -> CacheLoad): TranslationResult {
        val operation = if (key.operation == Operation.EXPLAIN) ApiOperation.EXPLAIN else ApiOperation.TRANSLATE
        val gate = synchronized(guard) { gates.getOrPut(key) { Gate() }.also { it.users++ } }
        try {
            return gate.mutex.withLock {
                currentCoroutineContext().ensureActive()
                val cached = synchronized(guard) {
                    expire()
                    if (bypass) null else entries[key]?.result
                }
                if (cached != null) {
                    usage?.cacheHit(operation)
                    return@withLock cached
                }
                if (bypass) usage?.cacheBypass(operation) else usage?.cacheMiss(operation)
                val loaded = loader()
                currentCoroutineContext().ensureActive()
                if (loaded.complete && loaded.result.outputText.isNotBlank() && loaded.result.operation == key.operation) {
                    synchronized(guard) {
                        val weight = key.text.length.toLong() + key.body.length + key.endpoint.length + loaded.result.outputText.length
                        entries.remove(key)?.let { characters -= it.weight }
                        if (weight <= maxCharacters) {
                            entries[key] = Entry(loaded.result, clockMillis(), weight)
                            characters += weight
                            while (entries.size > capacity || characters > maxCharacters) {
                                val first = entries.entries.iterator()
                                characters -= first.next().value.weight
                                first.remove()
                            }
                        }
                    }
                }
                loaded.result
            }
        } finally {
            synchronized(guard) { if (--gate.users == 0) gates.remove(key) }
        }
    }

    private fun expire() {
        val now = clockMillis()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (now - entry.created >= ttlMillis) { characters -= entry.weight; iterator.remove() }
        }
    }
}
