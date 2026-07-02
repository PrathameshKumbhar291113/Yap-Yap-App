package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallState

class InMemoryActiveCallSessionTracker : ActiveCallSessionTracker {
    private var sessionId: String? = null

    override fun read(): String? = sessionId

    override fun clear() {
        sessionId = null
    }

    override fun onStateChanged(state: CallState) {
        sessionId = state.activeSessionId()
    }
}
