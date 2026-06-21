package com.yapyap.core.push

object IosAppLifecycleBridge {
    private var foregroundCallback: (() -> Unit)? = null

    fun registerForegroundCallback(callback: () -> Unit) {
        foregroundCallback = callback
    }

    fun notifyForeground() {
        foregroundCallback?.invoke()
    }
}

internal actual fun registerPushAppForegroundCallback(callback: () -> Unit) {
    IosAppLifecycleBridge.registerForegroundCallback(callback)
}
