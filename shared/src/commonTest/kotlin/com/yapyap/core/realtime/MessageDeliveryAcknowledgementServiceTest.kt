package com.yapyap.core.realtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageDeliveryAcknowledgementServiceTest {

    @Test
    fun shouldAckInboundMessageWhenAuthNotRestoredYet() {
        assertTrue(shouldAckInboundMessage(senderId = 1, recipientUserId = null))
        assertTrue(shouldAckInboundMessage(senderId = 1, recipientUserId = 2))
        assertFalse(shouldAckInboundMessage(senderId = 2, recipientUserId = 2))
    }
}
