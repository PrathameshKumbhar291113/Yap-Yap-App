package com.yapyap.feature.call.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-only presentation state for the call overlay (expand / minimize).
 * Does not affect call media or signaling.
 */
class CallUiPresentationController {
    private val _isCallScreenExpanded = MutableStateFlow(false)

    val isCallScreenExpanded: StateFlow<Boolean> = _isCallScreenExpanded.asStateFlow()

    fun expandCallScreen() {
        _isCallScreenExpanded.value = true
    }

    fun minimizeCallScreen() {
        _isCallScreenExpanded.value = false
    }

    fun reset() {
        _isCallScreenExpanded.value = false
    }
}
