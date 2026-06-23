package com.yapyap.core.push

import co.touchlab.kermit.Logger
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.model.PushMessageType
import com.yapyap.core.push.model.PushNavigationTarget
import com.yapyap.core.push.model.PushPermissionStatus
import com.yapyap.core.realtime.ActiveConversationTracker
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import com.yapyap.feature.devices.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PushNotificationCoordinator(
    private val pushPlatform: PushPlatform,
    private val deviceRepository: DeviceRepository,
    private val tokenStorage: TokenStorage,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val activeConversationTracker: ActiveConversationTracker,
    private val deduplicator: PushMessageDeduplicator,
    private val navigationRouter: PushNavigationRouter,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val log = Logger.withTag("PushNotifications")
    private var scope: CoroutineScope? = null
    private val registerMutex = Mutex()
    private var initialized = false
    private var pendingNotificationOpen: Map<String, String>? = null

    val navigationTargets = navigationRouter.targets

    suspend fun initialize() {
        if (initialized) return
        val status = pushPlatform.initialize()
        when (status) {
            PushPermissionStatus.Granted -> tokenStorage.setPushPermissionDenied(false)
            PushPermissionStatus.Denied -> tokenStorage.setPushPermissionDenied(true)
            PushPermissionStatus.NotDetermined -> Unit
        }
        pushPlatform.setHandlers(
            PushPlatformHandlers(
                onMessage = { data -> scope?.launch { handlePushData(data, fromUserTap = false) } },
                onTokenRefresh = { token -> scope?.launch { onTokenRefreshed(token) } },
                onNotificationOpened = { data -> scope?.launch { handleNotificationOpened(data) } },
            ),
        )
        initialized = true
        if (
            status == PushPermissionStatus.Granted &&
            observeAuthStateUseCase().value is AuthState.Authenticated
        ) {
            registerDeviceWithBackend()
        }
    }

    fun start(parentScope: CoroutineScope) {
        scope = parentScope
        parentScope.launch(dispatcherProvider.default) {
            initialize()
        }
        parentScope.launch(dispatcherProvider.default) {
            observeAuthStateUseCase()
                .map { state -> state is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { authenticated ->
                    if (authenticated) {
                        registerDeviceWithBackend()
                    }
                }
        }
        pendingNotificationOpen?.let { data ->
            pendingNotificationOpen = null
            parentScope.launch { handlePushData(data, fromUserTap = true) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        tokenStorage.setPushPermissionDenied(!granted)
        if (granted) {
            scope?.launch {
                registerDeviceWithBackend()
            }
        }
    }

    fun onAppForeground() {
        scope?.launch {
            pushPlatform.onAppForeground()
            if (observeAuthStateUseCase().value is AuthState.Authenticated) {
                verifyTokenRegistration()
                conversationRepository.refreshConversations()
            }
        }
    }

    fun onAppBackground() = Unit

    suspend fun registerDeviceWithBackend() {
        if (tokenStorage.isPushPermissionDenied()) {
            log.d { "Skipping device register — notification permission denied" }
            return
        }
        registerMutex.withLock {
            val token = fetchDeviceTokenWithRetry()
            if (token == null) {
                log.w { "Skipping device register — no FCM token yet" }
                return
            }
            val previous = tokenStorage.getDeviceToken()
            if (token == previous && previous != null) {
                return
            }
            registerWithRetry(token)
        }
    }

    private suspend fun fetchDeviceTokenWithRetry(): String? {
        repeat(TOKEN_FETCH_ATTEMPTS) { attempt ->
            val token = pushPlatform.getDeviceToken()
            if (!token.isNullOrBlank()) {
                return token
            }
            if (attempt < TOKEN_FETCH_ATTEMPTS - 1) {
                delay(TOKEN_FETCH_DELAY_MS)
            }
        }
        return null
    }

    suspend fun unregisterDevice() {
        val token = tokenStorage.getDeviceToken() ?: pushPlatform.getDeviceToken()
        if (!token.isNullOrBlank()) {
            runCatching { deviceRepository.unregisterDevice(token) }
                .onFailure { error -> log.w { "Device unregister failed: ${error.message}" } }
        }
        runCatching { pushPlatform.deleteToken() }
        tokenStorage.saveDeviceToken(null)
    }

    fun handleNotificationOpened(data: Map<String, String>) {
        val activeScope = scope
        if (activeScope == null) {
            pendingNotificationOpen = data
            return
        }
        activeScope.launch { handlePushData(data, fromUserTap = true) }
    }

    suspend fun handlePushData(data: Map<String, String>, fromUserTap: Boolean) {
        val payload = PushPayloadParser.parse(data)
        when (payload.type) {
            PushMessageType.NewMessage -> handleNewMessage(payload, fromUserTap)
            PushMessageType.IncomingCall -> handleIncomingCall(payload, fromUserTap)
            PushMessageType.Unknown -> log.d { "Ignoring push with unknown type: $data" }
        }
    }

    private suspend fun handleNewMessage(payload: com.yapyap.core.push.model.PushPayload, fromUserTap: Boolean) {
        val messageId = payload.messageId
        val conversationId = payload.conversationId?.toIntOrNull()

        if (messageId != null && deduplicator.hasSeen(messageId)) {
            return
        }
        if (messageId != null) {
            deduplicator.markSeen(messageId)
        }

        syncIncomingPushMessage(payload)

        if (fromUserTap) {
            payload.conversationId?.let { id ->
                navigationRouter.navigate(PushNavigationTarget.Chat(id))
            }
        }
    }

    private suspend fun syncIncomingPushMessage(payload: com.yapyap.core.push.model.PushPayload) {
        val message = payload.toIncomingMessage() ?: return
        val currentUserId = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id
        chatRepository.upsertIncomingMessage(message)
        val isViewing = activeConversationTracker.isViewing(message.conversationId)
        val wasUnknown = conversationRepository.applyIncomingMessage(
            conversationId = message.conversationId,
            message = message,
            currentUserId = currentUserId,
            isViewingConversation = isViewing,
        )
        if (wasUnknown) {
            conversationRepository.refreshConversations()
        }
    }

    private fun handleIncomingCall(payload: com.yapyap.core.push.model.PushPayload, fromUserTap: Boolean) {
        if (fromUserTap) {
            payload.callSessionId?.let { sessionId ->
                navigationRouter.navigate(PushNavigationTarget.IncomingCall(sessionId))
            }
        }
    }

    private suspend fun onTokenRefreshed(token: String) {
        tokenStorage.saveDeviceToken(token)
        if (observeAuthStateUseCase().value is AuthState.Authenticated) {
            registerWithRetry(token)
        }
    }

    private suspend fun verifyTokenRegistration() {
        if (tokenStorage.isPushPermissionDenied()) return
        val current = pushPlatform.getDeviceToken() ?: return
        val stored = tokenStorage.getDeviceToken()
        if (current != stored || stored.isNullOrBlank()) {
            registerWithRetry(current)
        }
    }

    private suspend fun registerWithRetry(token: String) {
        val maxAttempts = 3
        repeat(maxAttempts) { attempt ->
            val result = deviceRepository.registerDevice(
                deviceToken = token,
                platform = currentPushPlatformType(),
                deviceName = currentDeviceName(),
                appVersion = currentAppVersion(),
            )
            if (result.isSuccess) {
                tokenStorage.saveDeviceToken(token)
                log.d { "Device registered for push" }
                return
            }
            log.w { "Device register attempt ${attempt + 1} failed: ${result.exceptionOrNull()?.message}" }
            if (attempt < maxAttempts - 1) {
                delay((1L shl attempt) * 1_000L)
            }
        }
    }

    private companion object {
        const val TOKEN_FETCH_ATTEMPTS = 4
        const val TOKEN_FETCH_DELAY_MS = 1_500L
    }
}
