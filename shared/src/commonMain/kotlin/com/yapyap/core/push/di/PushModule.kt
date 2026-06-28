package com.yapyap.core.push.di

import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.push.PushNavigationRouter
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.push.PushPlatform
import com.yapyap.feature.devices.data.remote.DeviceApi
import com.yapyap.feature.devices.data.repository.DeviceRepository
import org.koin.dsl.module

val pushModule = module {
    single { DeviceApi(get()) }
    single { DeviceRepository(get()) }
    single { PushMessageDeduplicator() }
    single { PushNavigationRouter() }
    single {
        PushNotificationCoordinator(
            pushPlatform = get<PushPlatform>(),
            deviceRepository = get(),
            tokenStorage = get(),
            observeAuthStateUseCase = get(),
            activeConversationTracker = get(),
            deduplicator = get(),
            navigationRouter = get(),
            conversationRepository = get(),
            chatRepository = get(),
            incomingMessageNotifier = get(),
            deliveryAckService = get(),
            dispatcherProvider = get(),
        )
    }
}
