package com.yapyap.feature.call.domain.entity

enum class CallLogStatus {
    Completed,
    Missed,
    Rejected,
    Failed,
    Cancelled,
}

data class CallLogEntry(
    val id: String,
    val conversationId: Int?,
    val latestMessageId: String? = null,
    val callType: CallType,
    val direction: CallDirection,
    val status: CallLogStatus,
    val answered: Boolean = false,
    val remoteUser: CallParticipant,
    val startedAtIso: String?,
    val durationSec: Int? = null,
    val endReason: String? = null,
    val roomName: String? = null,
) {
    val durationMs: Long?
        get() = durationSec?.times(1_000L)
}
