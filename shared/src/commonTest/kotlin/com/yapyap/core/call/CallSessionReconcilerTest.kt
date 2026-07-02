package com.yapyap.core.call

import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallServerStatus
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.entity.ReconnectReason
import com.yapyap.feature.call.domain.repository.CallRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CallSessionReconcilerTest {

    @Test
    fun connectedToReconnectingOnSocketLoss() = runTest {
        val fixture = createFixture()
        fixture.store.setState(
            CallState.Connected(
                session = session(),
                connectedAt = kotlin.time.Clock.System.now(),
            ),
        )

        fixture.reconciler.enterReconnectingFromSocketLoss()

        val state = fixture.store.state.value
        assertIs<CallState.Reconnecting>(state)
        assertEquals(ReconnectReason.SocketDisconnect, state.reason)
    }

    @Test
    fun reconcileRestoresConnectedFromActiveSession() = runTest {
        val fixture = createFixture()
        val activeSession = session()
        fixture.store.setState(
            CallState.Reconnecting(
                session = activeSession,
                attempt = 1,
                reason = ReconnectReason.SocketDisconnect,
            ),
        )
        fixture.repository.activeSnapshot = ActiveCallSnapshot(
            session = activeSession,
            status = CallServerStatus.Connected,
        )

        fixture.reconciler.reconcileAfterSocketReady()

        assertIs<CallState.Connected>(fixture.store.state.value)
    }

    @Test
    fun reconcileEndsWhenNoActiveSession() = runTest {
        val fixture = createFixture()
        fixture.store.setState(
            CallState.Reconnecting(
                session = session(),
                attempt = 1,
                reason = ReconnectReason.SocketDisconnect,
            ),
        )
        fixture.repository.activeSnapshot = null

        fixture.reconciler.reconcileAfterSocketReady()

        assertIs<CallState.Ended>(fixture.store.state.value)
    }

    @Test
    fun reconcileMovesConnectingToConnectedWhenServerSaysConnected() = runTest {
        val fixture = createFixture()
        val activeSession = session()
        fixture.store.setState(
            CallState.Connecting(
                session = activeSession,
                direction = CallDirection.Incoming,
            ),
        )
        fixture.repository.activeSnapshot = ActiveCallSnapshot(
            session = activeSession,
            status = CallServerStatus.Connected,
        )

        fixture.reconciler.reconcileAfterSocketReady()

        assertIs<CallState.Connected>(fixture.store.state.value)
    }

    @Test
    fun staleSessionMismatchEndsCall() = runTest {
        val fixture = createFixture()
        fixture.store.setState(
            CallState.Reconnecting(
                session = session(id = "local-call"),
                attempt = 1,
                reason = ReconnectReason.SocketDisconnect,
            ),
        )
        fixture.repository.activeSnapshot = ActiveCallSnapshot(
            session = session(id = "server-call"),
            status = CallServerStatus.Connected,
        )

        fixture.reconciler.reconcileAfterSocketReady()

        val state = fixture.store.state.value
        assertIs<CallState.Ended>(state)
        assertEquals(CallEndReason.RemoteHangup, state.reason)
    }

    @Test
    fun restorePersistedSessionRestoresConnectedCall() = runTest {
        val fixture = createFixture()
        val activeSession = session()
        fixture.repository.activeSnapshot = ActiveCallSnapshot(
            session = activeSession,
            status = CallServerStatus.Connected,
        )

        fixture.reconciler.restorePersistedSession(activeSession.id)

        assertIs<CallState.Connected>(fixture.store.state.value)
    }

    private fun createFixture(): TestFixture {
        val store = CallSessionStore()
        val stateMachine = CallStateMachine(store)
        val repository = FakeCallRepository()
        val dispatcherProvider = TestDispatcherProvider()
        val authState = kotlinx.coroutines.flow.MutableStateFlow<AuthState>(
            AuthState.Authenticated(
                com.yapyap.feature.auth.domain.entity.User(
                    id = 1,
                    uid = "alice",
                    email = "alice@test.com",
                    name = "Alice",
                ),
            ),
        )
        val entryHandler = CallEntryHandler(
            callSessionStore = store,
            callRepository = repository,
            inviteDeduplicator = CallInviteDeduplicator(),
            stateMachine = stateMachine,
            observeAuthStateUseCase = ObserveAuthStateUseCase(
                object : com.yapyap.feature.auth.domain.repository.AuthRepository {
                    override val authState = authState
                    override suspend fun login(identifier: String, password: String) = error("unused")
                    override suspend fun register(
                        email: String,
                        password: String,
                        name: String,
                        phone: String,
                    ) = error("unused")
                    override suspend fun refreshToken() = error("unused")
                    override suspend fun logout() = error("unused")
                    override suspend fun updateProfile(
                        update: com.yapyap.feature.auth.domain.model.ProfileUpdate,
                    ) = error("unused")
                },
            ),
        )
        val mediaLifecycle = CallMediaLifecycle(
            callMediaRepository = ReconcilerTestMediaRepository,
            callSessionStore = store,
            stateMachine = stateMachine,
            sessionBinder = CallMediaSessionBinder(
                store,
                ReconcilerTestMediaRepository,
                CallMediaCredentialsStore(),
                entryHandler,
                dispatcherProvider,
            ),
            dispatcherProvider = dispatcherProvider,
        )
        val reconciler = CallSessionReconciler(
            callRepository = repository,
            stateMachine = stateMachine,
            mediaLifecycle = mediaLifecycle,
            entryHandler = entryHandler,
            activeCallTracker = InMemoryActiveCallSessionTracker(),
        )
        return TestFixture(store = store, repository = repository, reconciler = reconciler)
    }

    @Test
    fun reconcileActiveIncomingRestoresIncomingForCallee() = runTest {
        val fixture = createFixture()
        val ringingSession = session().copy(
            caller = CallParticipant(userId = 2, displayName = "Alice"),
            callee = CallParticipant(userId = 1, displayName = "Bob"),
        )
        fixture.repository.activeSnapshot = ActiveCallSnapshot(
            session = ringingSession,
            status = CallServerStatus.Ringing,
        )

        fixture.reconciler.reconcileActiveIncomingCall()

        val state = fixture.store.state.value
        assertIs<CallState.Incoming>(state)
        assertEquals("call-1", state.session.id)
        assertEquals("Alice", state.remoteUser.displayName)
    }

    private fun session(id: String = "call-1"): CallSession = CallSession(
        id = id,
        conversationId = 1,
        callType = CallType.Voice,
        caller = CallParticipant(userId = 2, displayName = "Bob"),
        callee = CallParticipant(userId = 1, displayName = "Alice"),
    )

    private data class TestFixture(
        val store: CallSessionStore,
        val repository: FakeCallRepository,
        val reconciler: CallSessionReconciler,
    )

    private class FakeCallRepository : CallRepository {
        var activeSnapshot: ActiveCallSnapshot? = null
        var shouldFail: Boolean = false

        override suspend fun createOutgoingSession(
            conversationId: Int,
            callType: CallType,
            calleeUserId: Int,
        ) = error("unused")

        override suspend fun fetchSession(callSessionId: String): Result<CallSession> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun reconcileSession(callSessionId: String): Result<ActiveCallSnapshot?> {
            if (shouldFail) {
                return Result.failure(IllegalStateException("network down"))
            }
            return Result.success(activeSnapshot)
        }

        override suspend fun fetchActiveCall(): Result<ActiveCallSnapshot?> = reconcileSession("")

        override suspend fun refreshCallToken(callSessionId: String): Result<ActiveCallSnapshot> =
            reconcileSession(callSessionId).map { it ?: error("missing session") }

        override suspend fun redialFromHistory(callHistoryId: Int): Result<CallSession> = error("unused")

        override suspend fun answerSession(callSessionId: String) = Result.success(Unit)
        override suspend fun rejectSession(callSessionId: String) = Result.success(Unit)
        override suspend fun endSession(callSessionId: String) = Result.success(Unit)
    }

    private object ReconcilerTestMediaRepository : com.yapyap.feature.call.domain.repository.CallMediaRepository {
        override val isMuted = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val isCameraEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val audioRoute = kotlinx.coroutines.flow.MutableStateFlow(
            com.yapyap.feature.call.domain.entity.CallAudioRoute.Earpiece,
        )
        override val connectionState = kotlinx.coroutines.flow.MutableStateFlow(
            com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Idle,
        )
        override val localVideoTrack =
            kotlinx.coroutines.flow.MutableStateFlow<com.yapyap.feature.call.domain.media.CallRenderableVideoTrack?>(null)
        override val remoteVideoTrack =
            kotlinx.coroutines.flow.MutableStateFlow<com.yapyap.feature.call.domain.media.CallRenderableVideoTrack?>(null)
        override val remoteParticipantConnected = kotlinx.coroutines.flow.MutableStateFlow(false)

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

    private class TestDispatcherProvider : com.yapyap.core.util.DispatcherProvider {
        private val dispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
}
