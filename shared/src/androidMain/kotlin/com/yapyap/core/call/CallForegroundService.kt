package com.yapyap.core.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yapyap.core.push.PushNotificationChannels

class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Call in progress" }
                val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
                startForegroundWithNotification(title, isVideo)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(title: String, isVideo: Boolean) {
        PushNotificationChannels.create(this)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val iconRes = resources.getIdentifier("ic_notification", "drawable", packageName)
            .takeIf { it != 0 } ?: android.R.drawable.sym_call_incoming
        val notification = NotificationCompat.Builder(this, PushNotificationChannels.CALLS)
            .setContentTitle(title)
            .setContentText(if (isVideo) "Video call" else "Voice call")
            .setSmallIcon(iconRes)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (isVideo) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        CallDiagnostics.stage(stage = "CALL_FOREGROUND_STARTED")
        CallDiagnostics.stage(stage = "CALL_FOREGROUND_SERVICE_STARTED")
    }

    companion object {
        private const val NOTIFICATION_ID = 42_001
        private const val ACTION_STOP = "com.yapyap.action.STOP_CALL_FOREGROUND"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_IS_VIDEO = "extra_is_video"

        fun start(context: Context, title: String, isVideoCall: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IS_VIDEO, isVideoCall)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
