package com.yapyap.core.di

import com.yapyap.core.call.AndroidCallMediaController
import com.yapyap.core.call.AndroidCallNativeController
import com.yapyap.core.call.AndroidCallPermissionGateway
import com.yapyap.core.call.CallNativeController
import com.yapyap.core.call.CallMediaController
import com.yapyap.core.call.CallPermissionGateway
import com.yapyap.core.call.CallRingtoneController
import com.yapyap.core.call.AndroidCallRingtoneController
import com.yapyap.core.call.FakeCallMediaController
import com.yapyap.core.call.RoutedCallMediaController
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
    single<CallPermissionGateway> { AndroidCallPermissionGateway(androidContext()) }
    single<CallNativeController> { AndroidCallNativeController(androidContext().applicationContext) }
    single<CallRingtoneController> { AndroidCallRingtoneController(androidContext().applicationContext) }
    single { FakeCallMediaController() }
    single<CallMediaController> {
        RoutedCallMediaController(
            liveMediaController = AndroidCallMediaController(
                appContext = androidContext().applicationContext,
                dispatcherProvider = get(),
            ),
            stubMediaController = get(),
            dispatcherProvider = get(),
        )
    }
}
