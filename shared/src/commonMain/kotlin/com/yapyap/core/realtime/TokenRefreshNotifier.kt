package com.yapyap.core.realtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TokenRefreshNotifier {
    private val _accessTokenRefreshed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val accessTokenRefreshed: SharedFlow<String> = _accessTokenRefreshed.asSharedFlow()

    fun notifyAccessTokenRefreshed(accessToken: String) {
        _accessTokenRefreshed.tryEmit(accessToken)
    }
}
