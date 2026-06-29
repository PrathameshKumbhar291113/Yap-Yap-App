package com.yapyap.core.realtime.reconcile

import com.yapyap.core.network.NetworkConnectivityMonitor
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.domain.entity.ReconcileResult
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.LastMessage
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiverRepairServiceTest {

    @Test
    fun repairAll_repairsEachChangedConversationIndependently() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val conversationRepository = FakeConversationRepository(
            initial = listOf(
                conversation(1, lastId = 10),
                conversation(2, lastId = 20),
                conversation(3, lastId = 30),
            ),
            refreshed = listOf(
                conversation(1, lastId = 11),
                conversation(2, lastId = 21),
                conversation(3, lastId = 30),
            ),
        )
        val chatRepository = RecordingChatRepository()
        val service = createService(
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            dispatcher = testDispatcher,
        )

        val summary = service.repairAll("test").getOrThrow()

        assertEquals(2, summary.changedConversationCount)
        assertEquals(2, chatRepository.reconcileCalls.size)
        assertEquals(setOf(1, 2), chatRepository.reconcileCalls.map { it.first }.toSet())
        assertEquals(11, chatRepository.reconcileCalls.first { it.first == 1 }.second)
        assertEquals(21, chatRepository.reconcileCalls.first { it.first == 2 }.second)
    }

    @Test
    fun repairAll_afterStaleMetadata_repairsWithoutOpeningChat() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val conversationRepository = FakeConversationRepository(
            initial = listOf(conversation(1, lastId = 10)),
            refreshed = listOf(conversation(1, lastId = 20)),
        )
        val chatRepository = RecordingChatRepository(localLatestByConversation = mapOf(1 to 10))
        val service = createService(
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            dispatcher = testDispatcher,
        )

        val summary = service.repairAll(ReceiverRepairService.TRIGGER_FOREGROUND).getOrThrow()

        assertEquals(1, summary.changedConversationCount)
        assertEquals(1, chatRepository.reconcileCalls.size)
        assertEquals(20, chatRepository.reconcileCalls.first().second)
    }

    @Test
    fun repairAll_includesCacheGapCheckWhenMetadataMatches() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val conversationRepository = FakeConversationRepository(
            initial = listOf(conversation(1, lastId = 102)),
            refreshed = listOf(conversation(1, lastId = 102)),
        )
        val chatRepository = RecordingChatRepository(
            localLatestByConversation = mapOf(1 to 102),
            needsGapRepairByConversation = mapOf(1 to true),
        )
        val service = createService(
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            dispatcher = testDispatcher,
        )

        val summary = service.repairAll(ReceiverRepairService.TRIGGER_FOREGROUND).getOrThrow()

        assertEquals(1, summary.changedConversationCount)
        assertEquals(1, chatRepository.reconcileCalls.size)
        assertEquals(1 to 102, chatRepository.reconcileCalls.first())
    }

    @Test
    fun repairConversation_afterFiveMissedMessages_downloadsIncrementally() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val conversationRepository = FakeConversationRepository(
            initial = listOf(conversation(1, lastId = 10)),
            refreshed = listOf(conversation(1, lastId = 15)),
        )
        val chatRepository = RecordingChatRepository(
            localLatestByConversation = mapOf(1 to 10),
            reconcileResults = mapOf(
                1 to ReconcileResult(
                    conversationId = 1,
                    strategy = ReconcileStrategy.INCREMENTAL_AFTER,
                    localLatestMessageId = 10,
                    serverLatestMessageId = 15,
                    messagesDownloaded = 5,
                    messagesMerged = 5,
                    duplicatesIgnored = 0,
                    pendingMessagesPreserved = 0,
                    acksSent = 5,
                ),
            ),
        )
        val service = createService(
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            dispatcher = testDispatcher,
        )

        val result = service.repairConversation(
            conversationId = 1,
            trigger = ReceiverRepairService.TRIGGER_SOCKET_READY,
        ).getOrThrow()

        assertEquals(5, result.messagesMerged)
        assertEquals(5, result.acksSent)
        assertEquals(ReconcileStrategy.INCREMENTAL_AFTER, result.strategy)
        assertEquals(1, chatRepository.reconcileCalls.size)
        assertEquals(15, chatRepository.reconcileCalls.first().second)
    }

    private fun createService(
        conversationRepository: ConversationRepository,
        chatRepository: ChatRepository,
        dispatcher: CoroutineDispatcher,
    ): ReceiverRepairService {
        return ReceiverRepairService(
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            observeAuthStateUseCase = ObserveAuthStateUseCase(
                FakeAuthRepository(
                    MutableStateFlow(
                        AuthState.Authenticated(
                            User(id = 1, uid = "alice", email = "alice@test.com", name = "Alice"),
                        ),
                    ),
                ),
            ),
            networkMonitor = FakeNetworkMonitor(),
            chatRealtimeClient = FakeChatRealtimeClient(
                MutableStateFlow(RealtimeConnectionState.Disconnected),
            ),
            dispatcherProvider = TestDispatcherProvider(dispatcher),
        )
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class FakeNetworkMonitor : NetworkConnectivityMonitor {
        override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
        override fun start() = Unit
        override fun stop() = Unit
    }

    private class FakeChatRealtimeClient(
        override val connectionState: StateFlow<RealtimeConnectionState>,
    ) : ChatRealtimeClient {
        private val socketEvents = MutableSharedFlow<com.yapyap.core.realtime.SocketEvent>(extraBufferCapacity = 1)
        override val events: kotlinx.coroutines.flow.SharedFlow<com.yapyap.core.realtime.SocketEvent> =
            socketEvents.asSharedFlow()
        override val isReady: Boolean = false
        override fun connectionDiagnostics() =
            com.yapyap.core.realtime.SocketConnectionDiagnostics(false, false, "test", 0)
        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: com.yapyap.core.realtime.SocketSendMessagePayload) =
            Result.failure<com.yapyap.feature.chat.data.dto.MessageDto>(UnsupportedOperationException())
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
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

    private class FakeConversationRepository(
        initial: List<Conversation>,
        private val refreshed: List<Conversation>,
    ) : ConversationRepository {
        private val state = MutableStateFlow(initial)

        override fun observeConversations(): Flow<List<Conversation>> = state

        override suspend fun refreshConversations(): Result<Unit> {
            state.value = refreshed
            return Result.success(Unit)
        }

        override suspend fun createDirectConversation(contact: String): Result<Conversation> =
            Result.failure(UnsupportedOperationException())

        override fun applyIncomingMessage(
            conversationId: Int,
            message: Message,
            currentUserId: Int?,
            isViewingConversation: Boolean,
        ): Boolean = false

        override fun applyConversationRead(conversationId: Int) = Unit

        override fun snapshotConversations(): List<Conversation> = state.value
    }

    private class RecordingChatRepository(
        private val localLatestByConversation: Map<Int, Int?> = emptyMap(),
        private val reconcileResults: Map<Int, ReconcileResult> = emptyMap(),
        private val needsGapRepairByConversation: Map<Int, Boolean> = emptyMap(),
    ) : ChatRepository {
        val reconcileCalls = mutableListOf<Pair<Int, Int?>>()

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

        override fun getLocalSyncSnapshot(conversationId: Int): ChatSyncSnapshot {
            return ChatSyncSnapshot(
                conversationId = conversationId,
                localLatestMessageId = localLatestByConversation[conversationId],
                cachedMessageCount = localLatestByConversation[conversationId]?.let { 1 } ?: 0,
                initialLoaded = localLatestByConversation[conversationId] != null,
                pendingMessageCount = 0,
            )
        }

        override fun needsInteriorGapRepair(conversationId: Int, serverLatestMessageId: Int?): Boolean =
            needsGapRepairByConversation[conversationId] == true

        override suspend fun reconcileConversation(
            conversationId: Int,
            serverLatestMessageId: Int?,
            trigger: String,
        ): Result<ReconcileResult> {
            reconcileCalls += conversationId to serverLatestMessageId
            return Result.success(
                reconcileResults[conversationId] ?: ReconcileResult(
                    conversationId = conversationId,
                    strategy = ReconcileStrategy.INCREMENTAL_AFTER,
                    localLatestMessageId = localLatestByConversation[conversationId],
                    serverLatestMessageId = serverLatestMessageId,
                    messagesDownloaded = 1,
                    messagesMerged = 1,
                    duplicatesIgnored = 0,
                    pendingMessagesPreserved = 0,
                ),
            )
        }
    }

    private fun conversation(id: Int, lastId: Int): Conversation {
        return Conversation(
            id = id,
            type = "direct",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            unreadCount = 0,
            lastMessage = LastMessage(
                id = lastId,
                content = "msg",
                messageType = "text",
                senderId = 2,
                createdAt = "2026-01-01T00:00:0$id",
            ),
        )
    }
}
