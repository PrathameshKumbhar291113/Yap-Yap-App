package com.yapyap.core.network

import com.yapyap.core.config.AppBuildConfig

object Environment {
    val BASE_URL: String get() = AppBuildConfig.BASE_URL
    val isDebug: Boolean get() = AppBuildConfig.IS_DEBUG
}
