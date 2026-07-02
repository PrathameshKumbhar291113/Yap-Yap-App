package com.yapyap.core.call

import co.touchlab.kermit.Logger
import com.yapyap.feature.call.domain.entity.CallState

object CallDiagnostics {
    private val log = Logger.withTag("CallDiagnostics")

    fun stage(
        stage: String,
        callSessionId: String? = null,
        extra: Map<String, String> = emptyMap(),
    ) {
        val parts = buildList {
            add("stage=$stage")
            callSessionId?.let { add("callSessionId=$it") }
            addAll(extra.map { (key, value) -> "$key=$value" })
        }
        log.i { parts.joinToString(" ") }
    }

    fun stateChanged(from: CallState, to: CallState, callSessionId: String?) {
        stage(
            stage = "CALL_STATE_CHANGED",
            callSessionId = callSessionId,
            extra = mapOf(
                "from" to from::class.simpleName.orEmpty(),
                "to" to to::class.simpleName.orEmpty(),
            ),
        )
    }

    fun socketReceived(
        event: String,
        callSessionId: String?,
        extra: Map<String, String> = emptyMap(),
    ) {
        stage(
            stage = "CALL_SOCKET_EVENT_RECEIVED",
            callSessionId = callSessionId,
            extra = buildMap {
                put("event", event)
                putAll(extra)
            },
        )
    }

    fun socketEmit(
        event: String,
        callSessionId: String,
        success: Boolean,
        error: String? = null,
    ) {
        stage(
            stage = "CALL_SOCKET_EVENT_SENT",
            callSessionId = callSessionId,
            extra = buildMap {
                put("event", event)
                put("success", success.toString())
                error?.let { put("error", it) }
            },
        )
    }
}
