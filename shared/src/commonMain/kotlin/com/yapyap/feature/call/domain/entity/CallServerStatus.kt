package com.yapyap.feature.call.domain.entity

enum class CallServerStatus {
    Ringing,
    Connecting,
    Connected,
    Ended,
    Rejected,
    Cancelled,
    ;

    val isTerminal: Boolean
        get() = this == Ended || this == Rejected || this == Cancelled

    companion object {
        fun fromRaw(raw: String?): CallServerStatus = when (raw?.lowercase()) {
            "initiated" -> Connecting
            "ringing", "outgoing", "incoming" -> Ringing
            "connecting" -> Connecting
            "accepted", "connected", "active" -> Connected
            "ended", "completed" -> Ended
            "rejected", "declined" -> Rejected
            "cancelled", "canceled", "missed" -> Cancelled
            "failed" -> Ended
            else -> Ended
        }
    }
}
