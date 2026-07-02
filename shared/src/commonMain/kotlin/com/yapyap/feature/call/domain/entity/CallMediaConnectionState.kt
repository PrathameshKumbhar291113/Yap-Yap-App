package com.yapyap.feature.call.domain.entity

enum class CallMediaConnectionState {
    Idle,
    Connecting,
    Connected,
    Reconnecting,
    Disconnected,
    PoorConnection,
}
