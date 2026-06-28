package com.yapyap.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yapyap.core.diagnostics.RuntimeDiagnostics

class YapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        RuntimeDiagnostics.event(
            "Firebase",
            "onNewToken() tokenPrefix=${token.take(12)}... length=${token.length}",
        )
        PushNotificationBridge.deliverTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        RuntimeDiagnostics.event(
            "Firebase",
            "onMessageReceived() " +
                "from=${message.from} messageId=${message.messageId} sentTime=${message.sentTime} " +
                "notification=${notification?.let { "title=${it.title} body=${it.body} channelId=${it.channelId}" } ?: "null"} " +
                "dataKeys=${message.data.keys.sorted()} data=${message.data}",
        )
        PushNotificationChannels.create(applicationContext)
        val normalized = FcmPayloadNormalizer.normalize(message)
        RuntimeDiagnostics.event(
            "Firebase",
            "normalized mode=${normalized.delivery.mode} osHandled=${normalized.delivery.osNotificationHandled} " +
                "dataKeys=${normalized.data.keys.sorted()} type=${normalized.data["type"]} " +
                "conversationId=${normalized.data["conversationId"]} messageId=${normalized.data["messageId"]}",
        )
        if (normalized.data.isEmpty()) {
            RuntimeDiagnostics.event("Firebase", "SKIP reason=empty_normalized_data")
            return
        }
        PushNotificationBridge.deliverMessage(normalized)
    }

    override fun onDeletedMessages() {
        RuntimeDiagnostics.event("Firebase", "onDeletedMessages() FCM dropped messages while app was backgrounded")
        super.onDeletedMessages()
    }
}
