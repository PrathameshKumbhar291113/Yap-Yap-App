package com.yapyap.core.network

import kotlinx.coroutines.flow.SharedFlow

interface SessionEventPublisher {
    val sessionExpired: SharedFlow<Unit>
    fun emitSessionExpired()
}
