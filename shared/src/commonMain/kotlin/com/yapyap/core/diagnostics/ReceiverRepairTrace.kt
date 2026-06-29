package com.yapyap.core.diagnostics

/**
 * Guaranteed receiver repair tracing.
 * Filter: `adb logcat -s YapRuntimeDiag:I | grep ReceiverRepairTrace`
 */
object ReceiverRepairTrace {
    const val COMPONENT = "ReceiverRepairTrace"

    fun log(
        stage: String,
        trigger: String? = null,
        conversationId: Int? = null,
        lastKnownId: Int? = null,
        extra: Map<String, String?> = emptyMap(),
    ) {
        val parts = buildList {
            add("stage=$stage")
            trigger?.let { add("trigger=$it") }
            conversationId?.let { add("conversationId=$it") }
            lastKnownId?.let { add("lastKnownId=$it") }
            extra.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        RuntimeDiagnostics.event(COMPONENT, parts.joinToString(" "))
    }
}
