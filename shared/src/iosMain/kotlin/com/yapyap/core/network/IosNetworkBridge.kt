package com.yapyap.core.network

/**
 * Swift ↔ Kotlin bridge for [NWPathMonitor] (see `YapNetworkMonitor.swift`).
 * Avoids calling Network.framework directly from Kotlin/Native, which can crash at runtime.
 */
object IosNetworkBridge {
    private var onlineListener: ((Boolean) -> Unit)? = null
    private var startHandler: (() -> Unit)? = null
    private var stopHandler: (() -> Unit)? = null

    fun setOnlineListener(listener: (Boolean) -> Unit) {
        onlineListener = listener
    }

    fun registerStartHandler(handler: () -> Unit) {
        startHandler = handler
    }

    fun registerStopHandler(handler: () -> Unit) {
        stopHandler = handler
    }

    fun deliverOnlineStatus(online: Boolean) {
        onlineListener?.invoke(online)
    }

    internal fun startMonitoring() {
        startHandler?.invoke()
    }

    internal fun stopMonitoring() {
        stopHandler?.invoke()
    }
}
