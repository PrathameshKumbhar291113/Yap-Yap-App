package com.yapyap.feature.call.presentation

import com.yapyap.core.call.CallPermissionGateway
import com.yapyap.core.call.CallPermissionStatus
import com.yapyap.feature.call.domain.entity.CallType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared permission prompt state for call entry points (overlay, chat, call log).
 */
class CallPermissionPromptController(
    private val gateway: CallPermissionGateway,
) {
    private val _pendingStatus = MutableStateFlow<CallPermissionStatus?>(null)
    private val _pendingCallType = MutableStateFlow<CallType?>(null)

    val pendingStatus: StateFlow<CallPermissionStatus?> = _pendingStatus.asStateFlow()
    val pendingCallType: StateFlow<CallType?> = _pendingCallType.asStateFlow()

    suspend fun ensurePermissions(callType: CallType): Boolean {
        val status = gateway.ensurePermissions(callType)
        if (status == CallPermissionStatus.Granted) {
            return true
        }
        _pendingCallType.value = callType
        _pendingStatus.value = status
        return false
    }

    fun dismiss() {
        _pendingStatus.value = null
        _pendingCallType.value = null
    }

    suspend fun retryPermissions(callType: CallType): Boolean {
        val status = gateway.ensurePermissions(callType)
        if (status == CallPermissionStatus.Granted) {
            dismiss()
            return true
        }
        _pendingCallType.value = callType
        _pendingStatus.value = status
        return false
    }

    fun openSettings() {
        gateway.openAppSettings()
        dismiss()
    }
}
