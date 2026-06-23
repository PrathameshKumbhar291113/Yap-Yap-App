package com.yapyap.core.session

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.network.SessionEventPublisher
import com.yapyap.core.network.Environment
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeCoordinator
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BootstrapState {
    data object Checking : BootstrapState

    data object Unauthenticated : BootstrapState

    data object Authenticated : BootstrapState
}

class SessionManager(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val sessionEvents: SessionEventPublisher,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val realtimeCoordinator: RealtimeCoordinator,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(dispatcherProvider.default)

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    val authState: StateFlow<AuthState> = authRepository.authState

    val bootstrapState: StateFlow<BootstrapState> = authRepository.authState
        .map { state ->
            when (state) {
                AuthState.Loading -> BootstrapState.Checking
                AuthState.Unauthenticated -> BootstrapState.Unauthenticated
                is AuthState.Authenticated -> BootstrapState.Authenticated
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, BootstrapState.Checking)

    val hasStoredSession: Boolean
        get() = !tokenStorage.getAccessToken().isNullOrBlank()

    fun start(appScope: CoroutineScope) {
        realtimeCoordinator.start(appScope)

        scope.launch {
            sessionEvents.sessionExpired.collect {
                chatRealtimeClient.disconnect()
                _sessionExpired.emit(Unit)
            }
        }
    }

    fun disconnectRealtime() {
        chatRealtimeClient.disconnect()
    }

    fun onAppForeground() {
        val token = tokenStorage.getAccessToken()
        if (
            !token.isNullOrBlank() &&
            authRepository.authState.value is AuthState.Authenticated
        ) {
            chatRealtimeClient.connect(token)
        }
    }

    fun onAppBackground() {
        // Debug/local: keep socket alive so background messages trigger local notifications
        // without requiring FCM (emulator + simulator). Release disconnects for real push.
        if (!Environment.isDebug) {
            disconnectRealtime()
        }
    }
}
