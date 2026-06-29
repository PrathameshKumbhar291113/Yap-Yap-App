package com.yapyap.core.diagnostics

/**
 * Structured outbox / realtime pipeline tracing.
 * Filter logcat: `adb logcat -s YapRuntimeDiag:I | grep OutboxTrace`
 */
object OutboxPipelineTrace {
    const val COMPONENT = "OutboxTrace"

    fun stage(
        stage: String,
        clientMessageId: String? = null,
        serverMessageId: Int? = null,
        conversationId: Int? = null,
        enqueueOrder: Long? = null,
        retryCount: Int? = null,
        extra: Map<String, String?> = emptyMap(),
    ) {
        val parts = buildList {
            add("stage=$stage")
            clientMessageId?.let { add("clientMessageId=$it") }
            serverMessageId?.let { add("serverMessageId=$it") }
            conversationId?.let { add("conversationId=$it") }
            enqueueOrder?.let { add("enqueueOrder=$it") }
            retryCount?.let { add("retryCount=$it") }
            extra.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        RuntimeDiagnostics.event(COMPONENT, parts.joinToString(" "))
    }
}
