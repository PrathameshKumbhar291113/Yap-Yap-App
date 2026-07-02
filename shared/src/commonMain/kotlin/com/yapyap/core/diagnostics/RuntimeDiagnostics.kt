package com.yapyap.core.diagnostics

import co.touchlab.kermit.Logger
import kotlin.time.Clock

/**
 * Phase-3 runtime tracing. Filter logcat / Xcode console with tag [TAG].
 *
 * Use [enter] at method boundaries during killed-app / notification-tap investigations.
 */
object RuntimeDiagnostics {
    const val TAG = "YapRuntimeDiag"

    private val log = Logger.withTag(TAG)

    fun event(component: String, message: String) {
        log.i { "[$component] $message" }
    }

    /** Method-entry trace with timestamp and optional details. */
    fun enter(component: String, method: String, details: String = "") {
        val suffix = if (details.isBlank()) "" else " $details"
        log.i { "[$component] ENTER $method ${runtimeContext()}$suffix" }
    }

    fun stack(component: String, label: String) {
        val frames = Throwable()
            .stackTraceToString()
            .lineSequence()
            .filter { it.isNotBlank() }
            .drop(1)
            .take(6)
            .joinToString(" <- ") { it.trim() }
        log.i { "[$component] STACK $label $frames" }
    }

    private fun runtimeContext(): String = "ts=${Clock.System.now().toEpochMilliseconds()}"
}
