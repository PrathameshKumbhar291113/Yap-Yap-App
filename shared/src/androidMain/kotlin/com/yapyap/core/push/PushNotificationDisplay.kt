package com.yapyap.core.push

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushNotificationChannel
import java.net.URL

object PushNotificationDisplay {

    fun show(context: android.content.Context, notification: LocalPushNotification) {
        RuntimeDiagnostics.enter(
            "Notification",
            "PushNotificationDisplay.show",
            "tag=${notification.tag} notificationId=${notification.notificationId} " +
                "conversationId=${notification.conversationId} messageId=${notification.messageId}",
        )
        RuntimeDiagnostics.event("Notification", "APPLICATION_NOTIFICATION_CREATED tag=${notification.tag}")
        if (!hasPostNotificationsPermission(context)) {
            RuntimeDiagnostics.event(
                "Notification",
                "BLOCKED reason=POST_NOTIFICATIONS_denied conversationId=${notification.conversationId}",
            )
            return
        }

        PushNotificationChannels.create(context)

        val launchIntent = buildLaunchIntent(context, notification)
        if (launchIntent == null) {
            RuntimeDiagnostics.event(
                "Notification",
                "BLOCKED reason=no_launch_intent conversationId=${notification.conversationId}",
            )
            return
        }
        AndroidPushRuntimeTrace.dumpPendingIntentExtras("Notification", launchIntent)
        val notifyId = notification.notificationId ?: fallbackNotificationId(notification)
        val tag = notification.tag

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifyId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        RuntimeDiagnostics.event(
            "Notification",
            "PendingIntent.getActivity requestCode=$notifyId flags=UPDATE_CURRENT|IMMUTABLE " +
                "tag=$tag conversationId=${notification.conversationId} messageId=${notification.messageId}",
        )

        val channelId = PushNotificationChannels.idFor(notification.channel)
        val smallIcon = context.resources.getIdentifier(
            "ic_notification",
            "drawable",
            context.packageName,
        ).takeIf { it != 0 } ?: android.R.drawable.stat_notify_chat

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setPriority(priorityFor(notification.channel))
            .setCategory(categoryFor(notification.channel))

        notification.tag?.let { builder.setGroup(notification.groupKey ?: it) }

        val usedMessagingStyle = applyMessagingStyle(builder, notification)
        if (!usedMessagingStyle) {
            builder.setContentTitle(notification.title)
            builder.setContentText(notification.body)
        }
        applyImageStyle(context, builder, notification)

        val built = builder.build()

        val manager = NotificationManagerCompat.from(context)
        try {
            if (tag != null) {
                RuntimeDiagnostics.enter("Notification", "NotificationManager.notify", "tag=$tag id=$notifyId")
                manager.notify(tag, notifyId, built)
                if (notification.showGroupSummary) {
                    postGroupSummaryIfNeeded(context, notification)
                }
                RuntimeDiagnostics.event(
                    "Notification",
                    "NOTIFICATION_REPLACED tag=$tag id=$notifyId conversationId=${notification.conversationId} " +
                        "messages=${notification.messageHistory.size}",
                )
            } else {
                RuntimeDiagnostics.event(
                    "Notification",
                    "NotificationManager.notify() id=$notifyId channel=$channelId importance=HIGH " +
                        "smallIcon=$smallIcon pendingIntent=requestCode=$notifyId",
                )
                manager.notify(notifyId, built)
            }
        } catch (error: SecurityException) {
            RuntimeDiagnostics.event(
                "Notification",
                "EXCEPTION SecurityException notify failed: ${error.message}",
            )
            throw error
        } catch (error: Exception) {
            RuntimeDiagnostics.event(
                "Notification",
                "EXCEPTION notify failed: ${error.javaClass.simpleName} ${error.message}",
            )
            throw error
        }
    }

    fun dismiss(context: android.content.Context, conversationId: String) {
        val tag = "conv_$conversationId"
        val manager = NotificationManagerCompat.from(context)
        val stableId = PushNotificationContent.stableNotificationId(tag)

        RuntimeDiagnostics.event("Notification", "NotificationManager.cancel() tag=$tag conversationId=$conversationId")
        stableId?.let { manager.cancel(tag, it) }
        // FCM auto-displayed notifications often use notification id 0 with the backend tag.
        manager.cancel(tag, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val systemManager = context.getSystemService(NotificationManager::class.java)
            systemManager?.activeNotifications?.forEach { status ->
                if (status.tag == tag) {
                    manager.cancel(tag, status.id)
                }
            }
        }

        dismissGroupSummaryIfNeeded(context, manager)
    }

