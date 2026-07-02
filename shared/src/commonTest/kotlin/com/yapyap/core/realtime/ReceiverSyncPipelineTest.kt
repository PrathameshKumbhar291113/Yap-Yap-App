package com.yapyap.core.realtime

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.network.SessionEventPublisher
import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.realtime.reconcile.ReceiverRepairGateway
import com.yapyap.core.realtime.reconcile.RepairSummary
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxGateway
import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.ReconcileResult
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiverSyncPipelineTest {

    @Test
    fun socketReconnect_triggersReconciliationService() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
        val reconciliationService = RecordingReconciliationGateway()
        val chatRealtimeClient = FakeChatRealtimeClient(connectionState)
        val observeAuth = ObserveAuthStateUseCase(
            FakeAuthRepository(
                MutableStateFlow(
                    AuthState.Authenticated(
                        User(id = 1, uid = "alice", email = "alice@test.com", name = "Alice"),
                    ),
                ),
            ),
        )

        val coordinator = RealtimeCoordinator(
            observeAuthStateUseCase = observeAuth,
            tokenStorage = FakeTokenStorage("token"),
            chatRealtimeClient = chatRealtimeClient,
            chatRealtimeRepository = lazy { noopChatRealtimeRepository(chatRealtimeClient, observeAuth) },
            receiverRepairGateway = lazy { reconciliationService },
            deliveryAckService = lazy { NoopMessageDeliveryAcknowledgementGateway },
            outboxGateway = lazy { NoopOutgoingMessageOutboxGateway() },
            sessionEvents = NoopSessionEventPublisher(),
            tokenRefreshNotifier = TokenRefreshNotifier(),
            dispatcherProvider = dispatcherProvider,
        )

        val scope = TestScope(testDispatcher)
        coordinator.start(scope)
        advanceUntilIdle()

        assertEquals(0, reconciliationService.repairCount)

        connectionState.value = RealtimeConnectionState.Ready(userId = 1)
        advanceUntilIdle()

        assertEquals(1, reconciliationService.repairCount)
        assertEquals("socket_ready", reconciliationService.lastTrigger)
    }

    private fun noopChatRealtimeRepository(
        client: FakeChatRealtimeClient,
        observeAuth: ObserveAuthStateUseCase,
    ): ChatRealtimeRepository {
        return ChatRealtimeRepository(
            chatRealtimeClient = client,
            chatRepository = NoopChatRepository(),
            conversationRepository = NoopConversationRepository(),
            typingStore = TypingStore(),
            presenceStore = PresenceStore(),
            observeAuthStateUseCase = observeAuth,
            activeConversationTracker = ActiveConversationTracker(),
            deduplicator = PushMessageDeduplicator(),
            pushNotificationCoordinator = lazy { error("not used") },
            deliveryAckService = NoopMessageDeliveryAcknowledgementGateway,
            callSignalingRepository = lazy {
                object : com.yapyap.core.call.CallSignalingRepository {
                    override fun start(parentScope: kotlinx.coroutines.CoroutineScope) = Unit
                    override fun handleEvent(event: com.yapyap.core.call.CallSignalingEvent) = Unit
                }
            },
        )
    }

    private class RecordingReconciliationGateway : ReceiverRepairGateway {
        var repairCount = 0
        var lastTrigger: String? = null

        override fun start(parentScope: kotlinx.coroutines.CoroutineScope) = Unit

        override suspend fun repairAll(trigger: String): Result<RepairSummary> {
            repairCount += 1
            lastTrigger = trigger
            return Result.success(
                RepairSummary(
                    trigger = trigger,
                    changedConversationCount = 0,
                    repairedConversationCount = 0,
                    totalMessagesMerged = 0,
                    results = emptyList(),
                ),
            )
        }

        override suspend fun repairConversation(
            conversationId: Int,
            trigger: String,
            hintServerMessageId: Int?,
        ): Result<ReconcileResult> = Result.success(
            ReconcileResult(
                conversationId = conversationId,
                strategy = ReconcileStrategy.SKIP_ALREADY_CURRENT,
                localLatestMessageId = null,
                serverLatestMessageId = hintServerMessageId,
                messagesDownloaded = 0,
                messagesMerged = 0,
                duplicatesIgnored = 0,
                pendingMessagesPreserved = 0,
            ),
        )
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class FakeTokenStorage(private val token: String) : TokenStorage {
        override fun saveTokens(accessToken: String, refreshToken: String) = Unit
        override fun getAccessToken(): String? = token
        override fun getRefreshToken(): String? = "refresh"
        override fun saveCachedUser(json: String?) = Unit
        override fun getCachedUser(): String? = null
        override fun saveDeviceToken(token: String?) = Unit
        override fun getDeviceToken(): String? = null
        override fun setPushPermissionDenied(denied: Boolean) = Unit
        override fun isPushPermissionDenied(): Boolean = false
        override fun clear() = Unit
    }

    private class FakeChatRealtimeClient(
        override val connectionState: StateFlow<RealtimeConnectionState>,
        private val socketEvents: MutableSharedFlow<SocketEvent> = MutableSharedFlow(extraBufferCapacity = 8),
    ) : ChatRealtimeClient {
        override val events: SharedFlow<SocketEvent> = socketEvents.asSharedFlow()
        override val isReady: Boolean
            get() = connectionState.value is RealtimeConnectionState.Ready

        override fun connectionDiagnostics(): SocketConnectionDiagnostics =
            SocketConnectionDiagnostics(true, isReady, "test", 1)

        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: SocketSendMessagePayload) =
            Result.failure<MessageDto>(UnsupportedOperationException())

        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }

    private class NoopSessionEventPublisher : SessionEventPublisher {
        private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()
        override fun emitSessionExpired() {
            _sessionExpired.tryEmit(Unit)
        }
    }

    private class NoopOutgoingMessageOutboxGateway : OutgoingMessageOutboxGateway {
        override fun start(parentScope: kotlinx.coroutines.CoroutineScope) = Unit
        override suspend fun enqueue(conversationId: Int, senderId: Int, content: String): Message =
            error("not used")
        override suspend fun retry(clientMessageId: String, trigger: String) = Unit
        override suspend fun delete(clientMessageId: String, trigger: String) = Unit
        override suspend fun flushPendingOutgoingMessages(trigger: String) = Unit
        override fun pendingMessagesFor(conversationId: Int): List<Message> = emptyList()
    }

    private class FakeAuthRepository(
        override val authState: StateFlow<AuthState>,
    ) : AuthRepository {
        override suspend fun login(identifier: String, password: String): Result<User> =
            Result.failure(UnsupportedOperationException())
        override suspend fun register(email: String, password: String, name: String, phone: String): Result<User> =
            Result.failure(UnsupportedOperationException())
        override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override suspend fun updateProfile(update: com.yapyap.feature.auth.domain.model.ProfileUpdate): Result<User> =
            Result.failure(UnsupportedOperationException())
    }

    private class NoopConversationRepository : ConversationRepository {
        override fun observeConversations(): Flow<List<Conversation>> = emptyFlow()
        override suspend fun refreshConversations(): Result<Unit> = Result.success(Unit)
        override suspend fun createDirectConversation(contact: String): Result<Conversation> =
            Result.failure(UnsupportedOperationException())
        override suspend fun applyIncomingMessage(
            conversationId: Int,
            message: Message,
            currentUserId: Int?,
            isViewingConversation: Boolean,
        ): Boolean = false
        override suspend fun applyConversationRead(conversationId: Int) = Unit
        override fun snapshotConversations(): List<Conversation> = emptyList()
    }

    private class NoopChatRepository : ChatRepository {
        override fun observeMessages(conversationId: Int): Flow<List<Message>> = emptyFlow()
        override suspend fun loadLatestMessages(conversationId: Int): Result<Unit> = Result.success(Unit)
        override suspend fun loadOlderMessages(conversationId: Int): Result<Unit> = Result.success(Unit)
        override suspend fun sendMessage(conversationId: Int, senderId: Int, content: String): Result<Message> =
            Result.failure(UnsupportedOperationException())
        override suspend fun retryOutgoingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteOutgoingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
        override suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit> = Result.success(Unit)
        override fun hasMoreMessages(conversationId: Int): Boolean = false
        override fun isLoadingOlder(conversationId: Int): Boolean = false
        override suspend fun upsertIncomingMessage(message: Message) = Unit
        override suspend fun applyMessageStatus(
            conversationId: Int,
            messageId: Int,
            status: MessageStatus,
            readerUserId: Int?,
        ) = Unit
        override fun getLocalSyncSnapshot(conversationId: Int): ChatSyncSnapshot =
            ChatSyncSnapshot(conversationId, null, 0, false, 0)
        override fun needsInteriorGapRepair(conversationId: Int, serverLatestMessageId: Int?): Boolean = false
        override suspend fun reconcileConversation(
            conversationId: Int,
            serverLatestMessageId: Int?,
            trigger: String,
        ): Result<ReconcileResult> = Result.success(
            ReconcileResult(
                conversationId = conversationId,
                strategy = ReconcileStrategy.SKIP_ALREADY_CURRENT,
                localLatestMessageId = null,
                serverLatestMessageId = serverLatestMessageId,
                messagesDownloaded = 0,
                messagesMerged = 0,
                duplicatesIgnored = 0,
                pendingMessagesPreserved = 0,
            ),
        )
    }
}
