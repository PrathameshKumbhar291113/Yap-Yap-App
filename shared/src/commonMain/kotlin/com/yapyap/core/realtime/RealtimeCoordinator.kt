package com.yapyap.core.realtime

import co.touchlab.kermit.Logger
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.network.SessionEventPublisher
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RealtimeCoordinator(
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val tokenStorage: TokenStorage,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val chatRealtimeRepository: ChatRealtimeRepository,
    private val conversationRepository: ConversationRepository,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
    private val sessionEvents: SessionEventPublisher,
    private val tokenRefreshNotifier: TokenRefreshNotifier,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val log = Logger.withTag("SocketIO")
    private var scope: CoroutineScope? = null

    fun start(parentScope: CoroutineScope) {
        if (scope != null) return

        scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        val coordinatorScope = scope ?: return

        chatRealtimeRepository.start(coordinatorScope)
        deliveryAckService.start(coordinatorScope)

        coordinatorScope.launch(dispatcherProvider.default) {
            observeAuthStateUseCase()
                .map { state -> state is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { authenticated ->
                    if (authenticated) {
                        connectWithLatestToken()
                    } else {
                        chatRealtimeClient.disconnect()
                    }
                }
        }

        coordinatorScope.launch(dispatcherProvider.default) {
            sessionEvents.sessionExpired.collect {
                chatRealtimeClient.disconnect()
            }
        }

        coordinatorScope.launch(dispatcherProvider.default) {
            tokenRefreshNotifier.accessTokenRefreshed.collect { accessToken ->
                chatRealtimeClient.refreshAccessToken(accessToken)
            }
        }

        coordinatorScope.launch(dispatcherProvider.default) {
            chatRealtimeClient.connectionState
                .map { state -> state is RealtimeConnectionState.Ready }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready && observeAuthStateUseCase().value is AuthState.Authenticated) {
                        conversationRepository.refreshConversations()
                    }
                }
        }
    }

    private fun connectWithLatestToken() {
        val token = tokenStorage.getAccessToken()
        if (token.isNullOrBlank()) {
            log.w { "Skipping socket connect — no access token" }
            return
        }
        log.d { "Connecting socket for authenticated session" }
        chatRealtimeClient.connect(token)
    }
}
