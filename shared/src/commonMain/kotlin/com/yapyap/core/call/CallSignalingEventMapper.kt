package com.yapyap.core.call

import com.yapyap.core.realtime.SocketEvent

object CallSignalingEventMapper {
    fun fromSocketEvent(event: SocketEvent): CallSignalingEvent? = when (event) {
        is SocketEvent.CallIncoming -> CallSignalingEvent.Invite(
            callSessionId = event.callSessionId,
            conversationId = event.conversationId,
            callerId = event.callerId,
            callerName = event.callerName,
            callTypeRaw = event.callType,
        )
        is SocketEvent.CallAccepted -> CallSignalingEvent.Answer(
            callSessionId = event.callSessionId,
            providerToken = event.providerToken,
            livekitUrl = event.livekitUrl,
            roomName = event.roomName,
        )
        is SocketEvent.CallRejected -> CallSignalingEvent.Reject(
            callSessionId = event.callSessionId,
            reason = event.reason,
        )
        is SocketEvent.CallEnded -> CallSignalingEvent.End(
            callSessionId = event.callSessionId,
            durationSec = event.durationSec,
        )
        is SocketEvent.CallSync -> CallSignalingEvent.Sync(
            callSessionId = event.callSessionId,
            status = event.status,
            endReason = event.endReason,
            durationSec = event.durationSec,
            event = event.event,
        )
        else -> null
    }
}
