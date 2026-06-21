package com.yapyap.core.push

/**
 * Bridge between platform push layers (FCM service, iOS AppDelegate) and [PushNotificationCoordinator].
 */
object PushNotificationBridge {
    var onMessage: ((Map<String, String>) -> Unit)? = null
    var onTokenRefresh: ((String) -> Unit)? = null
    var onNotificationOpened: ((Map<String, String>) -> Unit)? = null

    private var pendingToken: String? = null
    private var pendingMessage: Map<String, String>? = null
    private var pendingNotificationOpen: Map<String, String>? = null

    fun deliverMessage(data: Map<String, String>) {
        val handler = onMessage
        if (handler != null) {
            handler(data)
        } else {
            pendingMessage = data
        }
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
        val handler = onNotificationOpened
        if (handler != null) {
            handler(data)
        } else {
            pendingNotificationOpen = data
        }
    }

    fun flushPendingHandlers() {
        pendingToken?.let { token ->
            pendingToken = null
            onTokenRefresh?.invoke(token)
        }
        pendingMessage?.let { data ->
            pendingMessage = null
            onMessage?.invoke(data)
        }
        pendingNotificationOpen?.let { data ->
            pendingNotificationOpen = null
            onNotificationOpened?.invoke(data)
        }
    }
}
