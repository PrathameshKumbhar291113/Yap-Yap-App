package com.yapyap.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultSessionEventPublisher : SessionEventPublisher {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    override fun emitSessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
