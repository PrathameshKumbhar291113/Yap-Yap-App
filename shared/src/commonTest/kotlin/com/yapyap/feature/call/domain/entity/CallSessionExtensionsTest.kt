package com.yapyap.feature.call.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class CallSessionExtensionsTest {

    private val alice = CallParticipant(userId = 1, displayName = "Alice")
    private val bob = CallParticipant(userId = 2, displayName = "Bob")

    private fun session() = CallSession(
        id = "call-1",
        conversationId = 9,
        callType = CallType.Voice,
        caller = alice,
        callee = bob,
    )

    @Test
    fun aliceSeesBobAsRemote() {
        assertEquals(bob, session().remoteParticipant(localUserId = 1))
        assertEquals("Bob", session().remoteDisplayName(localUserId = 1))
    }

    @Test
    fun bobSeesAliceAsRemote() {
        assertEquals(alice, session().remoteParticipant(localUserId = 2))
        assertEquals("Alice", session().remoteDisplayName(localUserId = 2))
    }

    @Test
    fun incomingStateUsesSessionRemoteParticipant() {
        val state = CallState.Incoming(
            session = session(),
            remoteUser = bob,
        )
        assertEquals(alice, state.remoteParticipant(localUserId = 2))
        assertEquals("Alice", state.remoteDisplayName(localUserId = 2))
    }

    @Test
    fun connectedStateUsesSessionRemoteParticipant() {
        val state = CallState.Connected(
            session = session(),
            connectedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        )
        assertEquals(bob, state.remoteParticipant(localUserId = 1))
        assertEquals(alice, state.remoteParticipant(localUserId = 2))
    }
}
