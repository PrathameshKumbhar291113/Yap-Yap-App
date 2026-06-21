package com.yapyap.feature.profile.di

import com.yapyap.feature.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel {
        ProfileViewModel(
            observeAuthStateUseCase = get(),
            updateProfileUseCase = get(),
            logoutUseCase = get(),
            chatRealtimeClient = get(),
            pushNotificationCoordinator = get(),
        )
    }
}
