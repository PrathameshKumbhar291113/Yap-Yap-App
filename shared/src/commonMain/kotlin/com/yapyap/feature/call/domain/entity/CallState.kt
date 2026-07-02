package com.yapyap.feature.call.domain.entity

import kotlin.time.Instant

sealed interface CallState {
    data object Idle : CallState

    data class Outgoing(
        val session: CallSession,
        val remoteUser: CallParticipant,
    ) : CallState

    data class Incoming(
        val session: CallSession,
        val remoteUser: CallParticipant,
    ) : CallState

    data class Connecting(
        val session: CallSession,
        val direction: CallDirection,
    ) : CallState

    data class Connected(
        val session: CallSession,
        val connectedAt: Instant,
        val isMuted: Boolean = false,
        val isCameraEnabled: Boolean = false,
        val audioRoute: CallAudioRoute = CallAudioRoute.Earpiece,
    ) : CallState

    data class Reconnecting(
        val session: CallSession,
        val attempt: Int,
        val reason: ReconnectReason,
    ) : CallState

    data class Ended(
        val session: CallSession,
        val reason: CallEndReason,
        val durationMs: Long? = null,
    ) : CallState

    data class Failed(
        val session: CallSession?,
        val error: CallError,
    ) : CallState
}
