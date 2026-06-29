package com.yapyap.core.session.di

import com.yapyap.core.session.SessionManager
import org.koin.dsl.module

val sessionModule = module {
    single {
        SessionManager(
            authRepository = get(),
            tokenStorage = get(),
            sessionEvents = get(),
            chatRealtimeClient = get(),
            realtimeCoordinator = lazy { get() },
            dispatcherProvider = get(),
        )
    }
}
