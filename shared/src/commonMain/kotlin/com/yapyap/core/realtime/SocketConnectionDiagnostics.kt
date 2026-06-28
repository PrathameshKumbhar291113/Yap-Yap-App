package com.yapyap.core.realtime

data class SocketConnectionDiagnostics(
    val connected: Boolean,
    val authenticated: Boolean,
    val socketId: String?,
    val userId: Int?,
)
