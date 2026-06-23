package com.yapyap.core.push

object IosAppLifecycleBridge {
    private var foregroundCallback: (() -> Unit)? = null
    private var backgroundCallback: (() -> Unit)? = null

    var isInForeground: Boolean = true

    fun registerCallbacks(onForeground: () -> Unit, onBackground: () -> Unit) {
        foregroundCallback = onForeground
        backgroundCallback = onBackground
    }

    fun notifyWillResignActive() {
        isInForeground = false
    }

    fun notifyDidEnterBackground() {
        isInForeground = false
        backgroundCallback?.invoke()
    }

    fun notifyForeground() {
        isInForeground = true
        foregroundCallback?.invoke()
    }
}

internal actual fun registerAppLifecycleCallbacks(
    onForeground: () -> Unit,
    onBackground: () -> Unit,
) {
    IosAppLifecycleBridge.registerCallbacks(onForeground, onBackground)
}

internal actual fun isAppInForeground(): Boolean = IosAppLifecycleBridge.isInForeground
