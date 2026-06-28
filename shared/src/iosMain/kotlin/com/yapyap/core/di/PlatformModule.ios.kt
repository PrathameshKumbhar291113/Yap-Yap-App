package com.yapyap.core.di

import com.yapyap.core.datastore.KeychainTokenStorage
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.IosPushPlatform
import com.yapyap.core.push.PushPlatform
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TokenStorage> { KeychainTokenStorage() }
    single<PushPlatform> { IosPushPlatform() }
}
