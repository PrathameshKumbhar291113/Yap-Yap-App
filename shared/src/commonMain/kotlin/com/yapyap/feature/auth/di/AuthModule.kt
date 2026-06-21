package com.yapyap.feature.auth.di

import com.yapyap.core.network.HealthApi
import com.yapyap.feature.auth.data.remote.AuthApi
import com.yapyap.feature.auth.data.repository.AuthRepositoryImpl
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.LoginUseCase
import com.yapyap.feature.auth.domain.usecase.LogoutUseCase
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.auth.domain.usecase.RefreshTokenUseCase
import com.yapyap.feature.auth.domain.usecase.RegisterUseCase
import com.yapyap.feature.auth.domain.usecase.UpdateProfileUseCase
import com.yapyap.feature.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    single { AuthApi(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { RefreshTokenUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ObserveAuthStateUseCase(get()) }
    viewModel { AuthViewModel(get(), get(), get(), get()) }
}
