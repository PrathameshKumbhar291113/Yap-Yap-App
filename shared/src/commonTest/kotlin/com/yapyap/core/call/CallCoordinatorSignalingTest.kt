package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketConnectionDiagnostics
import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import com.yapyap.feature.call.domain.repository.CallRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CallCoordinatorSignalingTest {

    @Test
    fun incomingInviteTransitionsToIncomingState() = runTest {
        val fixture = createFixture(this)
        fixture.coordinator.onSignalingEvent(
            CallSignalingEvent.Invite(
                callSessionId = "call-42",
                conversationId = 9,
                callerId = 2,
                callerName = "Bob",
                callTypeRaw = "voice",
            ),
        )
        advanceUntilIdle()

        val state = fixture.store.state.value
        assertIs<CallState.Incoming>(state)
        assertEquals("call-42", state.session.id)
        assertEquals("Bob", state.remoteUser.displayName)
    }

    @Test
    fun incomingInviteFromSelfIsIgnoredWhileOutgoing() = runTest {
        val fixture = createFixture(this)
        val session = CallSession(
            id = "call-42",
            conversationId = 1,
            callType = CallType.Voice,
            caller = CallParticipant(userId = 1, displayName = "Alice"),
            callee = CallParticipant(userId = 2, displayName = "Bob"),
        )
        fixture.store.setState(
            CallState.Outgoing(
                session = session,
                remoteUser = session.callee,
            ),
        )

        fixture.coordinator.onSignalingEvent(
            CallSignalingEvent.Invite(
                callSessionId = "call-42",
                conversationId = 1,
                callerId = 1,
                callerName = "Alice",
                callTypeRaw = "voice",
            ),
        )
        advanceUntilIdle()

        assertIs<CallState.Outgoing>(fixture.store.state.value)
    }

    @Test
    fun acceptedMovesOutgoingToConnected() = runTest {
        val fixture = createFixture(this)
        val session = CallSession(
            id = "call-99",
            conversationId = 1,
            callType = CallType.Voice,
            caller = CallParticipant(userId = 1, displayName = "Alice"),
            callee = CallParticipant(userId = 2, displayName = "Bob"),
        )
        fixture.store.setState(
            CallState.Outgoing(
                session = session,
                remoteUser = session.callee,
            ),
        )

        fixture.coordinator.onSignalingEvent(
            CallSignalingEvent.Answer(callSessionId = "call-99"),
        )
        advanceUntilIdle()

        assertIs<CallState.Connected>(fixture.store.state.value)
    }

    @Test
    fun startOutgoingBlockedWhileCallActive() = runTest {
        val fixture = createFixture(this)
        val session = CallSession(
            id = "call-busy",
            conversationId = 1,
            callType = CallType.Voice,
            caller = CallParticipant(userId = 1, displayName = "Alice"),
            callee = CallParticipant(userId = 2, displayName = "Bob"),
        )
        fixture.store.setState(
            CallState.Connected(
                session = session,
                connectedAt = kotlin.time.Clock.System.now(),
            ),
        )

        val result = fixture.coordinator.startOutgoing(
            conversationId = 1,
            callType = CallType.Voice,
            remoteUser = session.callee,
        )

        assertEquals(true, result.isFailure)
        assertIs<CallState.Connected>(fixture.store.state.value)
    }

    @Test
    fun rejectedMovesOutgoingToEnded() = runTest {
        val fixture = createFixture(this)
        val session = CallSession(
            id = "call-77",
            conversationId = 1,
            callType = CallType.Voice,
            caller = CallParticipant(userId = 1, displayName = "Alice"),
            callee = CallParticipant(userId = 2, displayName = "Bob"),
        )
        fixture.store.setState(
            CallState.Outgoing(
                session = session,
                remoteUser = session.callee,
            ),
        )

        fixture.coordinator.onSignalingEvent(
            CallSignalingEvent.Reject(callSessionId = "call-77"),
        )
        runCurrent()

        val state = fixture.store.state.value
        assertIs<CallState.Ended>(state)
        assertEquals(CallEndReason.Rejected, state.reason)
    }

    private fun createFixture(scope: kotlinx.coroutines.test.TestScope): TestFixture {
        val testDispatcher = StandardTestDispatcher(scope.testScheduler)
        val store = CallSessionStore()
        val authState = MutableStateFlow<AuthState>(
            AuthState.Authenticated(
                User(id = 1, uid = "alice", email = "alice@test.com", name = "Alice"),
            ),
        )
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val stateMachine = CallStateMachine(store)
        val inviteDeduplicator = CallInviteDeduplicator()
        val entryHandler = CallEntryHandler(
            callSessionStore = store,
            callRepository = NoopCallRepository,
            inviteDeduplicator = inviteDeduplicator,
            stateMachine = stateMachine,
            observeAuthStateUseCase = ObserveAuthStateUseCase(FakeAuthRepository(authState)),
        )
        val mediaLifecycle = CallMediaLifecycle(
            callMediaRepository = NoopCallMediaRepository,
            callSessionStore = store,
            stateMachine = stateMachine,
            sessionBinder = CallMediaSessionBinder(
                store,
                NoopCallMediaRepository,
                CallMediaCredentialsStore(),
                entryHandler,
                dispatcherProvider,
            ),
            dispatcherProvider = dispatcherProvider,
        )
        val signalingHandler = CallSignalingHandler(
            inviteDeduplicator = inviteDeduplicator,
            stateMachine = stateMachine,
            entryHandler = entryHandler,
            mediaLifecycle = mediaLifecycle,
            credentialsStore = CallMediaCredentialsStore(),
            dispatcherProvider = dispatcherProvider,
        )
        val activeCallTracker = InMemoryActiveCallSessionTracker()
        val reconciler = CallSessionReconciler(
            callRepository = NoopCallRepository,
            stateMachine = stateMachine,
            mediaLifecycle = mediaLifecycle,
            entryHandler = entryHandler,
            activeCallTracker = activeCallTracker,
        )
        val reconnectHandler = CallReconnectHandler(
            chatRealtimeClient = NoopChatRealtimeClient,
            stateMachine = stateMachine,
            reconciler = reconciler,
            activeCallTracker = activeCallTracker,
            dispatcherProvider = dispatcherProvider,
        )
        val ringtoneCoordinator = CallRingtoneCoordinator(
            callSessionReader = store,
            ringtoneController = object : CallRingtoneController {
                override fun startIncomingRingtone() = Unit
                override fun startOutgoingRingback() = Unit
                override fun stopAll() = Unit
            },
            dispatcherProvider = dispatcherProvider,
        )
        lateinit var coordinator: CallCoordinator
        val callNativeCoordinator = CallNativeCoordinator(
            callCoordinator = lazy { coordinator },
            callNativeController = object : CallNativeController {
                override fun registerPhoneAccount() = Unit
                override fun showIncomingCall(request: CallNativeIncomingRequest) = Unit
                override fun showOutgoingCall(request: CallNativeIncomingRequest) = Unit
                override fun setCallActive(callSessionId: String, isVideoCall: Boolean) = Unit
                override fun dismissCall(callSessionId: String, reason: CallNativeDismissReason) = Unit
                override fun cancelIncomingNotification(callSessionId: String) = Unit
                override fun showMissedCallNotification(callSessionId: String, callerDisplayName: String) = Unit
            },
            callSessionReader = store,
            entryHandler = entryHandler,
            dispatcherProvider = dispatcherProvider,
        )
        coordinator = CallCoordinator(
            callRepository = NoopCallRepository,
            callSignalingRepository = lazy { NoopCallSignalingRepository },
            observeAuthStateUseCase = ObserveAuthStateUseCase(FakeAuthRepository(authState)),
            dispatcherProvider = dispatcherProvider,
            stateMachine = stateMachine,
            entryHandler = entryHandler,
            signalingHandler = signalingHandler,
            mediaLifecycle = mediaLifecycle,
            reconnectHandler = reconnectHandler,
            activeCallTracker = activeCallTracker,
            ringtoneCoordinator = ringtoneCoordinator,
            callNativeCoordinator = callNativeCoordinator,
        )
        coordinator.start(scope)
        return TestFixture(store = store, coordinator = coordinator)
    }

    private data class TestFixture(
        val store: CallSessionStore,
        val coordinator: CallCoordinator,
    )

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }

    private class FakeAuthRepository(
        override val authState: MutableStateFlow<AuthState>,
    ) : AuthRepository {
        override suspend fun login(identifier: String, password: String) = error("unused")
        override suspend fun register(
            email: String,
            password: String,
            name: String,
            phone: String,
        ) = error("unused")

        override suspend fun refreshToken() = error("unused")
        override suspend fun logout() = error("unused")
        override suspend fun updateProfile(update: ProfileUpdate) = error("unused")
    }

    private object NoopCallRepository : CallRepository {
        override suspend fun createOutgoingSession(
            conversationId: Int,
            callType: CallType,
            calleeUserId: Int,
        ) = error("unused")

        override suspend fun fetchSession(callSessionId: String): Result<CallSession> =
            Result.failure(IllegalStateException("unused"))
        override suspend fun reconcileSession(callSessionId: String): Result<ActiveCallSnapshot?> =
            Result.success(null)
        override suspend fun fetchActiveCall(): Result<ActiveCallSnapshot?> = Result.success(null)
        override suspend fun refreshCallToken(callSessionId: String): Result<ActiveCallSnapshot> =
            error("unused")
        override suspend fun redialFromHistory(callHistoryId: Int): Result<CallSession> = error("unused")
        override suspend fun answerSession(callSessionId: String) = Result.success(Unit)
        override suspend fun rejectSession(callSessionId: String) = Result.success(Unit)
        override suspend fun endSession(callSessionId: String) = Result.success(Unit)
    }

    private object NoopChatRealtimeClient : ChatRealtimeClient {
        override val connectionState =
            MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Ready(userId = 1))
        override val events: SharedFlow<com.yapyap.core.realtime.SocketEvent> = MutableSharedFlow()
        override val isReady = true
        override fun connectionDiagnostics(): SocketConnectionDiagnostics =
            SocketConnectionDiagnostics(
                connected = true,
                authenticated = true,
                socketId = "test",
                userId = 1,
            )
        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: com.yapyap.core.realtime.SocketSendMessagePayload) =
            error("unused")
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }

    private object NoopCallMediaRepository : com.yapyap.feature.call.domain.repository.CallMediaRepository {
        override val isMuted: StateFlow<Boolean> = MutableStateFlow(false)
        override val isCameraEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val audioRoute: StateFlow<com.yapyap.feature.call.domain.entity.CallAudioRoute> =
            MutableStateFlow(com.yapyap.feature.call.domain.entity.CallAudioRoute.Earpiece)
        override val connectionState: StateFlow<com.yapyap.feature.call.domain.entity.CallMediaConnectionState> =
            MutableStateFlow(com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Idle)
        override val localVideoTrack: StateFlow<com.yapyap.feature.call.domain.media.CallRenderableVideoTrack?> =
            MutableStateFlow(null)
        override val remoteVideoTrack: StateFlow<com.yapyap.feature.call.domain.media.CallRenderableVideoTrack?> =
            MutableStateFlow(null)
        override val remoteParticipantConnected: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun prepareForCall(
            callSessionId: String,
            videoEnabled: Boolean,
            credentials: com.yapyap.feature.call.domain.entity.CallRoomCredentials,
        ) = Result.success(Unit)

        override suspend fun reconnectForCall(
            callSessionId: String,
            videoEnabled: Boolean,
            credentials: com.yapyap.feature.call.domain.entity.CallRoomCredentials,
            restoreMuted: Boolean,
            restoreCameraEnabled: Boolean,
        ) = Result.success(Unit)

        override suspend fun releaseCall(callSessionId: String) = Result.success(Unit)
        override suspend fun setMuted(muted: Boolean) = Result.success(Unit)
        override suspend fun setCameraEnabled(enabled: Boolean) = Result.success(Unit)
        override suspend fun switchCamera() = Result.success(Unit)
        override suspend fun setAudioRoute(route: com.yapyap.feature.call.domain.entity.CallAudioRoute) =
            Result.success(Unit)
    }

    private object NoopCallSignalingRepository : CallSignalingRepository {
        override fun start(parentScope: kotlinx.coroutines.CoroutineScope) = Unit
        override fun handleEvent(event: CallSignalingEvent) = Unit
    }
}
