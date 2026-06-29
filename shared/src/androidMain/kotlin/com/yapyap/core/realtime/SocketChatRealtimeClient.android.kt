package com.yapyap.core.realtime

import co.touchlab.kermit.Logger
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.RealtimeTransportClock
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.diagnostics.SocketRoomTrace
import com.yapyap.core.network.Environment
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.chat.data.dto.MessageDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.min

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L

class SocketChatRealtimeClient(
    private val socketEventParser: SocketEventParser,
    private val tokenStorage: TokenStorage,
    dispatcherProvider: DispatcherProvider,
) : ChatRealtimeClient {
    private val log = Logger.withTag("SocketIO")
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val connectMutex = Mutex()

    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    override val isReady: Boolean
        get() = _connectionState.value is RealtimeConnectionState.Ready

    override fun connectionDiagnostics(): SocketConnectionDiagnostics {
        val currentSocket = socket
        val readyState = _connectionState.value as? RealtimeConnectionState.Ready
        return SocketConnectionDiagnostics(
            connected = currentSocket?.connected() == true,
            authenticated = readyState != null,
            socketId = currentSocket?.id(),
            userId = readyState?.userId,
        )
    }

    private var socket: Socket? = null
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private var backoffMs = INITIAL_BACKOFF_MS
    private var activeAccessToken: String? = null

    override fun connect(accessToken: String) {
        shouldStayConnected = true
        activeAccessToken = accessToken
        reconnectAttempt = 0
        reconnectJob?.cancel()
        log.d { "connect requested to ${Environment.SOCKET_URL}" }
        SocketRoomTrace.stage(
            stage = "SOCKET_CONNECT_ATTEMPT",
            extra = mapOf("url" to Environment.SOCKET_URL),
        )
        RuntimeDiagnostics.event("Socket", "connect requested url=${Environment.SOCKET_URL}")
        reconnectJob = scope.launch {
            connectInternal(accessToken)
        }
    }

    override fun disconnect() {
        SocketRoomTrace.stage(
            stage = "SOCKET_DISCONNECT_REQUESTED",
            extra = mapOf(
                "socketId" to (socket?.id() ?: "null"),
                "reason" to "client_disconnect",
            ),
        )
        RuntimeDiagnostics.event("Socket", "disconnect requested")
        shouldStayConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        teardownSocket()
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    override fun refreshAccessToken(accessToken: String) {
        if (!shouldStayConnected) return
        activeAccessToken = accessToken
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            connectInternal(accessToken)
        }
    }

    override suspend fun sendMessage(payload: SocketSendMessagePayload): Result<MessageDto> {
        val currentSocket = socket
        if (!isReady || currentSocket == null || !currentSocket.connected()) {
            return Result.failure(IllegalStateException("Socket not connected"))
        }

        OutboxPipelineTrace.stage(
            stage = "SOCKET_SEND_BEGIN",
            clientMessageId = payload.clientMessageId,
            conversationId = payload.conversationId,
            extra = mapOf(
                "socketId" to (currentSocket.id() ?: "null"),
                "connected" to currentSocket.connected().toString(),
            ),
        )

        return suspendCancellableCoroutine { continuation ->
            val jsonPayload = JSONObject(socketEventParser.encodePayload(payload))
            currentSocket.emit(EVENT_MESSAGE_SEND, jsonPayload, io.socket.client.Ack { args ->
                if (continuation.isCancelled) return@Ack
                val raw = args.firstOrNull()?.toJsonString()
                if (raw == null) {
                    OutboxPipelineTrace.stage(
                        stage = "SOCKET_SEND_ACK",
                        clientMessageId = payload.clientMessageId,
                        conversationId = payload.conversationId,
                        extra = mapOf("success" to "false", "error" to "empty_ack"),
                    )
                    continuation.resume(Result.failure(IllegalStateException("Empty socket ack")))
                    return@Ack
                }
                val parsed = socketEventParser.parseSendMessageAck(raw)
                OutboxPipelineTrace.stage(
                    stage = "SOCKET_SEND_ACK",
                    clientMessageId = payload.clientMessageId,
                    serverMessageId = parsed.getOrNull()?.id,
                    conversationId = payload.conversationId,
                    extra = mapOf(
                        "success" to parsed.isSuccess.toString(),
                        "error" to parsed.exceptionOrNull()?.message,
                    ),
                )
                continuation.resume(parsed)
            })
        }
    }

    override fun emitTypingStart(conversationId: Int) {
        emitConversationEvent(EVENT_TYPING_START, conversationId)
    }

    override fun emitTypingStop(conversationId: Int) {
        emitConversationEvent(EVENT_TYPING_STOP, conversationId)
    }

    override suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit> {
        val currentSocket = socket
        if (!isReady || currentSocket == null || !currentSocket.connected()) {
            return Result.failure(IllegalStateException("Socket not connected"))
        }

        val payload = SocketMessageReadPayload(
            conversationId = conversationId,
            messageId = messageId,
        )

        return suspendCancellableCoroutine { continuation ->
            val jsonPayload = JSONObject(socketEventParser.encodePayload(payload))
            currentSocket.emit(EVENT_MESSAGE_READ, jsonPayload, io.socket.client.Ack { args ->
                if (continuation.isCancelled) return@Ack
                val raw = args.firstOrNull()?.toJsonString()
                if (raw == null) {
                    continuation.resume(Result.failure(IllegalStateException("Empty socket ack")))
                    return@Ack
                }
                continuation.resume(socketEventParser.parseAckSuccess(raw))
            })
        }
    }

    override suspend fun markDelivered(conversationId: Int, messageId: Int): Result<Unit> {
        val currentSocket = socket
        val diagnostics = connectionDiagnostics()
        if (!isReady || currentSocket == null || !currentSocket.connected()) {
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "MESSAGE_DELIVERY_ACK_SKIPPED reason=socket_not_ready conversationId=$conversationId " +
                    "messageId=$messageId socketConnected=${diagnostics.connected} " +
                    "socketAuthenticated=${diagnostics.authenticated} socketId=${diagnostics.socketId} " +
                    "socketUserId=${diagnostics.userId}",
            )
            return Result.failure(IllegalStateException("Socket not connected"))
        }

        val payload = SocketMessageDeliveredPayload(
            conversationId = conversationId,
            messageId = messageId,
        )
        val jsonPayload = JSONObject(socketEventParser.encodePayload(payload))

        RuntimeDiagnostics.event(
            "DeliveryAck",
            "SOCKET_EMIT event=$EVENT_MESSAGE_DELIVERED conversationId=$conversationId messageId=$messageId " +
                "socketConnected=${diagnostics.connected} socketAuthenticated=${diagnostics.authenticated}",
        )
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "MESSAGE_DELIVERY_ACK_EMIT event=$EVENT_MESSAGE_DELIVERED conversationId=$conversationId " +
                "messageId=$messageId socketConnected=${diagnostics.connected} " +
                "socketAuthenticated=${diagnostics.authenticated} socketId=${diagnostics.socketId} " +
                "socketUserId=${diagnostics.userId} payload=$jsonPayload",
        )

        return suspendCancellableCoroutine { continuation ->
            currentSocket.emit(EVENT_MESSAGE_DELIVERED, jsonPayload, io.socket.client.Ack { args ->
                if (continuation.isCancelled) return@Ack
                val raw = args.firstOrNull()?.toJsonString()
                if (raw == null) {
                    RuntimeDiagnostics.event(
                        "DeliveryAck",
                        "MESSAGE_DELIVERY_ACK_SKIPPED reason=empty_socket_ack conversationId=$conversationId " +
                            "messageId=$messageId socketId=${diagnostics.socketId}",
                    )
                    continuation.resume(Result.failure(IllegalStateException("Empty socket ack")))
                    return@Ack
                }
                val result = socketEventParser.parseAckSuccess(raw)
                if (result.isSuccess) {
                    RuntimeDiagnostics.event("DeliveryAck", "SOCKET_SUCCESS conversationId=$conversationId messageId=$messageId")
                    RuntimeDiagnostics.event(
                        "DeliveryAck",
                        "MESSAGE_DELIVERY_ACK_SUCCESS conversationId=$conversationId messageId=$messageId " +
                            "socketId=${diagnostics.socketId} ackResponse=$raw",
                    )
                } else {
                    RuntimeDiagnostics.event(
                        "DeliveryAck",
                        "SOCKET_FAILURE conversationId=$conversationId messageId=$messageId " +
                            "error=${result.exceptionOrNull()?.message}",
                    )
                    RuntimeDiagnostics.event(
                        "DeliveryAck",
                        "MESSAGE_DELIVERY_ACK_SKIPPED reason=ack_parse_failed conversationId=$conversationId " +
                            "messageId=$messageId socketId=${diagnostics.socketId} ackResponse=$raw " +
                            "error=${result.exceptionOrNull()?.message}",
                    )
                }
                continuation.resume(result)
            })
        }
    }

    private suspend fun connectInternal(accessToken: String) {
        connectMutex.withLock {
            teardownSocket()
            _connectionState.value = RealtimeConnectionState.Connecting

            val options = IO.Options.builder()
                .setAuth(mapOf("token" to accessToken))
                .setForceNew(true)
                .setReconnection(false)
                // Allow polling fallback — websocket-only often fails on Android emulators.
                .setTransports(arrayOf("polling", "websocket"))
                .build()

            log.d { "opening socket to ${Environment.SOCKET_URL}" }
            val newSocket = IO.socket(Environment.SOCKET_URL, options)
            registerListeners(newSocket)
            socket = newSocket
            newSocket.connect()
        }
    }

    private fun registerListeners(currentSocket: Socket) {
        currentSocket.on(Socket.EVENT_CONNECT) {
            log.d { "socket connected" }
            SocketRoomTrace.stage(
                stage = "SOCKET_TCP_CONNECTED",
                extra = mapOf(
                    "socketId" to (currentSocket.id() ?: "null"),
                    "authenticated" to "false",
                ),
            )
            RuntimeDiagnostics.event("Socket", "connected socketId=${currentSocket.id()}")
            _connectionState.value = RealtimeConnectionState.Connected
            backoffMs = INITIAL_BACKOFF_MS
            reconnectAttempt = 0
        }

        currentSocket.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull()?.toString() ?: "unknown"
            log.w { "socket disconnected: $reason" }
            SocketRoomTrace.stage(
                stage = "SOCKET_DISCONNECT",
                extra = mapOf(
                    "socketId" to (currentSocket.id() ?: "null"),
                    "reason" to reason,
                    "wasAuthenticated" to (connectionDiagnostics().authenticated).toString(),
                ),
            )
            RealtimeTransportClock.recordSocketDisconnected()
            RuntimeDiagnostics.event("Socket", "disconnected reason=$reason socketId=${currentSocket.id()}")
            _connectionState.value = RealtimeConnectionState.Disconnected
            scheduleReconnect()
        }

        currentSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.firstOrNull()?.toString() ?: "unknown"
            log.w { "socket connect_error: $error" }
            SocketRoomTrace.stage(
                stage = "SOCKET_CONNECT_ERROR",
                extra = mapOf("error" to error),
            )
            _connectionState.value = RealtimeConnectionState.Disconnected
            scheduleReconnect()
        }

        currentSocket.on(EVENT_SOCKET_READY) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseReady(raw) }
                .onSuccess { event ->
                    log.d { "socket ready for userId=${event.userId}" }
                    val socketId = currentSocket.id() ?: "null"
                    SocketRoomTrace.stage(
                        stage = "SOCKET_AUTHENTICATED",
                        extra = mapOf(
                            "userId" to event.userId.toString(),
                            "socketId" to socketId,
                            "note" to "rooms_joined_server_side_before_socket_ready",
                        ),
                    )
                    ReceiverSyncTrace.stage(
                        stage = "SOCKET_AUTHENTICATED",
                        trigger = "SocketChatRealtimeClient.socket:ready",
                        extra = mapOf(
                            "userId" to event.userId.toString(),
                            "socketId" to socketId,
                            "socketState" to "Ready",
                        ),
                    )
                    RuntimeDiagnostics.event("Socket", "SOCKET_CONNECTED userId=${event.userId} socketId=$socketId")
                    RealtimeTransportClock.recordSocketReady(
                        userId = event.userId,
                        socketId = currentSocket.id(),
                    )
                    OutboxPipelineTrace.stage(
                        stage = "SOCKET_CONNECTED",
                        extra = mapOf(
                            "userId" to event.userId.toString(),
                            "socketId" to (currentSocket.id() ?: "null"),
                        ),
                    )
                    _connectionState.value = RealtimeConnectionState.Ready(event.userId)
                    scope.launch { _events.emit(event) }
                }
                .onFailure { error -> log.w(error) { "failed to parse socket:ready: $raw" } }
        }

        currentSocket.on(EVENT_MESSAGE_NEW) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseMessageNew(raw) }
                .onSuccess { event ->
                    SocketRoomTrace.stage(
                        stage = "SOCKET_MESSAGE_NEW",
                        extra = mapOf(
                            "socketId" to (currentSocket.id() ?: "null"),
                            "conversationId" to event.conversationId.toString(),
                            "messageId" to (event.message.id?.toString() ?: "null"),
                            "clientMessageId" to (event.message.clientMessageId ?: "null"),
                            "authenticated" to connectionDiagnostics().authenticated.toString(),
                        ),
                    )
                    scope.launch { _events.emit(event) }
                }
                .onFailure { error -> log.w(error) { "failed to parse message:new: $raw" } }
        }

        currentSocket.on(EVENT_MESSAGE_STATUS) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseMessageStatus(raw) }
                .onSuccess { event -> scope.launch { _events.emit(event) } }
                .onFailure { error -> log.w(error) { "failed to parse message:status: $raw" } }
        }

        currentSocket.on(EVENT_TYPING) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseTyping(raw) }
                .onSuccess { event -> scope.launch { _events.emit(event) } }
                .onFailure { error -> log.w(error) { "failed to parse typing: $raw" } }
        }

        currentSocket.on(EVENT_PRESENCE_UPDATE) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parsePresenceUpdate(raw) }
                .onSuccess { event -> scope.launch { _events.emit(event) } }
                .onFailure { error -> log.w(error) { "failed to parse presence:update: $raw" } }
        }
    }

    private fun emitConversationEvent(eventName: String, conversationId: Int) {
        val currentSocket = socket ?: return
        if (!isReady || !currentSocket.connected()) return
        val payload = JSONObject(
            socketEventParser.encodePayload(SocketConversationPayload(conversationId)),
        )
        currentSocket.emit(eventName, payload)
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt += 1
            SocketRoomTrace.stage(
                stage = "SOCKET_RECONNECT_ATTEMPT",
                extra = mapOf(
                    "attempt" to reconnectAttempt.toString(),
                    "backoffMs" to backoffMs.toString(),
                    "previousSocketId" to (socket?.id() ?: "null"),
                ),
            )
            RuntimeDiagnostics.event("Socket", "reconnect attempt=$reconnectAttempt backoffMs=$backoffMs")
            _connectionState.value = RealtimeConnectionState.Reconnecting(reconnectAttempt)
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            val token = tokenStorage.getAccessToken() ?: activeAccessToken
            if (token.isNullOrBlank()) {
                _connectionState.value = RealtimeConnectionState.Disconnected
                return@launch
            }
            activeAccessToken = token
            connectInternal(token)
        }
    }

    private fun teardownSocket() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    private fun Any?.toJsonString(): String? = when (this) {
        is JSONObject -> toString()
        is String -> this
        else -> null
    }

    private companion object {
        const val EVENT_SOCKET_READY = "socket:ready"
        const val EVENT_MESSAGE_SEND = "message:send"
        const val EVENT_MESSAGE_NEW = "message:new"
        const val EVENT_MESSAGE_STATUS = "message:status"
        const val EVENT_MESSAGE_READ = "message:read"
        const val EVENT_MESSAGE_DELIVERED = "message:delivered"
        const val EVENT_TYPING_START = "typing:start"
        const val EVENT_TYPING_STOP = "typing:stop"
        const val EVENT_TYPING = "typing"
        const val EVENT_PRESENCE_UPDATE = "presence:update"
    }
}

actual fun createChatRealtimeClient(
    socketEventParser: SocketEventParser,
    tokenStorage: TokenStorage,
    dispatcherProvider: DispatcherProvider,
): ChatRealtimeClient = SocketChatRealtimeClient(
    socketEventParser = socketEventParser,
    tokenStorage = tokenStorage,
    dispatcherProvider = dispatcherProvider,
)
