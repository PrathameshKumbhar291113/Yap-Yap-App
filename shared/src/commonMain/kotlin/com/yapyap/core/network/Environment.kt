package com.yapyap.core.network

import com.yapyap.core.config.AppBuildConfig

object Environment {
    val BASE_URL: String get() = DevHostResolver.resolveBaseUrl()
    val SOCKET_URL: String get() = DevHostResolver.resolveSocketUrl()
    val isDebug: Boolean get() = AppBuildConfig.IS_DEBUG
}
