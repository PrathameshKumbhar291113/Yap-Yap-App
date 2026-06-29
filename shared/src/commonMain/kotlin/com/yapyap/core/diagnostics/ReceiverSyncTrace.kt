package com.yapyap.core.diagnostics

/**
 * Receiver-side reconciliation tracing after missed realtime events.
 * Filter: `adb logcat -s YapRuntimeDiag:I | grep ReceiverSyncTrace`
 */
object ReceiverSyncTrace {
    const val COMPONENT = "ReceiverSyncTrace"

    fun stage(
        stage: String,
        conversationId: Int? = null,
        lastKnownMessageId: Int? = null,
        latestServerMessageId: Int? = null,
        trigger: String? = null,
        extra: Map<String, String?> = emptyMap(),
    ) {
        val parts = buildList {
            add("stage=$stage")
            conversationId?.let { add("conversationId=$it") }
            lastKnownMessageId?.let { add("lastKnownMessageId=$it") }
            latestServerMessageId?.let { add("latestServerMessageId=$it") }
            trigger?.let { add("trigger=$it") }
            extra.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        RuntimeDiagnostics.event(COMPONENT, parts.joinToString(" "))
    }
}
