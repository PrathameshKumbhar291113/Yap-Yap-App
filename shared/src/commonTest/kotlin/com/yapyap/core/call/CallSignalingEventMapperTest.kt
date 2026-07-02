package com.yapyap.core.call

import com.yapyap.core.realtime.SocketEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallSignalingEventMapperTest {

    @Test
    fun mapsCallIncomingToInvite() {
        val event = CallSignalingEventMapper.fromSocketEvent(
            SocketEvent.CallIncoming(
                callSessionId = "call-1",
                callerId = 42,
                callerName = "Alice",
                callType = "audio",
                conversationId = 7,
                roomName = "room-1",
            ),
        )

        assertEquals(
            CallSignalingEvent.Invite(
                callSessionId = "call-1",
                conversationId = 7,
                callerId = 42,
                callerName = "Alice",
                callTypeRaw = "audio",
            ),
            event,
        )
    }

    @Test
    fun mapsCallAcceptedToAnswer() {
        val event = CallSignalingEventMapper.fromSocketEvent(
            SocketEvent.CallAccepted(
                callSessionId = "call-2",
                providerToken = "token",
                livekitUrl = "wss://livekit.example.com",
                roomName = "room-2",
            ),
        )

        assertEquals(
            CallSignalingEvent.Answer(
                callSessionId = "call-2",
                providerToken = "token",
                livekitUrl = "wss://livekit.example.com",
                roomName = "room-2",
            ),
            event,
        )
    }

    @Test
    fun mapsCallSyncToSyncEvent() {
        val event = CallSignalingEventMapper.fromSocketEvent(
            SocketEvent.CallSync(
                callSessionId = "call-3",
                status = "accepted",
                endReason = null,
                durationSec = null,
                event = "accepted",
            ),
        )

        assertEquals(
            CallSignalingEvent.Sync(
                callSessionId = "call-3",
                status = "accepted",
                endReason = null,
                durationSec = null,
                event = "accepted",
            ),
            event,
        )
    }

    @Test
    fun ignoresNonCallEvents() {
        assertNull(
            CallSignalingEventMapper.fromSocketEvent(
                SocketEvent.Ready(userId = 1),
            ),
        )
    }
}
