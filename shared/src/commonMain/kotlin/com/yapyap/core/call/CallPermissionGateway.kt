package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallType

enum class CallPermissionStatus {
    Granted,
    Denied,
    PermanentlyDenied,
}

interface CallPermissionGateway {
    suspend fun ensurePermissions(callType: CallType): CallPermissionStatus

    fun openAppSettings()
}
