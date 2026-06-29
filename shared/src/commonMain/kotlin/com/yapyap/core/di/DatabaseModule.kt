package com.yapyap.core.di

import com.yapyap.core.database.DatabaseDriverFactory
import com.yapyap.core.database.YapYapDatabase
import com.yapyap.core.database.createDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single<YapYapDatabase> { get<DatabaseDriverFactory>().createDatabase() }
}

expect fun networkConnectivityMonitorModule(): Module
