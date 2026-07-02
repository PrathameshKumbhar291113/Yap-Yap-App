package com.yapyap.core.push.di

import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.push.ConversationNotificationStore
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
    single { ConversationNotificationStore() }
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
            chatRealtimeClient = get(),
            receiverRepairGateway = lazy { get() },
            callCoordinator = lazy { get() },
            callNativeCoordinator = lazy { get() },
            notificationStore = get(),
            dispatcherProvider = get(),
        )
    }
}
