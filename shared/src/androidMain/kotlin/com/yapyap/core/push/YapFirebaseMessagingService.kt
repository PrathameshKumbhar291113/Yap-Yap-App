package com.yapyap.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class YapFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushNotificationBridge.deliverTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return

        val inForeground = isAppInForeground()
        if (!inForeground) {
            PushNotificationChannels.create(applicationContext)
            FcmMessageNotification.show(applicationContext, data, message.notification)
        }

        PushNotificationBridge.deliverMessage(data)
    }
}
