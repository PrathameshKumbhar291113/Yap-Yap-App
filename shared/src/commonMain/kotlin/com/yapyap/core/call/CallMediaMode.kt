package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallRoomCredentials

object CallMediaMode {
    fun isStub(credentials: CallRoomCredentials): Boolean {
        return credentials.providerToken.startsWith("stub-") ||
            credentials.livekitUrl.contains("stub", ignoreCase = true)
    }
}
