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
    val callSessionId: String? get() = raw["callSessionId"]
    val callerId: String? get() = raw["callerId"]
    val callerName: String? get() = raw["callerName"]
    val callType: String? get() = raw["callType"]
}

sealed class PushNavigationTarget {
    data class Chat(val conversationId: String) : PushNavigationTarget()
    data class IncomingCall(val callSessionId: String) : PushNavigationTarget()
}

data class LocalPushNotification(
    val title: String,
    val body: String,
    val channel: PushNotificationChannel,
    val data: Map<String, String>,
)

enum class PushNotificationChannel {
    Messages,
    Calls,
}

enum class PushPermissionStatus {
    Granted,
    Denied,
    NotDetermined,
}
