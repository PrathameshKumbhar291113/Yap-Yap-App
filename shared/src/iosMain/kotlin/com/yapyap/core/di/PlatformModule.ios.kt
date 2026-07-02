package com.yapyap.core.di

import com.yapyap.core.call.CallMediaController
import com.yapyap.core.call.CallNativeController
import com.yapyap.core.call.CallPermissionGateway
import com.yapyap.core.call.IosCallNativeController
import com.yapyap.core.call.CallRingtoneController
import com.yapyap.core.call.IosCallRingtoneController
import com.yapyap.core.call.FakeCallMediaController
import com.yapyap.core.call.IosCallMediaController
import com.yapyap.core.call.IosCallPermissionGateway
import com.yapyap.core.call.RoutedCallMediaController
import com.yapyap.core.datastore.KeychainTokenStorage
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.IosPushPlatform
import com.yapyap.core.push.PushPlatform
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TokenStorage> { KeychainTokenStorage() }
    single<PushPlatform> { IosPushPlatform() }
    single<CallPermissionGateway> { IosCallPermissionGateway() }
    single<CallNativeController> { IosCallNativeController() }
    single<CallRingtoneController> { IosCallRingtoneController() }
    single { FakeCallMediaController() }
    single<CallMediaController> {
        RoutedCallMediaController(
            liveMediaController = IosCallMediaController(dispatcherProvider = get()),
            stubMediaController = get(),
            dispatcherProvider = get(),
        )
    }
}
