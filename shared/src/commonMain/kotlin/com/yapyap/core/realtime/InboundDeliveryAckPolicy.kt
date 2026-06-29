package com.yapyap.core.realtime

/**
 * Whether an inbound message should enter the delivery-ack pipeline.
 * When auth is not restored yet ([recipientUserId] null), still ack — the service queues until ready.
 */
internal fun shouldAckInboundMessage(senderId: Int, recipientUserId: Int?): Boolean =
    recipientUserId == null || senderId != recipientUserId
