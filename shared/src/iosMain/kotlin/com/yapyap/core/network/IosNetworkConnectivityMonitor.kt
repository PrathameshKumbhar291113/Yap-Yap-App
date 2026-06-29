package com.yapyap.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosNetworkConnectivityMonitor : NetworkConnectivityMonitor {
    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        IosNetworkBridge.setOnlineListener { online ->
            _isOnline.value = online
        }
    }

    override fun start() {
        IosNetworkBridge.startMonitoring()
    }

    override fun stop() {
        IosNetworkBridge.stopMonitoring()
    }
}
