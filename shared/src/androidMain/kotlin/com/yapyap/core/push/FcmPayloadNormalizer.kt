package com.yapyap.core.push

import com.google.firebase.messaging.RemoteMessage
import com.yapyap.core.push.model.PushDeliveryContext
import com.yapyap.core.push.model.PushDeliveryMode
import com.yapyap.core.push.model.PushInboundMessage

internal object FcmPayloadNormalizer {

    fun normalize(message: RemoteMessage): PushInboundMessage {
        val hasNotification = message.notification != null
        val data = message.data.toMutableMap()

        message.notification?.let { notification ->
            notification.title?.takeIf { it.isNotBlank() }?.let { title ->
                if (data["senderName"].isNullOrBlank()) data["senderName"] = title
            }
            notification.body?.takeIf { it.isNotBlank() }?.let { body ->
                if (data["preview"].isNullOrBlank()) data["preview"] = body
            }
            notification.imageUrl?.toString()?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                if (data["mediaUrl"].isNullOrBlank()) data["mediaUrl"] = imageUrl
            }
        }

        if (data["type"].isNullOrBlank()) {
            data["type"] = when {
                data["callSessionId"] != null -> "incoming_call"
                hasNotification -> "new_message"
                else -> data["type"].orEmpty()
            }
        }

        val mode = when {
            hasNotification && data.isNotEmpty() -> PushDeliveryMode.Hybrid
            hasNotification -> PushDeliveryMode.NotificationOnly
            else -> PushDeliveryMode.DataOnly
        }

        // onMessageReceived is only invoked for notification payloads while the app is in the
        // foreground. In that case the OS does NOT auto-display the tray notification.
        // Background/killed hybrid messages are displayed by the system and do not reach here.
        val delivery = PushDeliveryContext(
            mode = mode,
            osNotificationHandled = false,
        )

        return PushInboundMessage(data = data.toMap(), delivery = delivery)
    }
}
