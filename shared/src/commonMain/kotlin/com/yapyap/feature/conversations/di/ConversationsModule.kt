package com.yapyap.feature.conversations.di

import com.yapyap.feature.conversations.data.remote.ConversationApi
import com.yapyap.feature.conversations.data.repository.ConversationRepositoryImpl
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import com.yapyap.feature.conversations.domain.usecase.GetConversationsUseCase
import com.yapyap.feature.conversations.domain.usecase.StartDirectChatUseCase
import com.yapyap.feature.conversations.presentation.ConversationsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val conversationsModule = module {
    single { ConversationApi(get()) }
    single<ConversationRepository> { ConversationRepositoryImpl(get(), get()) }
    factory { GetConversationsUseCase(get()) }
    factory { StartDirectChatUseCase(get()) }
    viewModel { ConversationsViewModel(get(), get()) }
}
