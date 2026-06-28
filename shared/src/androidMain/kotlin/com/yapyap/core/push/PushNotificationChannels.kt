package com.yapyap.core.push

import com.yapyap.core.push.model.PushChannelIds
import com.yapyap.core.push.model.PushNotificationChannel

/**
 * Android notification channels — IDs must match backend FCM android.notification.channelId.
 */
object PushNotificationChannels {
    const val MESSAGES = PushChannelIds.MESSAGES
    const val MEDIA = PushChannelIds.MEDIA
    const val CALLS = PushChannelIds.CALLS

    fun create(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return

        val messages = android.app.NotificationChannel(
            MESSAGES,
            "Messages",
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New chat messages"
            enableVibration(true)
        }

        val media = android.app.NotificationChannel(
            MEDIA,
            "Media",
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Photos, videos, and media messages"
            enableVibration(true)
        }

        val calls = android.app.NotificationChannel(
            CALLS,
            "Calls",
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming voice and video calls"
            enableVibration(true)
        }

        manager.createNotificationChannel(messages)
        manager.createNotificationChannel(media)
        manager.createNotificationChannel(calls)
    }

    fun idFor(channel: PushNotificationChannel): String = when (channel) {
        PushNotificationChannel.Messages -> MESSAGES
        PushNotificationChannel.Media -> MEDIA
        PushNotificationChannel.Calls -> CALLS
    }
}
