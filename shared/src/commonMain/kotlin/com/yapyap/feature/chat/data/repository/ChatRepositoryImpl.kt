package com.yapyap.feature.chat.data.repository

import com.yapyap.core.network.getOrThrow
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.core.realtime.SocketSendMessagePayload
import com.yapyap.feature.chat.data.dto.SendMessageRequestDto
import com.yapyap.feature.chat.data.mapper.toDomain
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.chat.util.currentTimestampIso
import com.yapyap.feature.chat.util.newClientMessageId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatRepositoryImpl(
    private val chatApi: ChatApi,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
) : ChatRepository {
    private data class ConversationCache(
        val messages: MutableStateFlow<List<Message>> = MutableStateFlow(emptyList()),
        var nextCursor: Int? = null,
        var hasMore: Boolean = true,
        var isLoadingOlder: Boolean = false,
        var initialLoaded: Boolean = false,
    )

    private val caches = mutableMapOf<Int, ConversationCache>()
    private val mutex = Mutex()

    override fun observeMessages(conversationId: Int): Flow<List<Message>> {
        return cacheFor(conversationId).messages.asStateFlow()
    }

    override suspend fun loadLatestMessages(conversationId: Int): Result<Unit> {
        return mutex.withLock {
            val cache = cacheFor(conversationId)
            runCatching {
                val page = chatApi.getMessages(conversationId).getOrThrow()
                val serverMessages = page.messages.map { it.toDomain() }
                val pending = cache.messages.value.filter { it.isPending || it.status == MessageStatus.FAILED }
                cache.messages.value = mergeMessages(serverMessages, pending)
                cache.nextCursor = page.nextCursor
                cache.hasMore = page.nextCursor != null
                cache.initialLoaded = true
                ackSyncedIncomingMessages(
                    conversationId = conversationId,
                    messages = serverMessages,
                    trigger = "ChatRepositoryImpl.loadLatestMessages",
                )
            }
        }
    }

    private suspend fun ackSyncedIncomingMessages(
        conversationId: Int,
        messages: List<Message>,
        trigger: String,
    ) {
        messages.forEach { message ->
            val messageId = message.id ?: return@forEach
            deliveryAckService.onMessageReceived(
                source = "sync",
                conversationId = conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
            deliveryAckService.onMessagePersisted(
                source = "sync",
                conversationId = conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
        }
    }

    override suspend fun loadOlderMessages(conversationId: Int): Result<Unit> {
        return mutex.withLock {
            val cache = cacheFor(conversationId)
            val cursor = cache.nextCursor
            if (!cache.hasMore || cache.isLoadingOlder || cursor == null) {
                return Result.success(Unit)
            }
            cache.isLoadingOlder = true
            runCatching {
                val page = chatApi.getMessages(conversationId, cursor = cursor).getOrThrow()
                val olderMessages = page.messages.map { it.toDomain() }
                cache.messages.value = mergeMessages(olderMessages, cache.messages.value)
                cache.nextCursor = page.nextCursor
                cache.hasMore = page.nextCursor != null
            }.also {
                cache.isLoadingOlder = false
            }
        }
    }

    override suspend fun sendMessage(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Result<Message> {
        val clientMessageId = newClientMessageId()
        val pending = Message(
            id = null,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            messageType = MessageType.TEXT,
            mediaUrl = null,
            status = MessageStatus.PENDING,
            clientMessageId = clientMessageId,
            createdAt = currentTimestampIso(),
            isPending = true,
        )

        mutex.withLock {
            val cache = cacheFor(conversationId)
            cache.messages.value = cache.messages.value + pending
        }

        if (chatRealtimeClient.isReady) {
            val socketResult = chatRealtimeClient.sendMessage(
                SocketSendMessagePayload(
                    conversationId = conversationId,
                    content = content,
                    messageType = MessageType.toApi(MessageType.TEXT),
                    clientMessageId = clientMessageId,
                ),
            )
            if (socketResult.isSuccess) {
                val sent = socketResult.getOrThrow().toDomain()
                mutex.withLock {
                    val cache = cacheFor(conversationId)
                    cache.messages.value = cache.messages.value.map { message ->
                        if (message.clientMessageId == clientMessageId) {
                            mergeMessageFields(message, sent.copy(isPending = false))
                        } else {
                            message
                        }
                    }
                }
                return Result.success(sent)
            }
        }

        return runCatching {
            val sent = chatApi.sendMessage(
                conversationId = conversationId,
                request = SendMessageRequestDto(
                    content = content,
                    messageType = MessageType.toApi(MessageType.TEXT),
                    clientMessageId = clientMessageId,
                ),
            ).getOrThrow().toDomain()

            mutex.withLock {
                val cache = cacheFor(conversationId)
                cache.messages.value = cache.messages.value.map { message ->
                    if (message.clientMessageId == clientMessageId) {
                        mergeMessageFields(message, sent.copy(isPending = false))
                    } else {
                        message
                    }
                }
            }
            sent
        }.onFailure {
            mutex.withLock {
                val cache = cacheFor(conversationId)
                cache.messages.value = cache.messages.value.map { message ->
                    if (message.clientMessageId == clientMessageId) {
                        message.copy(isPending = false, status = MessageStatus.FAILED)
                    } else {
                        message
                    }
                }
            }
        }
    }

    override suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit> {
        if (chatRealtimeClient.isReady) {
            val socketResult = chatRealtimeClient.markRead(conversationId, messageId)
            if (socketResult.isSuccess) {
                return socketResult
            }
        }

        return runCatching {
            chatApi.markRead(conversationId, messageId).getOrThrow()
        }
    }

    override fun hasMoreMessages(conversationId: Int): Boolean {
        return cacheFor(conversationId).hasMore
    }

    override fun isLoadingOlder(conversationId: Int): Boolean {
        return cacheFor(conversationId).isLoadingOlder
    }

    override suspend fun upsertIncomingMessage(message: Message) {
        mutex.withLock {
            val cache = cacheFor(message.conversationId)
            cache.messages.value = mergeMessages(listOf(message), cache.messages.value)
        }
    }

    override suspend fun applyMessageStatus(
        conversationId: Int,
        messageId: Int,
        status: MessageStatus,
        readerUserId: Int?,
    ) {
        mutex.withLock {
            val cache = cacheFor(conversationId)
            cache.messages.value = cache.messages.value.map { message ->
                if (!shouldApplyStatusUpdate(message, messageId, status, readerUserId)) {
                    message
                } else {
                    message.copy(
                        status = mergeMessageStatus(message.status, status),
                        isPending = false,
                    )
                }
            }
        }
    }

    private fun shouldApplyStatusUpdate(
        message: Message,
        messageId: Int,
        status: MessageStatus,
        readerUserId: Int?,
    ): Boolean {
        val id = message.id ?: return false

        // Outgoing receipts must reflect the recipient's actions, not the sender marking their own chat read.
        if (readerUserId != null && message.senderId == readerUserId) {
            return false
        }

        if (id == messageId) return true
        if (readerUserId == null) return false
        if (id > messageId) return false
        return status == MessageStatus.READ || status == MessageStatus.DELIVERED
    }

    private fun mergeMessageStatus(current: MessageStatus, incoming: MessageStatus): MessageStatus {
        if (current == MessageStatus.READ || incoming == MessageStatus.READ) {
            return MessageStatus.READ
        }
        if (incoming == MessageStatus.DELIVERED || current == MessageStatus.DELIVERED) {
            return MessageStatus.DELIVERED
        }
        return incoming
    }

    private fun cacheFor(conversationId: Int): ConversationCache {
        return caches.getOrPut(conversationId) { ConversationCache() }
    }

    private fun mergeMessages(primary: List<Message>, secondary: List<Message>): List<Message> {
        val merged = LinkedHashMap<String, Message>()
        (primary + secondary).forEach { message ->
            val existing = merged[message.stableKey]
            merged[message.stableKey] = if (existing == null) {
                message
            } else {
                mergeMessageFields(existing, message)
            }
        }
        return merged.values.sortedWith(compareBy<Message> { it.id ?: Int.MAX_VALUE })
    }

    private fun mergeMessageFields(existing: Message, incoming: Message): Message {
        val base = if (incoming.id != null) incoming else existing
        return base.copy(
            status = mergeMessageStatus(existing.status, incoming.status),
            isPending = existing.isPending && incoming.isPending,
        )
    }
}
