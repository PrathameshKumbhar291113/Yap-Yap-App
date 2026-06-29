package com.yapyap.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yapyap.core.diagnostics.RuntimeDiagnostics

class YapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        RuntimeDiagnostics.enter("Firebase", "FirebaseMessagingService.onNewToken", "tokenLen=${token.length}")
        PushNotificationBridge.deliverTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        RuntimeDiagnostics.enter("Firebase", "FirebaseMessagingService.onMessageReceived")
        RuntimeDiagnostics.stack("Firebase", "onMessageReceived")
        RuntimeDiagnostics.event("Firebase", "FCM_RAW from=${message.from} messageId=${message.messageId} sentTime=${message.sentTime} ttl=${message.ttl} collapseKey=${message.collapseKey} priority=${message.priority} originalPriority=${message.originalPriority} messageType=${message.messageType}")
        RuntimeDiagnostics.event("Firebase", "FCM_RAW dataKeys=${message.data.keys.sorted()} data=${message.data}")
        val notification = message.notification
        if (notification != null) {
            RuntimeDiagnostics.event(
                "Firebase",
                "FCM_RAW notification title=${notification.title} body=${notification.body} " +
                    "channelId=${notification.channelId} clickAction=${notification.clickAction} " +
                    "tag=${notification.tag} imageUrl=${notification.imageUrl} link=${notification.link} " +
                    "color=${notification.color} sound=${notification.sound} ticker=${notification.ticker} " +
                    "icon=${notification.icon} notificationCount=${notification.notificationCount} " +
                    "notificationPriority=${notification.notificationPriority} visibility=${notification.visibility} " +
                    "eventTime=${notification.eventTime} localOnly=${notification.localOnly} " +
                    "defaultSound=${notification.defaultSound} defaultVibrateSettings=${notification.defaultVibrateSettings} " +
                    "defaultLightSettings=${notification.defaultLightSettings} sticky=${notification.sticky}",
            )
            RuntimeDiagnostics.event("Firebase", "NOTIFICATION_BLOCK_PRESENT=true")
        } else {
            RuntimeDiagnostics.event("Firebase", "NOTIFICATION_BLOCK_PRESENT=false")
        }

        PushNotificationChannels.create(applicationContext)
        val normalized = FcmPayloadNormalizer.normalize(message)
        RuntimeDiagnostics.event(
            "Firebase",
            "FCM_NORMALIZED mode=${normalized.delivery.mode} osHandled=${normalized.delivery.osNotificationHandled} " +
                "dataKeys=${normalized.data.keys.sorted()} data=${normalized.data}",
        )
        if (normalized.data.isEmpty()) {
            RuntimeDiagnostics.event("Firebase", "EXIT onMessageReceived reason=empty_normalized_data")
            return
        }
        RuntimeDiagnostics.event("Firebase", "APPLICATION_PATH=onMessageReceived_will_deliver_to_coordinator")
        PushNotificationBridge.deliverMessage(normalized)
    }

    override fun onDeletedMessages() {
        RuntimeDiagnostics.enter("Firebase", "FirebaseMessagingService.onDeletedMessages")
        super.onDeletedMessages()
    }
}
