package com.yapyap.core.realtime

sealed interface RealtimeConnectionState {
    data object Disconnected : RealtimeConnectionState

    data object Connecting : RealtimeConnectionState

    data object Connected : RealtimeConnectionState

    data class Ready(val userId: Int) : RealtimeConnectionState

    data class Reconnecting(val attempt: Int) : RealtimeConnectionState
}
