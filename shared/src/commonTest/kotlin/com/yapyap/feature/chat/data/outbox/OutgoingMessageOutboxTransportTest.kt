package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.NetworkConnectivityMonitor
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketConnectionDiagnostics
import com.yapyap.core.realtime.SocketEvent
import com.yapyap.core.realtime.SocketSendMessagePayload
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.dto.SendMessageRequestDto
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutgoingMessageOutboxTransportTest {

    @Test
    fun networkOnline_socketReadyWithin150ms_allMessagesUseSocket() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher, socketReadyWaitMs = 300L)
        harness.networkOnline.value = false
        enqueueMessages(harness.service, count = 10)
        harness.networkOnline.value = true

        val scope = TestScope(dispatcher)
        scope.launch {
            kotlinx.coroutines.delay(150)
            harness.socket.setReady(userId = 1)
        }

        harness.service.flushTriggers.clear()
        harness.service.triggerNetworkOnlineReconnect()
        advanceUntilIdle()
        harness.service.awaitReconnectFlush()

        assertEquals(10, harness.socket.sendOrder.size)
        assertEquals(0, harness.chatApi.sendOrder.size)
        assertTrue(
            harness.service.flushTriggers.any { it == "socket_ready_after_network" },
            "Expected socket_ready_after_network flush, got ${harness.service.flushTriggers}",
        )
    }

    @Test
    fun networkOnline_socketNeverReady_restFallbackAfterTimeout() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher, socketReadyWaitMs = 300L)
        harness.networkOnline.value = false
        enqueueMessages(harness.service, count = 5)
        harness.networkOnline.value = true

        harness.service.flushTriggers.clear()
        harness.service.triggerNetworkOnlineReconnect()
        advanceUntilIdle()
        harness.service.awaitReconnectFlush()

        assertEquals(0, harness.socket.sendOrder.size)
        assertEquals(5, harness.chatApi.sendOrder.size)
        assertTrue(
            harness.service.flushTriggers.any { it == "socket_timeout_rest" },
            "Expected socket_timeout_rest flush, got ${harness.service.flushTriggers}",
        )
    }

    @Test
    fun socketBecomesReadyDuringBurst_firstRestThenSocket() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher)
        harness.networkOnline.value = false
        enqueueMessages(harness.service, count = 10)
        harness.networkOnline.value = true
        harness.chatApi.readySocketAfterRestSends = 3

        harness.service.flushTriggers.clear()
        harness.service.flushPendingOutgoingMessages("test_burst")
        advanceUntilIdle()

        assertEquals(3, harness.chatApi.sendOrder.size)
        assertEquals(7, harness.socket.sendOrder.size)
        assertEquals(10, harness.socket.sendOrder.size + harness.chatApi.sendOrder.size)
    }

    @Test
    fun socketDisconnectsMidBurst_remainingMessagesUseRest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher)
        harness.networkOnline.value = true
        harness.socket.setReady(userId = 1)
        harness.socket.disconnectAfterSocketSends = 2
        enqueueMessages(harness.service, count = 6)

        harness.service.flushPendingOutgoingMessages("test_disconnect_mid_burst")
        advanceUntilIdle()

        assertEquals(2, harness.socket.sendOrder.size)
        assertEquals(4, harness.chatApi.sendOrder.size)
    }

    @Test
    fun orderingPreserved_enqueueOrderMatchesSendOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher)
        harness.networkOnline.value = true
        harness.socket.setReady(userId = 1)

        harness.service.enqueue(1, 1, "A")
        harness.service.enqueue(1, 1, "B")
        harness.service.enqueue(1, 1, "C")
        advanceUntilIdle()

        val sentContents = buildList {
            addAll(harness.socket.sendOrder.map { it.second })
            addAll(harness.chatApi.sendOrder.map { it.second })
        }
        assertEquals(listOf("A", "B", "C"), sentContents)
    }

    @Test
    fun networkOnlineThenSocketReady_producesSingleReconnectFlush() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = createHarness(dispatcher, socketReadyWaitMs = 300L)
        harness.networkOnline.value = false
        enqueueMessages(harness.service, count = 3)
        harness.networkOnline.value = true

        val scope = TestScope(dispatcher)
        scope.launch {
            kotlinx.coroutines.delay(50)
            harness.socket.setReady(userId = 1)
        }

        harness.service.flushTriggers.clear()
        harness.service.triggerNetworkOnlineReconnect()
        advanceUntilIdle()
        harness.service.awaitReconnectFlush()

        val reconnectFlushes = harness.service.flushTriggers.count {
            it == "socket_ready_after_network" || it == "socket_timeout_rest"
        }
        assertEquals(1, reconnectFlushes)
        assertEquals(0, harness.service.flushTriggers.count { it == "socket_ready" })
    }

    private suspend fun enqueueMessages(service: OutgoingMessageOutboxService, count: Int) {
        repeat(count) { index ->
            service.enqueue(conversationId = 1, senderId = 1, content = "msg-${index + 1}")
        }
    }

    private fun createHarness(
        dispatcher: CoroutineDispatcher,
        socketReadyWaitMs: Long = 300L,
    ): TestHarness {
        val store = InMemoryOutgoingMessageOutboxStore()
        val networkOnline = MutableStateFlow(true)
        val socket = ControllableChatRealtimeClient(MutableStateFlow(RealtimeConnectionState.Disconnected))
        val chatApi = RecordingRestGateway(socket)
        val cacheWriter = NoopOutgoingMessageCacheWriter()
        val service = OutgoingMessageOutboxService(
            store = store,
            cacheWriterProvider = { cacheWriter },
            chatApi = chatApi,
            chatRealtimeClient = socket,
            networkMonitor = FakeNetworkMonitor(networkOnline),
            observeAuthStateUseCase = ObserveAuthStateUseCase(FakeAuthRepository()),
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            config = OutgoingMessageOutboxService.OutboxConfig(
                socketReadyWaitMs = socketReadyWaitMs,
                socketReadyPollMs = 10L,
            ),
        )
        service.bindParentScope(TestScope(dispatcher))
        return TestHarness(service, socket, chatApi, networkOnline)
    }

    private data class TestHarness(
        val service: OutgoingMessageOutboxService,
        val socket: ControllableChatRealtimeClient,
        val chatApi: RecordingRestGateway,
        val networkOnline: MutableStateFlow<Boolean>,
    )

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class FakeNetworkMonitor(
        override val isOnline: MutableStateFlow<Boolean>,
    ) : NetworkConnectivityMonitor {
        override fun start() = Unit
        override fun stop() = Unit
    }

    private class FakeAuthRepository : AuthRepository {
        override val authState = MutableStateFlow(
            AuthState.Authenticated(
                User(id = 1, uid = "bob", email = "bob@test.com", name = "Bob"),
            ),
        )

        override suspend fun login(identifier: String, password: String): Result<User> =
            Result.failure(UnsupportedOperationException())
        override suspend fun register(email: String, password: String, name: String, phone: String): Result<User> =
            Result.failure(UnsupportedOperationException())
        override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override suspend fun updateProfile(update: com.yapyap.feature.auth.domain.model.ProfileUpdate): Result<User> =
            Result.failure(UnsupportedOperationException())
    }

    private class NoopOutgoingMessageCacheWriter : OutgoingMessageCacheWriter {
        override suspend fun upsertOutboxMessage(message: Message) = Unit
        override suspend fun markOutboxSending(clientMessageId: String) = Unit
        override suspend fun markOutboxSent(clientMessageId: String, serverMessage: Message) = Unit
        override suspend fun markOutboxFailed(clientMessageId: String) = Unit
        override suspend fun removeOutboxMessage(clientMessageId: String) = Unit
        override suspend fun pendingOutboxMessages(conversationId: Int): List<Message> = emptyList()
    }

    private class ControllableChatRealtimeClient(
        private val connectionStateFlow: MutableStateFlow<RealtimeConnectionState>,
    ) : ChatRealtimeClient {
        private val socketEvents = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 8)
        override val connectionState: StateFlow<RealtimeConnectionState> = connectionStateFlow
        override val events: SharedFlow<SocketEvent> = socketEvents.asSharedFlow()
        override val isReady: Boolean
            get() = connectionStateFlow.value is RealtimeConnectionState.Ready

        var disconnectAfterSocketSends: Int? = null
        val sendOrder = mutableListOf<Pair<String, String>>()
        private var socketSendCount = 0

        fun setReady(userId: Int) {
            connectionStateFlow.value = RealtimeConnectionState.Ready(userId)
        }

        fun setDisconnected() {
            connectionStateFlow.value = RealtimeConnectionState.Disconnected
        }

        override fun connectionDiagnostics(): SocketConnectionDiagnostics =
            SocketConnectionDiagnostics(
                connected = isReady,
                authenticated = isReady,
                socketId = "test-socket",
                userId = (connectionStateFlow.value as? RealtimeConnectionState.Ready)?.userId,
            )

        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit

        override suspend fun sendMessage(payload: SocketSendMessagePayload): Result<MessageDto> {
            if (!isReady) {
                return Result.failure(IllegalStateException("Socket not connected"))
            }
            socketSendCount += 1
            disconnectAfterSocketSends?.let { threshold ->
                if (socketSendCount >= threshold) {
                    setDisconnected()
                }
            }
            sendOrder += payload.clientMessageId.orEmpty() to payload.content.orEmpty()
            return Result.success(
                MessageDto(
                    id = sendOrder.size,
                    conversationId = payload.conversationId,
                    senderId = 1,
                    content = payload.content,
                    messageType = payload.messageType,
                    clientMessageId = payload.clientMessageId,
                    createdAt = "2026-01-01T00:00:00Z",
                ),
            )
        }

        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }

    private class RecordingRestGateway(
        private val socket: ControllableChatRealtimeClient,
    ) : OutgoingMessageRestGateway {
        var readySocketAfterRestSends: Int? = null
        val sendOrder = mutableListOf<Pair<String, String>>()
        private var restSendCount = 0

        override suspend fun sendMessage(
            conversationId: Int,
            request: SendMessageRequestDto,
        ): ApiResult<MessageDto> {
            restSendCount += 1
            readySocketAfterRestSends?.let { threshold ->
                if (restSendCount >= threshold) {
                    socket.setReady(userId = 1)
                }
            }
            val clientMessageId = request.clientMessageId.orEmpty()
            sendOrder += clientMessageId to request.content.orEmpty()
            return ApiResult.Success(
                MessageDto(
                    id = 1_000 + sendOrder.size,
                    conversationId = conversationId,
                    senderId = 1,
                    content = request.content,
                    messageType = request.messageType,
                    clientMessageId = clientMessageId,
                    createdAt = "2026-01-01T00:00:00Z",
                ),
            )
        }
    }
}
