package com.yapyap.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.yapyap.core.push.model.PushNotificationChannel

object PushNotificationChannels {
    const val MESSAGES = "yapyap_messages"
    const val CALLS = "yapyap_calls"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val messages = NotificationChannel(
            MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New chat messages"
        }
        val calls = NotificationChannel(
            CALLS,
            "Calls",
            NotificationManager.IMPORTANCE_MAX,
        ).apply {
            description = "Incoming calls"
        }
        manager.createNotificationChannel(messages)
        manager.createNotificationChannel(calls)
    }

    fun idFor(channel: PushNotificationChannel): String = when (channel) {
        PushNotificationChannel.Messages -> MESSAGES
        PushNotificationChannel.Calls -> CALLS
    }
}
