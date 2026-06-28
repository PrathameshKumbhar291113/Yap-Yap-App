package com.yapyap.core.push

import com.yapyap.core.push.model.PushDeliveryContext
import com.yapyap.core.push.model.PushDeliveryMode
import com.yapyap.core.push.model.PushMessageType

/**
 * Central rules for when to show local/in-app notifications vs relying on the OS tray.
 */
object PushLocalNotificationPolicy {

    fun shouldSuppressLocalNotification(
        delivery: PushDeliveryContext,
        appInForeground: Boolean,
    ): Boolean {
        if (delivery.osNotificationHandled) return true
        if (
            delivery.mode == PushDeliveryMode.Realtime &&
            !appInForeground &&
            suppressLocalTrayForRealtimeBackground()
        ) {
            return true
        }
        return false
    }

    fun shouldShowInAppBanner(
        usesInAppForegroundNotification: Boolean,
        appInForeground: Boolean,
        messageType: PushMessageType,
    ): Boolean {
        return appInForeground &&
            usesInAppForegroundNotification &&
            messageType == PushMessageType.NewMessage
    }

    fun shouldShowLocalTray(
        delivery: PushDeliveryContext,
        appInForeground: Boolean,
        messageType: PushMessageType,
        usesInAppForegroundNotification: Boolean,
    ): Boolean {
        if (shouldSuppressLocalNotification(delivery, appInForeground)) return false
        if (shouldShowInAppBanner(usesInAppForegroundNotification, appInForeground, messageType)) {
            return false
        }
        if (appInForeground && messageType == PushMessageType.NewMessage && usesInAppForegroundNotification) {
            return false
        }
        // iOS foreground remote alerts are presented by UNUserNotificationCenterDelegate.
        if (appInForeground && !usesInAppForegroundNotification && delivery.osNotificationHandled) {
            return false
        }
        return true
    }
}
