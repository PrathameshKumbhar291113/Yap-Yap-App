package com.yapyap.core.call

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import com.yapyap.feature.call.domain.entity.CallState

interface ActiveCallSessionTracker {
    fun read(): String?

    fun clear()

    fun onStateChanged(state: CallState)
}

class SettingsActiveCallSessionTracker(
    private val settings: Settings,
) : ActiveCallSessionTracker {
    override fun read(): String? = settings.getStringOrNull(KEY)

    override fun clear() {
        settings.remove(KEY)
    }

    override fun onStateChanged(state: CallState) {
        val sessionId = state.activeSessionId()
        if (sessionId != null) {
            settings[KEY] = sessionId
        } else {
            settings.remove(KEY)
        }
    }

    private companion object {
        const val KEY = "active_call_session_id_v1"
    }
}

internal fun CallState.activeSessionId(): String? = when (this) {
    is CallState.Outgoing -> session.id
    is CallState.Incoming -> session.id
    is CallState.Connecting -> session.id
    is CallState.Connected -> session.id
    is CallState.Reconnecting -> session.id
    CallState.Idle,
    is CallState.Ended,
    is CallState.Failed,
    -> null
}
