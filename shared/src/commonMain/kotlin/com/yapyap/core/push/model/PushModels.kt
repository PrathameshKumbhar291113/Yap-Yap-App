package com.yapyap.core.push.model

enum class PushPlatformType(val apiValue: String) {
    Android("android"),
    Ios("ios"),
}

enum class PushMessageType(val value: String) {
    NewMessage("new_message"),
    IncomingCall("incoming_call"),
    Unknown(""),
    ;

    companion object {
        fun fromRaw(raw: String?): PushMessageType =
            entries.firstOrNull { it.value == raw } ?: Unknown
    }
}

data class PushPayload(
    val type: PushMessageType,
    val raw: Map<String, String>,
) {
    val conversationId: String? get() = raw["conversationId"]
    val messageId: String? get() = raw["messageId"]
    val senderId: String? get() = raw["senderId"]
    val senderName: String? get() = raw["senderName"]
    val preview: String? get() = raw["preview"]
    val messageType: String? get() = raw["messageType"]
    val mediaUrl: String? get() = raw["mediaUrl"] ?: raw["media_url"]
    val payloadVersion: String? get() = raw["payloadVersion"]
    val payloadKind: String? get() = raw["payloadKind"]
    val clickAction: String? get() = raw["click_action"] ?: raw["clickAction"]
    val callSessionId: String? get() = raw["callSessionId"]
    val callerId: String? get() = raw["callerId"]
    val callerName: String? get() = raw["callerName"]
    val callType: String? get() = raw["callType"] ?: raw["call_type"]
    val createdAt: String? get() = raw["createdAt"] ?: raw["created_at"]

    /** Matches backend conversation grouping: conv_{conversationId} */
    val notificationTag: String?
        get() = conversationId?.let { "conv_$it" }

    val threadId: String?
        get() = conversationId?.let { "conv_$it" }
            ?: callSessionId?.let { "call_$it" }
}

sealed class PushNavigationTarget {
    data class Chat(val conversationId: String) : PushNavigationTarget()
    data class IncomingCall(val callSessionId: String) : PushNavigationTarget()
}

data class ConversationNotificationMessage(
    val messageId: String?,
    val senderName: String,
    val text: String,
    val timestamp: Long,
)

data class LocalPushNotification(
    val title: String,
    val body: String,
    val channel: PushNotificationChannel,
    val data: Map<String, String>,
    /** Android tag + iOS replacement id — conv_{conversationId} from backend. */
    val tag: String? = null,
    val notificationId: Int? = null,
    val groupKey: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val imageUrl: String? = null,
    val threadId: String? = null,
    val isSummary: Boolean = false,
    /** Stacked messages for MessagingStyle / iOS thread body. */
    val messageHistory: List<ConversationNotificationMessage> = emptyList(),
    /** Post Android group summary only when multiple conversations are active. */
    val showGroupSummary: Boolean = false,
)

enum class PushNotificationChannel {
    Messages,
    Media,
    Calls,
}

enum class PushPermissionStatus {
    Granted,
    Denied,
    NotDetermined,
}

/** Must match backend ANDROID_CHANNELS in push/constants.js */
object PushChannelIds {
    const val MESSAGES = "messages"
    const val MEDIA = "media"
    const val CALLS = "calls"
}

object PushClickActions {
    const val OPEN_CHAT = "OPEN_CHAT"
    const val INCOMING_CALL = "INCOMING_CALL"
}
