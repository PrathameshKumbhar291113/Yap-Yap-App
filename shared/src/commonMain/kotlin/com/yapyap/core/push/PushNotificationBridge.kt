package com.yapyap.core.push

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.model.PushInboundMessage

/**
 * Bridge between platform push layers (FCM service, iOS AppDelegate) and [PushNotificationCoordinator].
 */
object PushNotificationBridge {
    var onMessage: ((PushInboundMessage) -> Unit)? = null
    var onTokenRefresh: ((String) -> Unit)? = null
    var onNotificationOpened: ((Map<String, String>) -> Unit)? = null

    private var pendingToken: String? = null
    private var pendingMessage: PushInboundMessage? = null
    private var pendingNotificationOpen: Map<String, String>? = null

    fun deliverMessage(message: PushInboundMessage) {
        RuntimeDiagnostics.enter(
            "PushBridge",
            "PushNotificationBridge.deliverMessage",
            "mode=${message.delivery.mode} osHandled=${message.delivery.osNotificationHandled} " +
                "dataKeys=${message.data.keys.sorted()} conversationId=${message.data["conversationId"]} " +
                "messageId=${message.data["messageId"]} handlerRegistered=${onMessage != null}",
        )
        val handler = onMessage
        if (handler != null) {
            handler(message)
        } else {
            RuntimeDiagnostics.event("PushBridge", "QUEUE message handler=null pendingMessage stored")
            pendingMessage = message
        }
    }

    /** Legacy entry — treated as data-only delivery. */
    fun deliverMessage(data: Map<String, String>) {
        deliverMessage(PushInboundMessage(data = data))
    }

    fun deliverTokenRefresh(token: String) {
        val handler = onTokenRefresh
        if (handler != null) {
            handler(token)
        } else {
            pendingToken = token
        }
    }

    fun deliverNotificationOpened(data: Map<String, String>) {
        RuntimeDiagnostics.enter(
            "PushBridge",
            "PushNotificationBridge.deliverNotificationOpened",
            "dataKeys=${data.keys.sorted()} data=$data handlerRegistered=${onNotificationOpened != null}",
        )
        val handler = onNotificationOpened
        if (handler != null) {
            handler(data)
        } else {
            RuntimeDiagnostics.event("PushBridge", "QUEUE notificationOpen handler=null pending stored")
            pendingNotificationOpen = data
        }
    }

    fun flushPendingHandlers() {
        RuntimeDiagnostics.enter("PushBridge", "PushNotificationBridge.flushPendingHandlers")
        pendingToken?.let { token ->
            pendingToken = null
            onTokenRefresh?.invoke(token)
        }
        pendingMessage?.let { message ->
            pendingMessage = null
            onMessage?.invoke(message)
        }
        pendingNotificationOpen?.let { data ->
            pendingNotificationOpen = null
            onNotificationOpened?.invoke(data)
        }
    }
}
