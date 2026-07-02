package com.yapyap.core.realtime

import com.yapyap.feature.chat.data.dto.MessageDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocketReadyPayload(
    val userId: Int,
)

@Serializable
data class MessageNewPayload(
    val conversationId: Int,
    val message: MessageDto,
)

@Serializable
data class MessageStatusPayload(
    val conversationId: Int,
    val messageId: Int,
    val status: String,
    val userId: Int,
)

@Serializable
data class TypingPayload(
    val conversationId: Int,
    val userId: Int,
    val isTyping: Boolean,
)

@Serializable
data class PresenceUpdatePayload(
    val userId: Int,
    val online: Boolean,
)

@Serializable
data class SocketSendMessagePayload(
    val conversationId: Int,
    val content: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

@Serializable
data class SocketConversationPayload(
    val conversationId: Int,
)

@Serializable
data class SocketMessageReadPayload(
    val conversationId: Int,
    val messageId: Int? = null,
)

@Serializable
data class SocketMessageDeliveredPayload(
    val conversationId: Int,
    val messageId: Int,
)

@Serializable
data class CallSessionIdPayload(
    val callSessionId: String,
)

@Serializable
data class CallIncomingPayload(
    val callSessionId: String,
    val callerId: Int,
    val callerName: String? = null,
    /** Socket payload uses `type` for audio/video; push uses `type` for incoming_call. */
    val type: String? = null,
    val callType: String? = null,
    @SerialName("call_type") val callTypeSnake: String? = null,
    val conversationId: Int? = null,
    val roomName: String? = null,
) {
    fun resolvedCallTypeRaw(): String? {
        callType?.takeIf { it.isNotBlank() }?.let { return it }
        callTypeSnake?.takeIf { it.isNotBlank() }?.let { return it }
        return type?.takeUnless { it.equals("incoming_call", ignoreCase = true) }
    }
}

@Serializable
data class CallAcceptedPayload(
    val callSessionId: String,
    val providerToken: String? = null,
    @SerialName("livekit_url") val livekitUrl: String? = null,
    val roomName: String? = null,
)

@Serializable
data class CallRejectedPayload(
    val callSessionId: String,
    val reason: String? = null,
)

@Serializable
data class CallEndedPayload(
    val callSessionId: String,
    val durationSec: Int? = null,
)

@Serializable
data class CallSyncPayload(
    val callSessionId: String,
    val status: String,
    val endReason: String? = null,
    val durationSec: Int? = null,
    val event: String? = null,
    val conversationId: Int? = null,
    val reason: String? = null,
)
