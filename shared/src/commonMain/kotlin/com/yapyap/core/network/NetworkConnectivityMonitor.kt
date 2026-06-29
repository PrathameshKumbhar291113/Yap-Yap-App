package com.yapyap.core.network

import kotlinx.coroutines.flow.StateFlow

interface NetworkConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}
