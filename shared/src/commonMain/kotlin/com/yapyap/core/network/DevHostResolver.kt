package com.yapyap.core.network

import com.yapyap.core.config.AppBuildConfig

internal object DevHostResolver {
    fun resolveBaseUrl(): String {
        if (!AppBuildConfig.IS_DEBUG) return AppBuildConfig.BASE_URL
        return if (isVirtualDevice()) {
            AppBuildConfig.DEBUG_VIRTUAL_BASE_URL
        } else {
            AppBuildConfig.DEBUG_PHYSICAL_BASE_URL
        }
    }

    fun resolveSocketUrl(): String {
        if (!AppBuildConfig.IS_DEBUG) return AppBuildConfig.SOCKET_URL
        return if (isVirtualDevice()) {
            AppBuildConfig.DEBUG_VIRTUAL_SOCKET_URL
        } else {
            AppBuildConfig.DEBUG_PHYSICAL_SOCKET_URL
        }
    }
}
