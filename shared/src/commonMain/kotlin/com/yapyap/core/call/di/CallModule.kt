package com.yapyap.core.call.di

import com.yapyap.core.call.ActiveCallSessionTracker
import com.yapyap.core.call.SettingsActiveCallSessionTracker
import com.yapyap.core.call.CallCoordinator
import com.yapyap.core.call.CallEntryHandler
import com.yapyap.core.call.CallInviteDeduplicator
import com.yapyap.core.call.CallMediaCredentialsStore
import com.yapyap.core.call.CallMediaLifecycle
import com.yapyap.core.call.CallMediaSessionBinder
import com.yapyap.core.call.CallNativeCoordinator
import com.yapyap.core.call.CallReconnectHandler
import com.yapyap.core.call.CallRingtoneCoordinator
import com.yapyap.core.call.CallSessionReconciler
import com.yapyap.core.call.CallSessionStore
import com.yapyap.core.call.CallSignalingClient
import com.yapyap.core.call.CallSignalingClientImpl
import com.yapyap.core.call.CallSignalingHandler
import com.yapyap.core.call.CallSignalingRepository
import com.yapyap.core.call.CallSignalingRepositoryImpl
import com.yapyap.core.call.CallStateMachine
import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator
import com.yapyap.feature.call.domain.reader.CallSessionReader
import org.koin.dsl.module

val callModule = module {
    single<ActiveCallSessionTracker> { SettingsActiveCallSessionTracker(get()) }
    single { CallSessionStore(activeCallTracker = get()) }
    single<CallSessionReader> { get<CallSessionStore>() }
    single { CallInviteDeduplicator() }
    single { CallMediaCredentialsStore() }
    single { CallStateMachine(get()) }
    single { CallMediaSessionBinder(get(), get(), get(), get(), get()) }
    single {
        CallRingtoneCoordinator(
            callSessionReader = get(),
            ringtoneController = get(),
            dispatcherProvider = get(),
        )
    }
    single {
        CallMediaLifecycle(
            callMediaRepository = get(),
            callSessionStore = get(),
            stateMachine = get(),
            sessionBinder = get(),
            dispatcherProvider = get(),
        )
    }
    single {
        CallEntryHandler(
            callSessionStore = get(),
            callRepository = get(),
            inviteDeduplicator = get(),
            stateMachine = get(),
            observeAuthStateUseCase = get(),
        )
    }
    single {
        CallSessionReconciler(
            callRepository = get(),
            stateMachine = get(),
            mediaLifecycle = get(),
            entryHandler = get(),
            activeCallTracker = get(),
        )
    }
    single {
        CallReconnectHandler(
            chatRealtimeClient = get(),
            stateMachine = get(),
            reconciler = get(),
            activeCallTracker = get(),
            dispatcherProvider = get(),
        )
    }
    single {
        CallSignalingHandler(
            inviteDeduplicator = get(),
            stateMachine = get(),
            entryHandler = get(),
            mediaLifecycle = get(),
            credentialsStore = get(),
            dispatcherProvider = get(),
        )
    }
    single<CallSignalingClient> {
        CallSignalingClientImpl(chatRealtimeClient = get())
    }
    single {
        CallNativeCoordinator(
            callCoordinator = lazy { get<CallCoordinator>() },
            callNativeController = get(),
            callSessionReader = get(),
            entryHandler = get(),
            dispatcherProvider = get(),
        )
    }
    single<CallCoordinator> {
        CallCoordinator(
            callRepository = get(),
            callSignalingRepository = lazy { get<CallSignalingRepository>() },
            observeAuthStateUseCase = get(),
            dispatcherProvider = get(),
            stateMachine = get(),
            entryHandler = get(),
            signalingHandler = get(),
            mediaLifecycle = get(),
            reconnectHandler = get(),
            activeCallTracker = get(),
            ringtoneCoordinator = get(),
            callNativeCoordinator = get(),
        )
    }
    single<CallOrchestrator> { get<CallCoordinator>() }
    single<CallSignalingRepository> {
        CallSignalingRepositoryImpl(
            callCoordinator = lazy { get<CallCoordinator>() },
        )
    }
}
