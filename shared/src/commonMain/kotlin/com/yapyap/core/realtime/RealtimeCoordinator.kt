package com.yapyap.core.realtime

import com.yapyap.core.diagnostics.ReceiverSyncTrace
import co.touchlab.kermit.Logger
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxGateway
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.network.SessionEventPublisher
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.core.realtime.reconcile.ReceiverRepairGateway
import com.yapyap.core.realtime.reconcile.ReceiverRepairService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RealtimeCoordinator(
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val tokenStorage: TokenStorage,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val chatRealtimeRepository: Lazy<ChatRealtimeRepository>,
    private val receiverRepairGateway: Lazy<ReceiverRepairGateway>,
    private val deliveryAckService: Lazy<MessageDeliveryAcknowledgementGateway>,
    private val outboxGateway: Lazy<OutgoingMessageOutboxGateway>,
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

        chatRealtimeRepository.value.start(coordinatorScope)
        deliveryAckService.value.start(coordinatorScope)
        outboxGateway.value.start(coordinatorScope)
        receiverRepairGateway.value.start(coordinatorScope)

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
                    val socketState = chatRealtimeClient.connectionState.value.toString()
                    ReceiverSyncTrace.stage(
                        stage = "OBSERVE_SOCKET_READY",
                        trigger = "RealtimeCoordinator.connectionState",
                        extra = mapOf(
                            "ready" to ready.toString(),
                            "socketState" to socketState,
                        ),
                    )
                    if (ready && observeAuthStateUseCase().value is AuthState.Authenticated) {
                        receiverRepairGateway.value.repairAll(ReceiverRepairService.TRIGGER_SOCKET_READY)
                            .onFailure { error ->
                                log.w(error) { "Receiver repair after socket ready failed" }
                            }
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
