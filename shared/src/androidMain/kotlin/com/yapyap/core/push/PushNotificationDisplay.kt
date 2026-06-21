package com.yapyap.core.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yapyap.core.push.model.LocalPushNotification

object PushNotificationDisplay {
    private var notificationId = 1_000

    fun show(context: android.content.Context, notification: LocalPushNotification) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        notification.data.forEach { (key, value) ->
            launchIntent.putExtra(key, value)
        }
        launchIntent.putExtra(PushIntentExtras.FROM_NOTIFICATION, true)
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = PushNotificationChannels.idFor(notification.channel)
        val built = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                when (notification.channel) {
                    com.yapyap.core.push.model.PushNotificationChannel.Messages ->
                        NotificationCompat.PRIORITY_HIGH
                    com.yapyap.core.push.model.PushNotificationChannel.Calls ->
                        NotificationCompat.PRIORITY_MAX
                },
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId++, built)
    }
}

object PushIntentExtras {
    const val FROM_NOTIFICATION = "yapyap_from_notification"
}

object PushIntentParser {
    fun parse(intent: Intent?): Map<String, String>? {
        if (intent == null) return null
        if (!intent.getBooleanExtra(PushIntentExtras.FROM_NOTIFICATION, false)) {
            val hasPushType = intent.extras?.containsKey("type") == true
            if (!hasPushType) return null
        }
        val extras = intent.extras ?: return null
        return extras.keySet()
            .mapNotNull { key ->
                val value = extras.get(key)?.toString()
                if (value.isNullOrBlank()) null else key to value
            }
            .toMap()
            .takeIf { it.isNotEmpty() }
    }
}
