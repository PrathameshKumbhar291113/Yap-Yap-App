package com.yapyap.core.realtime

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.ConversationNotificationStore
import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.push.PushNavigationRouter
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.push.model.PushPermissionStatus
import com.yapyap.core.push.PushPlatform
import com.yapyap.core.push.PushPlatformHandlers
import com.yapyap.core.push.model.PushDeliveryContext
import com.yapyap.core.push.model.PushNavigationTarget
import com.yapyap.core.realtime.reconcile.ReceiverRepairGateway
import com.yapyap.core.realtime.reconcile.ReceiverRepairService
import com.yapyap.core.realtime.reconcile.RepairSummary
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.ReconcileResult
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import com.yapyap.feature.devices.data.remote.DeviceApi
import com.yapyap.feature.devices.data.repository.DeviceRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GuaranteedReceiverSyncIntegrationTest {

    @Test
    fun fcmReceived_triggersRepairConversationAfterSync() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repairGateway = RecordingReceiverRepairGateway()
        val coordinator = createPushCoordinator(repairGateway, testDispatcher)

        coordinator.handlePushData(
            data = mapOf(
                "type" to "new_message",
                "conversationId" to "1",
                "messageId" to "42",
                "senderId" to "2",
                "preview" to "hello",
            ),
            delivery = PushDeliveryContext(),
            fromUserTap = false,
        )
        advanceUntilIdle()

        assertEquals(1, repairGateway.repairConversationCalls.size)
        assertEquals(1, repairGateway.repairConversationCalls.first().conversationId)
        assertEquals(ReceiverRepairService.TRIGGER_FCM, repairGateway.repairConversationCalls.first().trigger)
    }

    @Test
    fun appForeground_triggersRepairAll() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repairGateway = RecordingReceiverRepairGateway()
        val coordinator = createPushCoordinator(repairGateway, testDispatcher)

        coordinator.start(this)
        coordinator.onAppForeground()
        advanceUntilIdle()

        assertEquals(1, repairGateway.repairAllCalls.size)
        assertEquals(ReceiverRepairService.TRIGGER_FOREGROUND, repairGateway.repairAllCalls.first())
    }

    @Test
    fun notificationTap_repairsBeforeNavigation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repairGateway = RecordingReceiverRepairGateway()
        val navigationRouter = PushNavigationRouter()
        val coordinator = createPushCoordinator(
            repairGateway = repairGateway,
            dispatcher = testDispatcher,
            navigationRouter = navigationRouter,
        )

        coordinator.handlePushData(
            data = mapOf(
                "type" to "new_message",
                "conversationId" to "9",
                "messageId" to "100",
                "senderId" to "2",
                "preview" to "tap me",
            ),
            fromUserTap = true,
        )
        advanceUntilIdle()

        assertEquals(1, repairGateway.repairConversationCalls.size)
        assertEquals(9, repairGateway.repairConversationCalls.first().conversationId)
        assertEquals(ReceiverRepairService.TRIGGER_NOTIFICATION_TAP, repairGateway.repairConversationCalls.first().trigger)
        assertEquals(PushNavigationTarget.Chat("9"), navigationRouter.pendingTarget.value)
    }

    private fun createPushCoordinator(
        repairGateway: RecordingReceiverRepairGateway,
        dispatcher: CoroutineDispatcher,
        navigationRouter: PushNavigationRouter = PushNavigationRouter(),
    ): PushNotificationCoordinator {
        val authState = MutableStateFlow(
            AuthState.Authenticated(
                User(id = 1, uid = "alice", email = "alice@test.com", name = "Alice"),
            ),
        )
        return PushNotificationCoordinator(
            pushPlatform = NoopPushPlatform(),
            deviceRepository = DeviceRepository(DeviceApi(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }))),
            tokenStorage = NoopTokenStorage(),
            observeAuthStateUseCase = ObserveAuthStateUseCase(NoopAuthRepository(authState)),
            activeConversationTracker = ActiveConversationTracker(),
            deduplicator = PushMessageDeduplicator(),
            navigationRouter = navigationRouter,
            conversationRepository = NoopConversationRepository(),
            chatRepository = NoopChatRepository(),
            incomingMessageNotifier = IncomingMessageNotifier(),
            deliveryAckService = NoopMessageDeliveryAcknowledgementGateway,
            chatRealtimeClient = NoopChatRealtimeClient(),
            receiverRepairGateway = lazy { repairGateway },
            callCoordinator = lazy { error("CallCoordinator not used in push sync tests") },
            callNativeCoordinator = lazy { error("CallNativeCoordinator not used in push sync tests") },
            notificationStore = ConversationNotificationStore(),
            dispatcherProvider = TestDispatcherProvider(dispatcher),
        )
    }

    private class RecordingReceiverRepairGateway : ReceiverRepairGateway {
        val repairAllCalls = mutableListOf<String>()
        val repairConversationCalls = mutableListOf<RepairConversationCall>()

        data class RepairConversationCall(
            val conversationId: Int,
            val trigger: String,
            val hintServerMessageId: Int?,
        )

        override fun start(parentScope: CoroutineScope) = Unit

        override suspend fun repairAll(trigger: String): Result<RepairSummary> {
            repairAllCalls += trigger
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
        ): Result<ReconcileResult> {
            repairConversationCalls += RepairConversationCall(conversationId, trigger, hintServerMessageId)
            return Result.success(
                ReconcileResult(
                    conversationId = conversationId,
                    strategy = ReconcileStrategy.INCREMENTAL_AFTER,
                    localLatestMessageId = null,
                    serverLatestMessageId = hintServerMessageId,
                    messagesDownloaded = 1,
                    messagesMerged = 1,
                    duplicatesIgnored = 0,
                    pendingMessagesPreserved = 0,
                ),
            )
        }
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class NoopPushPlatform : PushPlatform {
        override suspend fun initialize(): PushPermissionStatus = PushPermissionStatus.Granted
        override suspend fun getDeviceToken(): String? = null
        override suspend fun deleteToken() = Unit
        override fun setHandlers(handlers: PushPlatformHandlers) = Unit
        override suspend fun onAppForeground() = Unit
        override fun showLocalNotification(notification: com.yapyap.core.push.model.LocalPushNotification) = Unit
        override fun dismissConversationNotification(conversationId: String) = Unit
        override fun usesInAppForegroundNotification(): Boolean = false
    }

    private class NoopTokenStorage : TokenStorage {
        override fun saveTokens(accessToken: String, refreshToken: String) = Unit
        override fun getAccessToken(): String? = "token"
        override fun getRefreshToken(): String? = "refresh"
        override fun saveCachedUser(json: String?) = Unit
        override fun getCachedUser(): String? = null
        override fun saveDeviceToken(token: String?) = Unit
        override fun getDeviceToken(): String? = null
        override fun setPushPermissionDenied(denied: Boolean) = Unit
        override fun isPushPermissionDenied(): Boolean = false
        override fun clear() = Unit
    }

    private class NoopAuthRepository(
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

    private class NoopChatRealtimeClient : ChatRealtimeClient {
        private val socketEvents = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 1)
        override val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
        override val events: SharedFlow<SocketEvent> = socketEvents.asSharedFlow()
        override val isReady: Boolean = false
        override fun connectionDiagnostics() = SocketConnectionDiagnostics(false, false, null, null)
        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: SocketSendMessagePayload) =
            Result.failure<com.yapyap.feature.chat.data.dto.MessageDto>(UnsupportedOperationException())
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }
}
