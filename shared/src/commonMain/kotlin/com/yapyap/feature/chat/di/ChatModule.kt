package com.yapyap.feature.chat.di

import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.data.repository.ChatRepositoryImpl
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.chat.domain.usecase.LoadMoreMessagesUseCase
import com.yapyap.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveChatUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveTypingStateUseCase
import com.yapyap.feature.chat.domain.usecase.SendMessageUseCase
import com.yapyap.feature.chat.presentation.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val chatModule = module {
    single { ChatApi(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
    factory { ObserveChatUseCase(get()) }
    factory { ObserveTypingStateUseCase(get()) }
    factory { SendMessageUseCase(get()) }
    factory { LoadMoreMessagesUseCase(get()) }
    factory { MarkConversationReadUseCase(get(), get()) }
    viewModel { (conversationId: Int) ->
        ChatViewModel(
            conversationId = conversationId,
            getConversationsUseCase = get(),
            observeChatUseCase = get(),
            loadMoreMessagesUseCase = get(),
            sendMessageUseCase = get(),
            markConversationReadUseCase = get(),
            observeAuthStateUseCase = get(),
            observeTypingStateUseCase = get(),
            chatRealtimeClient = get(),
            activeConversationTracker = get(),
        )
    }
}
