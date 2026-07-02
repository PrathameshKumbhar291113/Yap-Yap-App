package com.yapyap.core.call

sealed interface CallSignalingEvent {
    data class Invite(
        val callSessionId: String,
        val conversationId: Int?,
        val callerId: Int,
        val callerName: String?,
        val callTypeRaw: String?,
    ) : CallSignalingEvent

    data class Answer(
        val callSessionId: String,
        val providerToken: String? = null,
        val livekitUrl: String? = null,
        val roomName: String? = null,
    ) : CallSignalingEvent

    data class Reject(
        val callSessionId: String,
        val reason: String? = null,
    ) : CallSignalingEvent

    data class End(
        val callSessionId: String,
        val durationSec: Int? = null,
    ) : CallSignalingEvent

    data class Sync(
        val callSessionId: String,
        val status: String,
        val endReason: String? = null,
        val durationSec: Int? = null,
        val event: String? = null,
    ) : CallSignalingEvent

    data class IceCandidate(
        val callSessionId: String,
        val candidate: String,
    ) : CallSignalingEvent
}
