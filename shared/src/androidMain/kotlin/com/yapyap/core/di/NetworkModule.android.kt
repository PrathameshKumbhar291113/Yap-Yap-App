package com.yapyap.core.di

import com.yapyap.core.database.DatabaseDriverFactory
import com.yapyap.core.network.AndroidNetworkConnectivityMonitor
import com.yapyap.core.network.NetworkConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun networkConnectivityMonitorModule(): Module = module {
    single<NetworkConnectivityMonitor> { AndroidNetworkConnectivityMonitor(androidContext()) }
    single { DatabaseDriverFactory(androidContext()) }
}
