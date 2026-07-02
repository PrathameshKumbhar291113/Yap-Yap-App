package com.yapyap.core.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Entry point for Android ConnectionService / iOS CallKit to invoke call actions
 * without duplicating signaling logic.
 */
object CallNativeBridge {
    private var coordinator: CallNativeCoordinator? = null

    internal fun bind(coordinator: CallNativeCoordinator) {
        this.coordinator = coordinator
    }

    fun onAnswerFromNative(callSessionId: String) {
        coordinator?.onAnswerFromNative(callSessionId)
    }

    fun onRejectFromNative(callSessionId: String) {
        coordinator?.onRejectFromNative(callSessionId)
    }

    fun onEndFromNative(callSessionId: String) {
        coordinator?.onEndFromNative(callSessionId)
    }

    fun onRestoreFromNative(callSessionId: String) {
        coordinator?.onRestoreFromNative(callSessionId)
    }
}
