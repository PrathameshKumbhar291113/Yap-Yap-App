package com.yapyap.core.realtime

import co.touchlab.kermit.Logger
import com.yapyap.core.datastore.TokenStorage
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
        reconnectJob = scope.launch {
            connectInternal(accessToken)
        }
    }

    override fun disconnect() {
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

        return suspendCancellableCoroutine { continuation ->
            val jsonPayload = JSONObject(socketEventParser.encodePayload(payload))
            currentSocket.emit(EVENT_MESSAGE_SEND, jsonPayload, io.socket.client.Ack { args ->
                if (continuation.isCancelled) return@Ack
                val raw = args.firstOrNull()?.toJsonString()
                if (raw == null) {
                    continuation.resume(Result.failure(IllegalStateException("Empty socket ack")))
                    return@Ack
                }
                continuation.resume(socketEventParser.parseSendMessageAck(raw))
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
            _connectionState.value = RealtimeConnectionState.Connected
            backoffMs = INITIAL_BACKOFF_MS
            reconnectAttempt = 0
        }

        currentSocket.on(Socket.EVENT_DISCONNECT) { args ->
            log.w { "socket disconnected: ${args.firstOrNull()}" }
            _connectionState.value = RealtimeConnectionState.Disconnected
            scheduleReconnect()
        }

        currentSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.firstOrNull()
            log.w { "socket connect_error: $error" }
            _connectionState.value = RealtimeConnectionState.Disconnected
            scheduleReconnect()
        }

        currentSocket.on(EVENT_SOCKET_READY) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseReady(raw) }
                .onSuccess { event ->
                    log.d { "socket ready for userId=${event.userId}" }
                    _connectionState.value = RealtimeConnectionState.Ready(event.userId)
                    scope.launch { _events.emit(event) }
                }
                .onFailure { error -> log.w(error) { "failed to parse socket:ready: $raw" } }
        }

        currentSocket.on(EVENT_MESSAGE_NEW) { args ->
            val raw = args.firstOrNull()?.toJsonString() ?: return@on
            runCatching { socketEventParser.parseMessageNew(raw) }
                .onSuccess { event -> scope.launch { _events.emit(event) } }
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
