package com.yapyap.feature.chat.di

import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxGateway
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxPersistence
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxService
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxStore
import com.yapyap.feature.chat.data.outbox.OutgoingMessageRestGateway
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.data.repository.ChatRepositoryImpl
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter
import com.yapyap.feature.chat.domain.usecase.DeleteOutgoingMessageUseCase
import com.yapyap.feature.chat.domain.usecase.LoadMoreMessagesUseCase
import com.yapyap.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveChatUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveTypingStateUseCase
import com.yapyap.feature.chat.domain.usecase.RetryOutgoingMessageUseCase
import com.yapyap.feature.chat.domain.usecase.SendMessageUseCase
import com.yapyap.feature.chat.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    single { ChatApi(get()) }
    single<OutgoingMessageOutboxPersistence> { OutgoingMessageOutboxStore(get()) }
    single<ChatRepositoryImpl> {
        ChatRepositoryImpl(
            chatApi = get(),
            chatRealtimeClient = get(),
            deliveryAckService = get(),
            outboxGateway = lazy { get() },
        )
    }
    single<OutgoingMessageOutboxGateway> {
        val chatApi = get<ChatApi>()
        OutgoingMessageOutboxService(
            store = get(),
            cacheWriterProvider = { get<OutgoingMessageCacheWriter>() },
            chatApi = OutgoingMessageRestGateway { conversationId, request ->
                chatApi.sendMessage(conversationId, request)
            },
            chatRealtimeClient = get(),
            networkMonitor = get(),
            observeAuthStateUseCase = get(),
            dispatcherProvider = get(),
        )
    }
    single<ChatRepository> { get<ChatRepositoryImpl>() }
    single<OutgoingMessageCacheWriter> { get<ChatRepositoryImpl>() }
    factory { ObserveChatUseCase(get()) }
    factory { ObserveTypingStateUseCase(get()) }
    factory { SendMessageUseCase(get()) }
    factory { RetryOutgoingMessageUseCase(get()) }
    factory { DeleteOutgoingMessageUseCase(get()) }
    factory { LoadMoreMessagesUseCase(get()) }
    factory { MarkConversationReadUseCase(get(), get()) }
    viewModel { (conversationId: Int) ->
        ChatViewModel(
            conversationId = conversationId,
            getConversationsUseCase = get(),
            observeChatUseCase = get(),
            loadMoreMessagesUseCase = get(),
            sendMessageUseCase = get(),
            retryOutgoingMessageUseCase = get(),
            deleteOutgoingMessageUseCase = get(),
            markConversationReadUseCase = get(),
            observeAuthStateUseCase = get(),
            observeTypingStateUseCase = get(),
            chatRealtimeClient = get(),
            activeConversationTracker = get(),
            pushNotificationCoordinator = get(),
            receiverRepairGateway = get(),
            startOutgoingCallUseCase = get(),
            permissionPromptController = get(),
        )
    }
}
