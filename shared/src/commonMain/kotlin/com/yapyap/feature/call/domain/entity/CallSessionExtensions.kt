package com.yapyap.feature.call.domain.entity

/**
 * Single source of truth for resolving the remote participant in a call.
 * The authenticated user is always local; the UI must show the other party.
 */
fun CallSession.remoteParticipant(localUserId: Int): CallParticipant =
    if (caller.userId == localUserId) callee else caller

fun CallSession.remoteDisplayName(localUserId: Int): String =
    remoteParticipant(localUserId).displayName.ifBlank { "Unknown" }

fun CallState.remoteParticipant(localUserId: Int): CallParticipant? = when (this) {
    is CallState.Outgoing -> session.remoteParticipant(localUserId)
    is CallState.Incoming -> session.remoteParticipant(localUserId)
    is CallState.Connecting -> session.remoteParticipant(localUserId)
    is CallState.Connected -> session.remoteParticipant(localUserId)
    is CallState.Reconnecting -> session.remoteParticipant(localUserId)
    is CallState.Ended -> session.remoteParticipant(localUserId)
    is CallState.Failed -> session?.remoteParticipant(localUserId)
    CallState.Idle -> null
}

fun CallState.remoteDisplayName(localUserId: Int?): String {
    val userId = localUserId ?: return ""
    return remoteParticipant(userId)?.displayName.orEmpty().ifBlank { "Unknown" }
}
