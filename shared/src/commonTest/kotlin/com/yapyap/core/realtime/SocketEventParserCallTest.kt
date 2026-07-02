package com.yapyap.core.realtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocketEventParserCallTest {

    private val parser = SocketEventParser()

    @Test
    fun parsesCallIncomingPayload() {
        val event = parser.parseCallIncoming(
            """
            {
              "callSessionId": "abc-123",
              "callerId": 5,
              "callerName": "Alice",
              "type": "audio",
              "conversationId": 10,
              "roomName": "room-abc"
            }
            """.trimIndent(),
        )

        assertEquals("abc-123", event.callSessionId)
        assertEquals(5, event.callerId)
        assertEquals("Alice", event.callerName)
        assertEquals("audio", event.callType)
        assertEquals(10, event.conversationId)
    }

    @Test
    fun parsesCallIncomingPayloadWithCallTypeAlias() {
        val event = parser.parseCallIncoming(
            """
            {
              "callSessionId": "abc-124",
              "callerId": 5,
              "callerName": "Alice",
              "callType": "video",
              "conversationId": 10
            }
            """.trimIndent(),
        )

        assertEquals("video", event.callType)
    }

    @Test
    fun encodesCallSessionIdPayload() {
        val encoded = parser.encodePayload(CallSessionIdPayload(callSessionId = "call-55"))
        assertTrue(encoded.contains("call-55"))
    }
}
