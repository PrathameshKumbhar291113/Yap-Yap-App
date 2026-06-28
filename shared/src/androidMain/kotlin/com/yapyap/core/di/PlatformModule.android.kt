package com.yapyap.core.di

import com.yapyap.core.datastore.EncryptedTokenStorage
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.AndroidPushPlatform
import com.yapyap.core.push.PushPlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TokenStorage> { EncryptedTokenStorage(androidContext()) }
    single<PushPlatform> { AndroidPushPlatform(androidContext().applicationContext) }
}
