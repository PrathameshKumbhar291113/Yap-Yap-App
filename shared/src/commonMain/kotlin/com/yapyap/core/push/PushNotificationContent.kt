package com.yapyap.core.push

import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushMessageType
import com.yapyap.core.push.model.PushNotificationChannel
import com.yapyap.core.push.model.PushPayload

object PushNotificationContent {

    fun fromPayload(payload: PushPayload): LocalPushNotification? = when (payload.type) {
        PushMessageType.NewMessage -> buildMessageNotification(payload)
        PushMessageType.IncomingCall -> buildCallNotification(payload)
        PushMessageType.Unknown -> null
    }

    private fun buildMessageNotification(payload: PushPayload): LocalPushNotification {
        val channel = channelForMessageType(payload.messageType)
        return LocalPushNotification(
            title = payload.senderName?.takeIf { it.isNotBlank() } ?: "New message",
            body = payload.preview?.takeIf { it.isNotBlank() } ?: defaultBodyForType(payload.messageType),
            channel = channel,
            data = payload.raw,
            tag = payload.notificationTag,
            notificationId = stableNotificationId(payload.notificationTag),
            groupKey = payload.conversationId?.let { GROUP_MESSAGES },
            conversationId = payload.conversationId,
            messageId = payload.messageId,
            imageUrl = payload.mediaUrl?.takeIf { channel == PushNotificationChannel.Media },
            threadId = payload.threadId,
        )
    }

    private fun buildCallNotification(payload: PushPayload): LocalPushNotification {
        val callLabel = payload.callType?.takeIf { it.isNotBlank() } ?: "call"
        val tag = payload.callSessionId?.let { "call_$it" }
        return LocalPushNotification(
            title = payload.callerName?.takeIf { it.isNotBlank() } ?: "Incoming call",
            body = "Incoming $callLabel",
            channel = PushNotificationChannel.Calls,
            data = payload.raw,
            tag = tag,
            notificationId = stableNotificationId(tag),
            groupKey = GROUP_CALLS,
            threadId = payload.threadId,
        )
    }

    private fun channelForMessageType(messageType: String?): PushNotificationChannel = when (messageType) {
        "image", "video" -> PushNotificationChannel.Media
        else -> PushNotificationChannel.Messages
    }

    private fun defaultBodyForType(messageType: String?): String = when (messageType) {
        "image" -> "Photo"
        "video" -> "Video"
        "audio" -> "Voice message"
        "document" -> "Document"
        else -> "You have a new message"
    }

    fun stableNotificationId(tag: String?): Int? =
        tag?.hashCode()?.and(0x7FFFFFFF)

    const val GROUP_MESSAGES = "yapyap.messages"
    const val GROUP_CALLS = "yapyap.calls"
}
