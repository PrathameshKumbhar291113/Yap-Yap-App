package com.yapyap.core.push

import com.google.firebase.messaging.RemoteMessage
import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushNotificationChannel

internal object FcmMessageNotification {
    fun show(
        context: android.content.Context,
        data: Map<String, String>,
        notification: RemoteMessage.Notification?,
    ) {
        val payload = PushPayloadParser.parse(data)
        when (payload.type) {
            com.yapyap.core.push.model.PushMessageType.NewMessage -> {
                val title = notification?.title
                    ?: payload.senderName?.takeIf { it.isNotBlank() }
                    ?: "New message"
                val body = notification?.body
                    ?: payload.preview?.takeIf { it.isNotBlank() }
                    ?: "You have a new message"
                PushNotificationDisplay.show(
                    context,
                    LocalPushNotification(
                        title = title,
                        body = body,
                        channel = PushNotificationChannel.Messages,
                        data = payload.raw,
                    ),
                )
            }
            com.yapyap.core.push.model.PushMessageType.IncomingCall -> {
                val title = notification?.title
                    ?: payload.callerName?.takeIf { it.isNotBlank() }
                    ?: "Incoming call"
                val callLabel = payload.callType?.takeIf { it.isNotBlank() } ?: "call"
                val body = notification?.body ?: "Incoming $callLabel"
                PushNotificationDisplay.show(
                    context,
                    LocalPushNotification(
                        title = title,
                        body = body,
                        channel = PushNotificationChannel.Calls,
                        data = payload.raw,
                    ),
                )
            }
            com.yapyap.core.push.model.PushMessageType.Unknown -> Unit
        }
    }
}
