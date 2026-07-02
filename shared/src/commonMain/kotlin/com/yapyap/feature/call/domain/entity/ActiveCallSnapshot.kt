package com.yapyap.feature.call.domain.entity

data class ActiveCallSnapshot(
    val session: CallSession,
    val status: CallServerStatus,
    val durationMs: Long? = null,
    val reconciliation: CallReconciliation? = null,
    val credentials: CallRoomCredentials? = null,
    val tokenExpiresAt: String? = null,
)
