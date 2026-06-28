package com.yapyap.core.push.model

enum class PushDeliveryMode {
    /** Legacy / background data message — app must show tray notification when appropriate. */
    DataOnly,

    /** FCM notification block only — OS displays tray item. */
    NotificationOnly,

    /** notification + data — OS displays in background; app handles foreground UX. */
    Hybrid,

    /** Delivered via Socket.IO — never duplicate FCM tray when backgrounded. */
    Realtime,
}

/**
 * Describes how the platform delivered an inbound push so the coordinator can avoid duplicate tray UI.
 */
data class PushDeliveryContext(
    val mode: PushDeliveryMode = PushDeliveryMode.DataOnly,
    /**
     * True when the OS has already shown (or will show) a system notification for this message.
     * Local notifications must be suppressed.
     */
    val osNotificationHandled: Boolean = false,
)

data class PushInboundMessage(
    val data: Map<String, String>,
    val delivery: PushDeliveryContext = PushDeliveryContext(),
)
