package com.karas.qwentranslator.network

import com.karas.qwentranslator.core.FailureKind
import java.io.IOException
import java.io.InterruptedIOException

internal object HttpFailureMapping {
    fun status(code: Int): FailureKind = when (code) {
        401 -> FailureKind.AUTHENTICATION
        403 -> FailureKind.ACCESS_DENIED
        408, 504 -> FailureKind.TIMEOUT
        429 -> FailureKind.RATE_LIMIT
        400, 404, 422 -> FailureKind.CONFIGURATION
        in 500..599 -> FailureKind.UNAVAILABLE
        else -> FailureKind.UNKNOWN
    }
    fun network(error: Exception): FailureKind = when (error) {
        is InterruptedIOException -> FailureKind.TIMEOUT
        is IOException -> FailureKind.NETWORK
        else -> FailureKind.UNKNOWN
    }
}
