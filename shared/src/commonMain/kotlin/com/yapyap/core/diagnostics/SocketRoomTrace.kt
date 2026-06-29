package com.yapyap.core.diagnostics

/**
 * Socket room / reconnect tracing. Filter: `adb logcat | grep SocketRoomTrace`
 */
object SocketRoomTrace {
    const val COMPONENT = "SocketRoomTrace"

    fun stage(stage: String, extra: Map<String, String?> = emptyMap()) {
        val parts = buildList {
            add("stage=$stage")
            extra.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        RuntimeDiagnostics.event(COMPONENT, parts.joinToString(" "))
    }
}
