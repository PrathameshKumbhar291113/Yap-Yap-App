package com.yapyap.core.di

import com.yapyap.core.database.DatabaseDriverFactory
import com.yapyap.core.network.IosNetworkConnectivityMonitor
import com.yapyap.core.network.NetworkConnectivityMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun networkConnectivityMonitorModule(): Module = module {
    single<NetworkConnectivityMonitor> { IosNetworkConnectivityMonitor() }
    single { DatabaseDriverFactory() }
}