    private fun applyMessagingStyle(
        builder: NotificationCompat.Builder,
        notification: LocalPushNotification,
    ): Boolean {
        if (notification.isSummary) return false
        if (notification.channel == PushNotificationChannel.Calls) return false

        val history = notification.messageHistory
        if (history.isEmpty()) return false

        val sender = Person.Builder()
            .setName(notification.title)
            .setImportant(true)
            .build()

        val style = NotificationCompat.MessagingStyle(sender)
            .setConversationTitle(notification.title)

        history.forEach { message ->
            val person = Person.Builder()
                .setName(message.senderName)
                .build()
            style.addMessage(message.text, message.timestamp, person)
        }

        builder.setStyle(style)
        builder.setContentText(history.last().text)
        return true
    }

    private fun applyImageStyle(
        context: android.content.Context,
        builder: NotificationCompat.Builder,
        notification: LocalPushNotification,
    ) {
        val imageUrl = notification.imageUrl ?: return
        val bitmap = runCatching {
            URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return

        builder.setLargeIcon(bitmap)
        builder.setStyle(
            NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .setSummaryText(notification.body),
        )
    }

    private fun postGroupSummaryIfNeeded(
        context: android.content.Context,
        notification: LocalPushNotification,
    ) {
        val groupKey = notification.groupKey ?: return
        if (notification.channel == PushNotificationChannel.Calls) return

        val summary = NotificationCompat.Builder(context, PushNotificationChannels.MESSAGES)
            .setSmallIcon(
                context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.stat_notify_chat,
            )
            .setContentTitle("YapYap")
            .setContentText("New messages")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle("YapYap")
                    .setSummaryText("New messages"),
            )
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun dismissGroupSummaryIfNeeded(
        context: android.content.Context,
        manager: NotificationManagerCompat,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val systemManager = context.getSystemService(NotificationManager::class.java)
            val hasGroupedChild = systemManager?.activeNotifications?.any { status ->
                val notification = status.notification
                notification.group == PushNotificationContent.GROUP_MESSAGES &&
                    (notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            } ?: false
            if (!hasGroupedChild) {
                manager.cancel(SUMMARY_NOTIFICATION_ID)
            }
            return
        }
        manager.cancel(SUMMARY_NOTIFICATION_ID)
    }

    private fun buildLaunchIntent(
        context: android.content.Context,
        notification: LocalPushNotification,
    ): Intent? {
        val template = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return Intent(template).apply {
            notification.data.forEach { (key, value) -> putExtra(key, value) }
            putExtra(PushIntentExtras.FROM_NOTIFICATION, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun priorityFor(channel: PushNotificationChannel): Int = when (channel) {
        PushNotificationChannel.Calls -> NotificationCompat.PRIORITY_MAX
        PushNotificationChannel.Media,
        PushNotificationChannel.Messages,
        -> NotificationCompat.PRIORITY_HIGH
    }

    private fun categoryFor(channel: PushNotificationChannel): String = when (channel) {
        PushNotificationChannel.Calls -> NotificationCompat.CATEGORY_CALL
        else -> NotificationCompat.CATEGORY_MESSAGE
    }

    private fun hasPostNotificationsPermission(context: android.content.Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var fallbackId = 1_000

    private fun fallbackNotificationId(notification: LocalPushNotification): Int {
        return notification.conversationId?.hashCode()?.and(0x7FFFFFFF) ?: fallbackId++
    }

    private const val SUMMARY_NOTIFICATION_ID = 1
}

object PushIntentExtras {
    const val FROM_NOTIFICATION = "yapyap_from_notification"
}

object PushIntentParser {
    fun parse(intent: Intent?): Map<String, String>? {
        RuntimeDiagnostics.enter("DeepLink", "PushIntentParser.parse")
        if (intent == null) {
            RuntimeDiagnostics.event("DeepLink", "PushIntentParser.parse result=null reason=intent_null")
            return null
        }
        AndroidPushRuntimeTrace.dumpIntent("DeepLink", "PushIntentParser.input", intent)
        if (!intent.getBooleanExtra(PushIntentExtras.FROM_NOTIFICATION, false)) {
            val hasPushType = intent.extras?.containsKey("type") == true
            RuntimeDiagnostics.event(
                "DeepLink",
                "PushIntentParser fromNotification=false hasPushType=$hasPushType",
            )
            if (!hasPushType) {
                RuntimeDiagnostics.event(
                    "DeepLink",
                    "PushIntentParser result=null reason=no_from_notification_and_no_type",
                )
                return null
            }
        }
        val extras = intent.extras ?: run {
            RuntimeDiagnostics.event("DeepLink", "PushIntentParser result=null reason=extras_null")
            return null
        }
        val parsed = extras.keySet()
            .mapNotNull { key ->
                if (key == PushIntentExtras.FROM_NOTIFICATION) return@mapNotNull null
                val value = extras.get(key)?.toString()
                if (value.isNullOrBlank()) null else key to value
            }
            .toMap()
            .takeIf { it.isNotEmpty() }
        RuntimeDiagnostics.event("DeepLink", "PushIntentParser result=$parsed")
        return parsed
    }
}
