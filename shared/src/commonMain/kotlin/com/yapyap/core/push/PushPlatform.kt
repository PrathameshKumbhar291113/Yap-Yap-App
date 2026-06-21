package com.yapyap.core.push

import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushPermissionStatus

data class PushPlatformHandlers(
    val onMessage: (Map<String, String>) -> Unit,
    val onTokenRefresh: (String) -> Unit,
    val onNotificationOpened: (Map<String, String>) -> Unit,
)

interface PushPlatform {
    suspend fun initialize(): PushPermissionStatus
    suspend fun getDeviceToken(): String?
    fun showLocalNotification(notification: LocalPushNotification)
    fun setHandlers(handlers: PushPlatformHandlers)
    suspend fun deleteToken()
    suspend fun onAppForeground()
}

expect fun createPushPlatform(): PushPlatform

expect fun currentPushPlatformType(): com.yapyap.core.push.model.PushPlatformType

expect fun currentDeviceName(): String?

expect fun currentAppVersion(): String?
