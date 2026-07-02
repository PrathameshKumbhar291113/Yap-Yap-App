package com.yapyap.feature.call.domain.entity

data class CallSession(
    val id: String,
    val conversationId: Int?,
    val callType: CallType,
    val caller: CallParticipant,
    val callee: CallParticipant,
    val createdAtIso: String? = null,
)
