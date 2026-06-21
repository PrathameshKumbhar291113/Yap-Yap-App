package com.yapyap.core.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushNotificationChannel
import com.yapyap.core.push.model.PushPermissionStatus
import com.yapyap.core.push.model.PushPlatformType
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.koin.mp.KoinPlatformTools

class AndroidPushPlatform(
    private val context: Context,
) : PushPlatform {
    private val log = Logger.withTag("PushNotifications")
    private var handlers: PushPlatformHandlers? = null

    override suspend fun initialize(): PushPermissionStatus {
        PushNotificationChannels.create(context)
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PushPermissionStatus.Granted
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED -> PushPermissionStatus.Granted
            else -> PushPermissionStatus.NotDetermined
        }
    }

    override suspend fun getDeviceToken(): String? {
        return runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onFailure { error ->
            log.w(error) { "Failed to fetch FCM token" }
        }.getOrNull()
    }

    override fun showLocalNotification(notification: LocalPushNotification) {
        PushNotificationDisplay.show(context, notification)
    }

    override fun setHandlers(handlers: PushPlatformHandlers) {
        this.handlers = handlers
        PushNotificationBridge.onMessage = { data -> handlers.onMessage(data) }
        PushNotificationBridge.onTokenRefresh = { token -> handlers.onTokenRefresh(token) }
        PushNotificationBridge.onNotificationOpened = { data -> handlers.onNotificationOpened(data) }
        PushNotificationBridge.flushPendingHandlers()
    }

    override suspend fun deleteToken() {
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    override suspend fun onAppForeground() = Unit

    internal fun deliverMessage(data: Map<String, String>) {
        handlers?.onMessage?.invoke(data) ?: PushNotificationBridge.deliverMessage(data)
    }

    internal fun deliverTokenRefresh(token: String) {
        handlers?.onTokenRefresh?.invoke(token) ?: PushNotificationBridge.deliverTokenRefresh(token)
    }
}

actual fun createPushPlatform(): PushPlatform {
    val koin = KoinPlatformTools.defaultContext().getOrNull()
        ?: error("Koin must be started before creating PushPlatform")
    val context = koin.get<Context>()
    return AndroidPushPlatform(context.applicationContext)
}

actual fun currentPushPlatformType(): PushPlatformType = PushPlatformType.Android

actual fun currentDeviceName(): String? = Build.MODEL

actual fun currentAppVersion(): String? {
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return null
    val context = koin.get<Context>()
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    }.getOrNull()
}
