package com.yapyap.core.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

object YapLogger {
    private val logger = Logger.withTag("YapYap")

    fun install(isDebug: Boolean) {
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(if (isDebug) Severity.Debug else Severity.Warn)
    }

    fun d(message: String, tag: String = "App") {
        logger.d(tag = tag) { redact(message) }
    }

    fun i(message: String, tag: String = "App") {
        logger.i(tag = tag) { redact(message) }
    }

    fun w(message: String, tag: String = "App", throwable: Throwable? = null) {
        logger.w(tag = tag, throwable = throwable) { redact(message) }
    }

    fun e(message: String, tag: String = "App", throwable: Throwable? = null) {
        logger.e(tag = tag, throwable = throwable) { redact(message) }
    }

    fun redact(message: String): String {
        return message
            .let { JSON_TOKEN_FIELD.replace(it, "$1\"[REDACTED]\"") }
            .let { BEARER_TOKEN.replace(it, "Bearer [REDACTED]") }
            .let { RAW_TOKEN_FIELD.replace(it, "$1=[REDACTED]") }
    }

    private val JSON_TOKEN_FIELD = Regex(
        """"(accessToken|refreshToken|token|authorization)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val BEARER_TOKEN = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
    private val RAW_TOKEN_FIELD = Regex(
        """(accessToken|refreshToken|token|authorization)\s*=\s*\S+""",
        RegexOption.IGNORE_CASE,
    )
}
