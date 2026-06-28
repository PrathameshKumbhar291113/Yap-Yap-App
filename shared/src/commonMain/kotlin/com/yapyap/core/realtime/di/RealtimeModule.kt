package com.yapyap.core.realtime.di

import com.yapyap.core.realtime.ActiveConversationTracker
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.ChatRealtimeRepository
import com.yapyap.core.realtime.IncomingMessageNotifier
import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.core.realtime.MessageDeliveryAcknowledgementService
import com.yapyap.core.realtime.PresenceStore
import com.yapyap.core.realtime.RealtimeCoordinator
import com.yapyap.core.realtime.SocketEventParser
import com.yapyap.core.realtime.TokenRefreshNotifier
import com.yapyap.core.realtime.TypingStore
import com.yapyap.core.realtime.createChatRealtimeClient
import org.koin.dsl.module

val realtimeModule = module {
    single { SocketEventParser() }
    single { TypingStore() }
    single { PresenceStore() }
    single { ActiveConversationTracker() }
    single { IncomingMessageNotifier() }
    single<MessageDeliveryAcknowledgementGateway> {
        MessageDeliveryAcknowledgementService(
            chatRealtimeClient = get(),
            observeAuthStateUseCase = get(),
            dispatcherProvider = get(),
        )
    }
    single<ChatRealtimeClient> {
        createChatRealtimeClient(
            socketEventParser = get(),
            tokenStorage = get(),
            dispatcherProvider = get(),
        )
    }
    single {
        ChatRealtimeRepository(
            chatRealtimeClient = get(),
            chatRepository = get(),
            conversationRepository = get(),
            typingStore = get(),
            presenceStore = get(),
            observeAuthStateUseCase = get(),
            activeConversationTracker = get(),
            deduplicator = get(),
            pushNotificationCoordinator = lazy { get() },
            deliveryAckService = get(),
        )
    }
    single {
        RealtimeCoordinator(
            observeAuthStateUseCase = get(),
            tokenStorage = get(),
            chatRealtimeClient = get(),
            chatRealtimeRepository = get(),
            conversationRepository = get(),
            deliveryAckService = get(),
            sessionEvents = get(),
            tokenRefreshNotifier = get(),
            dispatcherProvider = get(),
        )
    }
}
