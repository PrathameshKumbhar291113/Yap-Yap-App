package com.yapyap.feature.call.di

import com.yapyap.core.call.CallPermissionGateway
import com.yapyap.feature.call.data.remote.CallApi
import com.yapyap.feature.call.data.repository.CallMediaRepositoryImpl
import com.yapyap.feature.call.data.repository.CallRepositoryImpl
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import com.yapyap.feature.call.domain.repository.CallRepository
import com.yapyap.feature.call.domain.usecase.AnswerIncomingCallUseCase
import com.yapyap.feature.call.domain.usecase.EndCallUseCase
import com.yapyap.feature.call.domain.usecase.ObserveActiveCallUseCase
import com.yapyap.feature.call.domain.usecase.RejectIncomingCallUseCase
import com.yapyap.feature.call.domain.usecase.StartOutgoingCallUseCase
import com.yapyap.feature.call.data.repository.CallLogRepositoryImpl
import com.yapyap.feature.call.domain.repository.CallLogRepository
import com.yapyap.feature.call.domain.usecase.ObserveCallLogUseCase
import com.yapyap.feature.call.domain.usecase.RedialCallUseCase
import com.yapyap.feature.call.domain.usecase.RefreshCallTokenUseCase
import com.yapyap.feature.call.domain.usecase.SwitchAudioRouteUseCase
import com.yapyap.feature.call.domain.usecase.SwitchCameraUseCase
import com.yapyap.feature.call.domain.usecase.ToggleCameraUseCase
import com.yapyap.feature.call.domain.usecase.ToggleMuteUseCase
import com.yapyap.feature.call.domain.usecase.ToggleSpeakerUseCase
import com.yapyap.feature.call.presentation.CallLogViewModel
import com.yapyap.feature.call.presentation.CallPermissionPromptController
import com.yapyap.feature.call.presentation.CallUiPresentationController
import com.yapyap.feature.call.presentation.CallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val callFeatureModule = module {
    single { CallUiPresentationController() }
    single { CallPermissionPromptController(get<CallPermissionGateway>()) }
    single { CallApi(get()) }
    single<CallRepository> {
        CallRepositoryImpl(
            callApi = get(),
            callSignalingClient = get(),
            credentialsStore = get(),
        )
    }
    single<CallMediaRepository> { CallMediaRepositoryImpl(mediaController = get()) }
    factory { StartOutgoingCallUseCase(get()) }
    factory { AnswerIncomingCallUseCase(get()) }
    factory { RejectIncomingCallUseCase(get()) }
    factory { EndCallUseCase(get()) }
    factory { ObserveActiveCallUseCase(get()) }
    factory { ToggleMuteUseCase(get()) }
    factory { ToggleCameraUseCase(get()) }
    factory { ToggleSpeakerUseCase(get()) }
    factory { SwitchAudioRouteUseCase(get()) }
    factory { SwitchCameraUseCase(get()) }
    single<CallLogRepository> { CallLogRepositoryImpl(callApi = get(), settings = get()) }
    factory { ObserveCallLogUseCase(get()) }
    factory { RedialCallUseCase(get()) }
    factory { RefreshCallTokenUseCase(get()) }
    viewModel {
        CallLogViewModel(
            observeCallLogUseCase = get(),
            redialCallUseCase = get(),
            startOutgoingCallUseCase = get(),
            permissionPromptController = get(),
        )
    }
    viewModel {
        CallViewModel(
            observeActiveCallUseCase = get(),
            observeAuthStateUseCase = get(),
            answerIncomingCallUseCase = get(),
            rejectIncomingCallUseCase = get(),
            endCallUseCase = get(),
            toggleMuteUseCase = get(),
            toggleCameraUseCase = get(),
            toggleSpeakerUseCase = get(),
            switchCameraUseCase = get(),
            callCoordinator = get(),
            permissionPromptController = get(),
            presentationController = get(),
            callMediaRepository = get(),
        )
    }
}
