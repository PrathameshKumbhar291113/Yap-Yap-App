package com.yapyap.core.push

import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushDeliveryContext
import com.yapyap.core.push.model.PushDeliveryMode
import com.yapyap.core.push.model.PushInboundMessage
import com.yapyap.core.push.model.PushPermissionStatus
import com.yapyap.core.push.model.PushPlatformType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.UIKit.UIDevice

object IosPushBridge {
    private var requestPermissionHandler: (() -> Unit)? = null
    private var getTokenHandler: (() -> Unit)? = null
    private var deleteTokenHandler: (() -> Unit)? = null
    private var showNotificationHandler: ((String, String, String, String?, String?, String?) -> Unit)? = null
    private var dismissNotificationHandler: ((String) -> Unit)? = null

    fun registerRequestPermissionHandler(handler: () -> Unit) {
        requestPermissionHandler = handler
    }

    fun registerGetTokenHandler(handler: () -> Unit) {
        getTokenHandler = handler
    }

    fun registerDeleteTokenHandler(handler: () -> Unit) {
        deleteTokenHandler = handler
    }

    fun registerShowNotificationHandler(handler: (String, String, String, String?, String?, String?) -> Unit) {
        showNotificationHandler = handler
    }

    fun registerDismissNotificationHandler(handler: (String) -> Unit) {
        dismissNotificationHandler = handler
    }

    fun requestPermission() {
        requestPermissionHandler?.invoke()
    }

    fun fetchToken() {
        getTokenHandler?.invoke()
    }

    fun deleteToken() {
        deleteTokenHandler?.invoke()
    }

    fun showNotification(
        title: String,
        body: String,
        payloadJson: String,
        threadId: String?,
        notificationIdentifier: String?,
        summaryArgument: String?,
    ) {
        showNotificationHandler?.invoke(title, body, payloadJson, threadId, notificationIdentifier, summaryArgument)
    }

    fun dismissNotification(identifier: String) {
        dismissNotificationHandler?.invoke(identifier)
    }

    fun deliverPermissionResult(granted: Boolean) {
        IosPushPlatform.deliverPermissionResult(granted)
    }

    fun deliverToken(token: String?) {
        IosPushPlatform.deliverToken(token)
    }

    fun deliverMessage(message: PushInboundMessage) {
        PushNotificationBridge.deliverMessage(message)
    }

    fun deliverTokenRefresh(token: String) {
        PushNotificationBridge.deliverTokenRefresh(token)
    }

    fun deliverNotificationOpened(data: Map<String, String>) {
        PushNotificationBridge.deliverNotificationOpened(data)
    }
}

class IosPushPlatform : PushPlatform {
    private var handlers: PushPlatformHandlers? = null
    private var permissionContinuation: ((PushPermissionStatus) -> Unit)? = null
    private var tokenContinuation: ((String?) -> Unit)? = null

    override suspend fun initialize(): PushPermissionStatus {
        return suspendCancellableCoroutine { continuation ->
            permissionContinuation = { status ->
                if (continuation.isActive) continuation.resume(status)
            }
            IosPushBridge.requestPermission()
        }
    }

    override suspend fun getDeviceToken(): String? {
        return suspendCancellableCoroutine { continuation ->
            tokenContinuation = { token ->
                if (continuation.isActive) continuation.resume(token)
            }
            IosPushBridge.fetchToken()
        }
    }

    override fun showLocalNotification(notification: LocalPushNotification) {
        val payloadJson = notification.data.entries.joinToString(
            prefix = "{",
            postfix = "}",
        ) { (key, value) ->
            """"$key":"${value.replace("\"", "\\\"")}""""
        }
        val identifier = notification.tag ?: notification.conversationId?.let { "conv_$it" }
        val body = notification.messageHistory
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { it.text }
            ?: notification.body
        IosPushBridge.showNotification(
            title = notification.title,
            body = body,
            payloadJson = payloadJson,
            threadId = notification.threadId,
            notificationIdentifier = identifier,
            summaryArgument = notification.title,
        )
    }

    override fun dismissConversationNotification(conversationId: String) {
        IosPushBridge.dismissNotification("conv_$conversationId")
    }

    override fun setHandlers(handlers: PushPlatformHandlers) {
        this.handlers = handlers
        PushNotificationBridge.onMessage = { message -> handlers.onMessage(message) }
        PushNotificationBridge.onTokenRefresh = { token -> handlers.onTokenRefresh(token) }
        PushNotificationBridge.onNotificationOpened = { data -> handlers.onNotificationOpened(data) }
        PushNotificationBridge.flushPendingHandlers()
    }

    override suspend fun deleteToken() {
        IosPushBridge.deleteToken()
    }

    override suspend fun onAppForeground() {
        IosPushBridge.fetchToken()
    }

    override fun usesInAppForegroundNotification(): Boolean = false

    companion object {
        private var instance: IosPushPlatform? = null

        fun deliverPermissionResult(granted: Boolean) {
            val status = if (granted) PushPermissionStatus.Granted else PushPermissionStatus.Denied
            instance?.permissionContinuation?.invoke(status)
            instance?.permissionContinuation = null
        }

        fun deliverToken(token: String?) {
            instance?.tokenContinuation?.invoke(token)
            instance?.tokenContinuation = null
        }
    }

    init {
        instance = this
    }
}

actual fun createPushPlatform(): PushPlatform = IosPushPlatform()

actual fun currentPushPlatformType(): PushPlatformType = PushPlatformType.Ios

actual fun currentDeviceName(): String? = UIDevice.currentDevice.name

actual fun currentAppVersion(): String? =
    platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
