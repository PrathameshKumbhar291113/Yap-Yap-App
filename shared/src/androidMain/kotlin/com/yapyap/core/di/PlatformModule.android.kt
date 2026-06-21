package com.yapyap.core.di

import com.yapyap.core.datastore.EncryptedTokenStorage
import com.yapyap.core.datastore.TokenStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TokenStorage> { EncryptedTokenStorage(androidContext()) }
}
