package com.yapyap.core.diagnostics

import co.touchlab.kermit.Logger

/**
 * Phase-3 runtime tracing only. Filter logcat / Xcode console with tag [TAG].
 */
object RuntimeDiagnostics {
    const val TAG = "YapRuntimeDiag"

    private val log = Logger.withTag(TAG)

    fun event(component: String, message: String) {
        log.i { "[$component] $message" }
    }
}
