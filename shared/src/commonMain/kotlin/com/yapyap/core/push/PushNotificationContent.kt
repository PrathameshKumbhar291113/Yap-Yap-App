package com.yapyap.core.push

import com.yapyap.core.push.model.ConversationNotificationMessage
import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushMessageType
import com.yapyap.core.push.model.PushNotificationChannel
import com.yapyap.core.push.model.PushPayload
import kotlin.time.Clock

object PushNotificationContent {

    fun fromPayload(
        payload: PushPayload,
        messageHistory: List<ConversationNotificationMessage> = emptyList(),
        showGroupSummary: Boolean = false,
    ): LocalPushNotification? = when (payload.type) {
        PushMessageType.NewMessage -> buildMessageNotification(payload, messageHistory, showGroupSummary)
        PushMessageType.IncomingCall -> buildCallNotification(payload)
        PushMessageType.Unknown -> null
    }

    fun fromConversationState(
        state: ConversationNotificationState,
        payload: PushPayload,
        showGroupSummary: Boolean,
    ): LocalPushNotification {
        val latest = state.messages.lastOrNull()
        return LocalPushNotification(
            title = state.senderName,
            body = latest?.text ?: payload.preview.orEmpty(),
            channel = channelForMessageType(payload.messageType),
            data = payload.raw,
            tag = payload.notificationTag,
            notificationId = stableNotificationId(payload.notificationTag),
            groupKey = payload.conversationId?.let { GROUP_MESSAGES },
            conversationId = payload.conversationId,
            messageId = latest?.messageId ?: payload.messageId,
            imageUrl = payload.mediaUrl?.takeIf { channelForMessageType(payload.messageType) == PushNotificationChannel.Media },
            threadId = payload.threadId,
            messageHistory = state.messages,
            showGroupSummary = showGroupSummary,
        )
    }

    private fun buildMessageNotification(
        payload: PushPayload,
        messageHistory: List<ConversationNotificationMessage>,
        showGroupSummary: Boolean,
    ): LocalPushNotification {
        val channel = channelForMessageType(payload.messageType)
        val senderName = payload.senderName?.takeIf { it.isNotBlank() } ?: "New message"
        val body = payload.preview?.takeIf { it.isNotBlank() } ?: defaultBodyForType(payload.messageType)
        val history = messageHistory.ifEmpty {
            payload.messageId?.let { id ->
                listOf(
                    ConversationNotificationMessage(
                        messageId = id,
                        senderName = senderName,
                        text = body,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            }.orEmpty()
        }
        return LocalPushNotification(
            title = senderName,
            body = history.lastOrNull()?.text ?: body,
            channel = channel,
            data = payload.raw,
            tag = payload.notificationTag,
            notificationId = stableNotificationId(payload.notificationTag),
            groupKey = payload.conversationId?.let { GROUP_MESSAGES },
            conversationId = payload.conversationId,
            messageId = payload.messageId,
            imageUrl = payload.mediaUrl?.takeIf { channel == PushNotificationChannel.Media },
            threadId = payload.threadId,
            messageHistory = history,
            showGroupSummary = showGroupSummary,
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
