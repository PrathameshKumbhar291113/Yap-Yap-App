package com.yapyap.core.di

import com.yapyap.core.util.DefaultDispatcherProvider
import com.yapyap.core.util.DispatcherProvider
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val coreModule = module {
    includes(platformModule)
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
