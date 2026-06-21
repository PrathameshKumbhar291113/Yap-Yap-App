package com.yapyap.core.push.di

import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.push.PushNavigationRouter
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.push.createPushPlatform
import com.yapyap.feature.devices.data.remote.DeviceApi
import com.yapyap.feature.devices.data.repository.DeviceRepository
import org.koin.dsl.module

val pushModule = module {
    single { createPushPlatform() }
    single { DeviceApi(get()) }
    single { DeviceRepository(get()) }
    single { PushMessageDeduplicator() }
    single { PushNavigationRouter() }
    single {
        PushNotificationCoordinator(
            pushPlatform = get(),
            deviceRepository = get(),
            tokenStorage = get(),
            observeAuthStateUseCase = get(),
            chatRealtimeClient = get(),
            activeConversationTracker = get(),
            deduplicator = get(),
            navigationRouter = get(),
            dispatcherProvider = get(),
        )
    }
}
