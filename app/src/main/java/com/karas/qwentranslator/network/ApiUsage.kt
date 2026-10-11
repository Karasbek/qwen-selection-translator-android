package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.*
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import org.json.JSONObject

internal fun parseApiTokens(body: String): ApiTokens = try {
    val usage = JSONObject(body).optJSONObject("usage")
    fun number(objectValue: JSONObject?, key: String): Long? {
        val value = objectValue?.opt(key) as? Number ?: return null
        return value.toString().takeIf { Regex("[0-9]+").matches(it) }?.toLongOrNull()
    }
    ApiTokens(number(usage, "prompt_tokens"), number(usage, "completion_tokens"), number(usage, "total_tokens"),
        number(usage?.optJSONObject("prompt_tokens_details"), "cached_tokens"))
} catch (_: Exception) { ApiTokens() }

/** Starts at enqueue boundary, after all local configuration validation. Records usage even when parsing fails. */
internal suspend fun <T> Call.withApiUsage(tracker: ApiUsageTracker?, operation: ApiOperation, parse: (HttpPayload) -> T): T {
    val attempt = tracker?.start(operation)
    var tokens = ApiTokens()
    try {
        val response = awaitPayload()
        if (response.code in 200..299) tokens = parseApiTokens(response.body)
        val result = parse(response)
        attempt?.finish(ApiOutcome.SUCCESS, tokens)
        return result
    } catch (e: CancellationException) {
        attempt?.finish(ApiOutcome.CANCELLED, tokens); throw e
    } catch (e: Exception) {
        attempt?.finish(ApiOutcome.ERROR, tokens); throw e
    }
}
