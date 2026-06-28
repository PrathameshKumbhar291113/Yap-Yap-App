package com.yapyap.core.di

import com.yapyap.core.config.AppBuildConfig
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.network.Environment
import com.yapyap.core.util.YapLogger
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    YapLogger.install(AppBuildConfig.IS_DEBUG)

    if (AppBuildConfig.IS_DEBUG) {
        RuntimeDiagnostics.event(
            "Network",
            "BASE_URL=${Environment.BASE_URL} SOCKET_URL=${Environment.SOCKET_URL}",
        )
    }

    startKoin {
        appDeclaration()
        modules(appModules)
    }
}
