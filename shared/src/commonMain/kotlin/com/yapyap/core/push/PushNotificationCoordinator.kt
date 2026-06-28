package com.yapyap.core.push

import co.touchlab.kermit.Logger
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.model.PushDeliveryContext
import com.yapyap.core.push.model.PushDeliveryMode
import com.yapyap.core.push.model.PushInboundMessage
import com.yapyap.core.push.model.PushMessageType
import com.yapyap.core.push.model.PushNavigationTarget
import com.yapyap.core.push.model.PushPermissionStatus
import com.yapyap.core.realtime.ActiveConversationTracker
import com.yapyap.core.realtime.IncomingMessageNotification
import com.yapyap.core.realtime.IncomingMessageNotifier
import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
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
    private val incomingMessageNotifier: IncomingMessageNotifier,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val log = Logger.withTag("PushNotifications")
    private var scope: CoroutineScope? = null
    private val registerMutex = Mutex()
    private var initialized = false
    private var started = false
    private var lastRegisteredToken: String? = null
    private var pendingNotificationOpen: Map<String, String>? = null
    private var pendingNavigationTarget: PushNavigationTarget? = null

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
                onMessage = { message -> scope?.launch { handleInboundPush(message, fromUserTap = false) } },
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
        if (started) return
        started = true
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
                        flushPendingNotificationOpenIfAuthenticated()
                        flushPendingNavigationIfAuthenticated()
                    }
                }
        }
        if (observeAuthStateUseCase().value is AuthState.Authenticated) {
            parentScope.launch(dispatcherProvider.default) {
                flushPendingNotificationOpenIfAuthenticated()
                flushPendingNavigationIfAuthenticated()
            }
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

    fun onConversationOpened(conversationId: Int) {
        pushPlatform.dismissConversationNotification(conversationId.toString())
    }

    fun onRealtimeNewMessage(
        conversationId: Int,
        messageId: String?,
        senderId: Int,
        preview: String,
        senderName: String? = null,
    ) {
        scope?.launch {
            val data = buildMap {
                put("type", PushMessageType.NewMessage.value)
                put("conversationId", conversationId.toString())
                put("senderId", senderId.toString())
                messageId?.let { put("messageId", it) }
                put("preview", preview)
                senderName?.let { put("senderName", it) }
            }
            presentNotificationIfNeeded(
                conversationId = conversationId,
                payload = PushPayloadParser.parse(data),
                delivery = PushDeliveryContext(mode = PushDeliveryMode.Realtime),
            )
        }
    }

    suspend fun registerDeviceWithBackend() {
        if (tokenStorage.isPushPermissionDenied()) {
            RuntimeDiagnostics.event("Registration", "SKIP reason=push_permission_denied")
            log.d { "Skipping device register — notification permission denied" }
            return
        }
        registerMutex.withLock {
            val token = fetchDeviceTokenWithRetry()
            if (token == null) {
                RuntimeDiagnostics.event("Registration", "SKIP reason=no_fcm_token")
                log.w { "Skipping device register — no FCM token yet" }
                return
            }
            if (token == lastRegisteredToken) {
                RuntimeDiagnostics.event(
                    "Registration",
                    "SKIP reason=registered_this_session tokenPrefix=${token.take(12)}...",
                )
                return
            }
            registerWithRetry(token, reason = "register")
        }
    }

    private suspend fun fetchDeviceTokenWithRetry(): String? {
        repeat(TOKEN_FETCH_ATTEMPTS) { attempt ->
            val token = pushPlatform.getDeviceToken()
            if (!token.isNullOrBlank()) {
                RuntimeDiagnostics.event(
                    "Registration",
                    "FCM token fetched attempt=${attempt + 1} tokenPrefix=${token.take(12)}...",
                )
                return token
            }
            RuntimeDiagnostics.event(
                "Registration",
                "FCM token missing attempt=${attempt + 1}/$TOKEN_FETCH_ATTEMPTS",
            )
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
        pendingNotificationOpen = data
        val activeScope = scope ?: return
        activeScope.launch { flushPendingNotificationOpenIfAuthenticated() }
    }

    private suspend fun flushPendingNotificationOpenIfAuthenticated() {
        if (observeAuthStateUseCase().value !is AuthState.Authenticated) return
        val data = pendingNotificationOpen ?: return
        pendingNotificationOpen = null
        handlePushData(data, fromUserTap = true)
    }

    private fun flushPendingNavigationIfAuthenticated() {
        if (observeAuthStateUseCase().value !is AuthState.Authenticated) return
        val target = pendingNavigationTarget ?: return
        pendingNavigationTarget = null
        navigationRouter.navigate(target)
    }

    private fun requestNavigation(target: PushNavigationTarget) {
        if (observeAuthStateUseCase().value is AuthState.Authenticated) {
            pendingNavigationTarget = null
            navigationRouter.navigate(target)
        } else {
            pendingNavigationTarget = target
        }
    }

    private suspend fun handleInboundPush(message: PushInboundMessage, fromUserTap: Boolean) {
        handlePushData(
            data = message.data,
            delivery = message.delivery,
            fromUserTap = fromUserTap,
        )
    }

    suspend fun handlePushData(
        data: Map<String, String>,
        delivery: PushDeliveryContext = PushDeliveryContext(),
        fromUserTap: Boolean = false,
    ) {
        val payload = PushPayloadParser.parse(data)
        log.d {
            "Push received type=${payload.type.value} mode=${delivery.mode} " +
                "osHandled=${delivery.osNotificationHandled} foreground=${isAppInForeground()} " +
                "conversationId=${payload.conversationId} messageId=${payload.messageId}"
        }
        RuntimeDiagnostics.event(
            "PushCoordinator",
            "handlePushData type=${payload.type.value} mode=${delivery.mode} " +
                "osHandled=${delivery.osNotificationHandled} foreground=${isAppInForeground()} " +
                "conversationId=${payload.conversationId} messageId=${payload.messageId}",
        )

        when (payload.type) {
            PushMessageType.NewMessage -> handleNewMessage(payload, delivery, fromUserTap)
            PushMessageType.IncomingCall -> handleIncomingCall(payload, delivery, fromUserTap)
            PushMessageType.Unknown -> log.d { "Ignoring push with unknown type: $data" }
        }
    }

    private suspend fun handleNewMessage(
        payload: com.yapyap.core.push.model.PushPayload,
        delivery: PushDeliveryContext,
        fromUserTap: Boolean,
    ) {
        val messageId = payload.messageId

        if (messageId != null && deduplicator.hasSeen(messageId)) {
            if (fromUserTap) {
                navigateToChat(payload.conversationId)
            }
            return
        }
        if (messageId != null) {
            deduplicator.markSeen(messageId)
        }

        syncIncomingPushMessage(payload, trigger = "PushNotificationCoordinator.handleNewMessage")

        if (fromUserTap) {
            navigateToChat(payload.conversationId)
        } else {
            presentNotificationIfNeeded(
                conversationId = payload.conversationId?.toIntOrNull(),
                payload = payload,
                delivery = delivery,
            )
        }
    }

    private fun navigateToChat(conversationId: String?) {
        conversationId?.let { id ->
            pushPlatform.dismissConversationNotification(id)
            requestNavigation(PushNavigationTarget.Chat(id))
        }
    }

    private suspend fun presentNotificationIfNeeded(
        conversationId: Int?,
        payload: com.yapyap.core.push.model.PushPayload,
        delivery: PushDeliveryContext,
        allowInAppOnIos: Boolean = false,
    ) {
        if (conversationId != null && activeConversationTracker.isViewing(conversationId)) {
            RuntimeDiagnostics.event(
                "PushPolicy",
                "SUPPRESS reason=viewing_same_chat conversationId=$conversationId",
            )
            pushPlatform.dismissConversationNotification(conversationId.toString())
            return
        }

        val foreground = isAppInForeground()
        val notification = PushNotificationContent.fromPayload(payload) ?: run {
            RuntimeDiagnostics.event("PushPolicy", "SUPPRESS reason=no_notification_content")
            return
        }

        if (PushLocalNotificationPolicy.shouldSuppressLocalNotification(delivery, foreground)) {
            val reason = when {
                delivery.osNotificationHandled -> "os_notification_handled"
                delivery.mode == PushDeliveryMode.Realtime && !foreground ->
                    "realtime_background_android_local_tray"
                else -> "policy_suppress"
            }
            RuntimeDiagnostics.event(
                "PushPolicy",
                "SUPPRESS reason=$reason mode=${delivery.mode} foreground=$foreground " +
                    "osHandled=${delivery.osNotificationHandled} conversationId=$conversationId",
            )
            log.d { "Suppressing local notification — OS or push path already handled tray UI" }
            return
        }

        if (
            PushLocalNotificationPolicy.shouldShowInAppBanner(
                usesInAppForegroundNotification = pushPlatform.usesInAppForegroundNotification() || allowInAppOnIos,
                appInForeground = foreground,
                messageType = payload.type,
            ) && conversationId != null
        ) {
            RuntimeDiagnostics.event(
                "PushPolicy",
                "IN_APP_BANNER conversationId=$conversationId foreground=$foreground",
            )
            incomingMessageNotifier.notify(
                IncomingMessageNotification(
                    conversationId = conversationId,
                    preview = payload.preview ?: notification.body,
                ),
            )
            return
        }

        if (
            PushLocalNotificationPolicy.shouldShowLocalTray(
                delivery = delivery,
                appInForeground = foreground,
                messageType = payload.type,
                usesInAppForegroundNotification = pushPlatform.usesInAppForegroundNotification(),
            )
        ) {
            RuntimeDiagnostics.event(
                "PushPolicy",
                "SHOW_LOCAL_TRAY conversationId=$conversationId mode=${delivery.mode} foreground=$foreground",
            )
            pushPlatform.showLocalNotification(notification)
        } else {
            RuntimeDiagnostics.event(
                "PushPolicy",
                "NO_LOCAL_TRAY conversationId=$conversationId mode=${delivery.mode} foreground=$foreground",
            )
        }
    }

    private suspend fun syncIncomingPushMessage(
        payload: com.yapyap.core.push.model.PushPayload,
        trigger: String,
    ) {
        val message = payload.toIncomingMessage() ?: return
        val currentUserId = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id
        val messageId = message.id
        val isFromOther = currentUserId != null && message.senderId != currentUserId

        if (isFromOther && messageId != null) {
            deliveryAckService.onMessageReceived(
                source = "fcm",
                conversationId = message.conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
        }

        chatRepository.upsertIncomingMessage(message)

        if (isFromOther && messageId != null) {
            deliveryAckService.onMessagePersisted(
                source = "fcm",
                conversationId = message.conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
        }

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

    private suspend fun handleIncomingCall(
        payload: com.yapyap.core.push.model.PushPayload,
        delivery: PushDeliveryContext,
        fromUserTap: Boolean,
    ) {
        if (fromUserTap) {
            payload.callSessionId?.let { sessionId ->
                requestNavigation(PushNavigationTarget.IncomingCall(sessionId))
            }
            return
        }

        val notification = PushNotificationContent.fromPayload(payload) ?: return
        val foreground = isAppInForeground()

        if (PushLocalNotificationPolicy.shouldSuppressLocalNotification(delivery, foreground)) {
            return
        }

        if (
            PushLocalNotificationPolicy.shouldShowLocalTray(
                delivery = delivery,
                appInForeground = foreground,
                messageType = payload.type,
                usesInAppForegroundNotification = pushPlatform.usesInAppForegroundNotification(),
            )
        ) {
            pushPlatform.showLocalNotification(notification)
        }
    }

    private suspend fun onTokenRefreshed(token: String) {
        RuntimeDiagnostics.event(
            "Registration",
            "token refresh tokenPrefix=${token.take(12)}...",
        )
        tokenStorage.saveDeviceToken(token)
        if (observeAuthStateUseCase().value is AuthState.Authenticated) {
            registerWithRetry(token, reason = "token_refresh")
        }
    }

    private suspend fun verifyTokenRegistration() {
        if (tokenStorage.isPushPermissionDenied()) return
        val current = pushPlatform.getDeviceToken() ?: return
        val stored = tokenStorage.getDeviceToken()
        if (current != stored || stored.isNullOrBlank()) {
            RuntimeDiagnostics.event(
                "Registration",
                "verify mismatch storedPrefix=${stored?.take(12)} currentPrefix=${current.take(12)}...",
            )
            registerWithRetry(current, reason = "verify_foreground")
        }
    }

    private suspend fun registerWithRetry(token: String, reason: String) {
        val userId = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id
        RuntimeDiagnostics.event(
            "Registration",
            "register request reason=$reason userId=$userId platform=${currentPushPlatformType().apiValue} " +
                "tokenPrefix=${token.take(12)}...",
        )
        val maxAttempts = 3
        repeat(maxAttempts) { attempt ->
            val result = deviceRepository.registerDevice(
                deviceToken = token,
                platform = currentPushPlatformType(),
                deviceName = currentDeviceName(),
                appVersion = currentAppVersion(),
            )
            if (result.isSuccess) {
                val deviceId = result.getOrNull()
                tokenStorage.saveDeviceToken(token)
                lastRegisteredToken = token
                RuntimeDiagnostics.event(
                    "Registration",
                    "register SUCCESS reason=$reason userId=$userId deviceId=$deviceId " +
                        "tokenPrefix=${token.take(12)}... attempt=${attempt + 1}",
                )
                log.d { "Device registered for push deviceId=$deviceId" }
                return
            }
            val error = result.exceptionOrNull()?.message
            RuntimeDiagnostics.event(
                "Registration",
                "register FAILED reason=$reason userId=$userId attempt=${attempt + 1} error=$error",
            )
            log.w { "Device register attempt ${attempt + 1} failed: $error" }
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
