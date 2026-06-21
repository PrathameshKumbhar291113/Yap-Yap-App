package com.yapyap.core.di

import com.yapyap.core.config.AppBuildConfig
import com.yapyap.core.util.YapLogger
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    YapLogger.install(AppBuildConfig.IS_DEBUG)

    startKoin {
        appDeclaration()
        modules(appModules)
    }
}
