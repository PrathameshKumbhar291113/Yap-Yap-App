package com.yapyap.feature.chat.data.repository

import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.core.network.getOrThrow
import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxGateway
import com.yapyap.feature.chat.data.mapper.toDomain
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.ReconcileResult
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter
import com.yapyap.feature.chat.domain.sync.MessageSequenceGapDetector
import com.yapyap.core.realtime.ChatRealtimeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatRepositoryImpl(
    private val chatApi: ChatApi,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
    private val outboxGateway: Lazy<OutgoingMessageOutboxGateway>,
) : ChatRepository, OutgoingMessageCacheWriter {
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
            val lastKnownMessageId = cache.messages.value.mapNotNull { it.id }.maxOrNull()
            ReceiverSyncTrace.stage(
                stage = "LOAD_LATEST_MESSAGES_BEGIN",
                conversationId = conversationId,
                lastKnownMessageId = lastKnownMessageId,
                trigger = "ChatRepositoryImpl.loadLatestMessages",
                extra = mapOf(
                    "endpoint" to "GET /api/conversations/$conversationId/messages",
                    "cachedMessageCount" to cache.messages.value.size.toString(),
                    "initialLoaded" to cache.initialLoaded.toString(),
                    "socketState" to chatRealtimeClient.connectionState.value.toString(),
                ),
            )
            runCatching {
                val page = chatApi.getMessages(conversationId).getOrThrow()
                val serverMessages = page.messages.map { it.toDomain() }
                val latestServerMessageId = serverMessages.mapNotNull { it.id }.maxOrNull()
                val outboxPending = outboxGateway.value.pendingMessagesFor(conversationId)
                cache.messages.value = mergeMessages(serverMessages, outboxPending)
                cache.nextCursor = page.nextCursor
                cache.hasMore = page.nextCursor != null
                cache.initialLoaded = true
                ReceiverSyncTrace.stage(
                    stage = "LOAD_LATEST_MESSAGES_DONE",
                    conversationId = conversationId,
                    lastKnownMessageId = lastKnownMessageId,
                    latestServerMessageId = latestServerMessageId,
                    trigger = "ChatRepositoryImpl.loadLatestMessages",
                    extra = mapOf(
                        "numberOfMessagesFetched" to serverMessages.size.toString(),
                        "mergedCacheCount" to cache.messages.value.size.toString(),
                        "chatCacheUpdated" to "true",
                        "repositoryUpdated" to "true",
                    ),
                )
                ackNewInboundMessages(
                    conversationId = conversationId,
                    messages = serverMessages,
                    trigger = "ChatRepositoryImpl.loadLatestMessages",
                )
                Unit
            }.onFailure { error ->
                ReceiverSyncTrace.stage(
                    stage = "LOAD_LATEST_MESSAGES_FAILED",
                    conversationId = conversationId,
                    lastKnownMessageId = lastKnownMessageId,
                    trigger = "ChatRepositoryImpl.loadLatestMessages",
                    extra = mapOf("error" to error.message),
                )
            }
        }
    }

    override fun getLocalSyncSnapshot(conversationId: Int): ChatSyncSnapshot {
        val cache = cacheFor(conversationId)
        val messages = cache.messages.value
        return ChatSyncSnapshot(
            conversationId = conversationId,
            localLatestMessageId = messages.mapNotNull { it.id }.maxOrNull(),
            cachedMessageCount = messages.size,
            initialLoaded = cache.initialLoaded,
            pendingMessageCount = messages.count(::isPendingOutboxMessage),
        )
    }

    override fun needsInteriorGapRepair(conversationId: Int, serverLatestMessageId: Int?): Boolean {
        val cache = cacheFor(conversationId)
        val localServerIds = cache.messages.value.mapNotNull { it.id }
        return MessageSequenceGapDetector.needsGapRepair(
            snapshot = getLocalSyncSnapshot(conversationId),
            serverLatestMessageId = serverLatestMessageId,
            localServerIds = localServerIds,
            hasMoreOlderMessages = cache.hasMore,
        )
    }

    override suspend fun reconcileConversation(
        conversationId: Int,
        serverLatestMessageId: Int?,
        trigger: String,
    ): Result<ReconcileResult> {
        return mutex.withLock {
            val cache = cacheFor(conversationId)
            val beforeMessages = cache.messages.value
            val beforeKeys = beforeMessages.map { it.stableKey }.toSet()
            val localLatestMessageId = beforeMessages.mapNotNull { it.id }.maxOrNull()
            val pendingBefore = beforeMessages.count(::isPendingOutboxMessage)

            if (serverLatestMessageId == null) {
                return@withLock Result.success(
                    ReconcileResult(
                        conversationId = conversationId,
                        strategy = ReconcileStrategy.SKIP_NO_SERVER_MESSAGES,
                        localLatestMessageId = localLatestMessageId,
                        serverLatestMessageId = null,
                        messagesDownloaded = 0,
                        messagesMerged = 0,
                        duplicatesIgnored = 0,
                        pendingMessagesPreserved = pendingBefore,
                    ),
                )
            }

            if (localLatestMessageId != null && localLatestMessageId >= serverLatestMessageId) {
                return@withLock runCatching {
                    reconcileWhenAppearingCurrent(
                        conversationId = conversationId,
                        cache = cache,
                        beforeMessages = beforeMessages,
                        beforeKeys = beforeKeys,
                        localLatestMessageId = localLatestMessageId,
                        serverLatestMessageId = serverLatestMessageId,
                        pendingBefore = pendingBefore,
                        trigger = trigger,
                    )
                }
            }

            runCatching {
                val strategy: ReconcileStrategy
                val downloaded: List<Message>
                if (localLatestMessageId != null) {
                    strategy = ReconcileStrategy.INCREMENTAL_AFTER
                    val page = chatApi.getMessages(
                        conversationId = conversationId,
                        after = localLatestMessageId,
                    ).getOrThrow()
                    downloaded = page.messages.map { it.toDomain() }
                } else {
                    strategy = ReconcileStrategy.FULL_PAGE
                    val page = chatApi.getMessages(conversationId).getOrThrow()
                    downloaded = page.messages.map { it.toDomain() }
                    cache.nextCursor = page.nextCursor
                    cache.hasMore = page.nextCursor != null
                    cache.initialLoaded = true
                }

                val duplicatesIgnored = downloaded.count { it.stableKey in beforeKeys }
                val messagesMerged = downloaded.size - duplicatesIgnored
                val outboxPending = outboxGateway.value.pendingMessagesFor(conversationId)
                val merged = if (strategy == ReconcileStrategy.INCREMENTAL_AFTER) {
                    mergeMessages(mergeMessages(beforeMessages, downloaded), outboxPending)
                } else {
                    mergeMessages(downloaded, outboxPending)
                }
                cache.messages.value = merged
                val pendingAfter = merged.count(::isPendingOutboxMessage)

                val acksSent = ackNewInboundMessages(
                    conversationId = conversationId,
                    messages = downloaded.filter { message ->
                        message.id != null && message.stableKey !in beforeKeys
                    },
                    trigger = "ChatRepositoryImpl.reconcileConversation:$trigger",
                )

                ReconcileResult(
                    conversationId = conversationId,
                    strategy = strategy,
                    localLatestMessageId = localLatestMessageId,
                    serverLatestMessageId = serverLatestMessageId,
                    messagesDownloaded = downloaded.size,
                    messagesMerged = messagesMerged,
                    duplicatesIgnored = duplicatesIgnored,
                    pendingMessagesPreserved = pendingAfter,
                    acksSent = acksSent,
                )
            }
        }
    }

    private suspend fun reconcileWhenAppearingCurrent(
        conversationId: Int,
        cache: ConversationCache,
        beforeMessages: List<Message>,
        beforeKeys: Set<String>,
        localLatestMessageId: Int,
        serverLatestMessageId: Int,
        pendingBefore: Int,
        trigger: String,
    ): ReconcileResult {
        val localServerIds = beforeMessages.mapNotNull { it.id }
        val snapshot = ChatSyncSnapshot(
            conversationId = conversationId,
            localLatestMessageId = localLatestMessageId,
            cachedMessageCount = beforeMessages.size,
            initialLoaded = cache.initialLoaded,
            pendingMessageCount = pendingBefore,
        )
        if (!MessageSequenceGapDetector.needsGapRepair(
                snapshot = snapshot,
                serverLatestMessageId = serverLatestMessageId,
                localServerIds = localServerIds,
                hasMoreOlderMessages = cache.hasMore,
            )
        ) {
            ReceiverSyncTrace.stage(
                stage = "RECONCILE_SKIP_ALREADY_CURRENT",
                conversationId = conversationId,
                lastKnownMessageId = localLatestMessageId,
                latestServerMessageId = serverLatestMessageId,
                trigger = trigger,
                extra = mapOf(
                    "localServerIdCount" to localServerIds.size.toString(),
                    "contiguous" to MessageSequenceGapDetector.isContiguousRange(localServerIds).toString(),
                ),
            )
            return ReconcileResult(
                conversationId = conversationId,
                strategy = ReconcileStrategy.SKIP_ALREADY_CURRENT,
                localLatestMessageId = localLatestMessageId,
                serverLatestMessageId = serverLatestMessageId,
                messagesDownloaded = 0,
                messagesMerged = 0,
                duplicatesIgnored = 0,
                pendingMessagesPreserved = pendingBefore,
            )
        }

        val gaps = MessageSequenceGapDetector.interiorGaps(localServerIds)
        ReceiverSyncTrace.stage(
            stage = "GAP_REPAIR_DETECTED",
            conversationId = conversationId,
            lastKnownMessageId = localLatestMessageId,
            latestServerMessageId = serverLatestMessageId,
            trigger = trigger,
            extra = mapOf(
                "interiorGapCount" to gaps.size.toString(),
                "localServerIds" to localServerIds.sorted().joinToString(","),
                "initialLoaded" to cache.initialLoaded.toString(),
                "hasMoreOlderMessages" to cache.hasMore.toString(),
            ),
        )

        val downloaded = LinkedHashMap<String, Message>()
        if (gaps.isNotEmpty()) {
            gaps.forEach { gap ->
                fetchGapRange(conversationId, gap.afterId, gap.beforeId).forEach { message ->
                    downloaded.getOrPut(message.stableKey) { message }
                }
            }
        }
        if (!cache.initialLoaded || cache.hasMore || gaps.isEmpty()) {
            chatApi.getMessages(conversationId).getOrThrow()
                .messages
                .map { it.toDomain() }
                .forEach { message -> downloaded.getOrPut(message.stableKey) { message } }
        }

        val downloadedList = downloaded.values.toList()
        val duplicatesIgnored = downloadedList.count { it.stableKey in beforeKeys }
        val messagesMerged = downloadedList.size - duplicatesIgnored
        val outboxPending = outboxGateway.value.pendingMessagesFor(conversationId)
        cache.messages.value = mergeMessages(mergeMessages(beforeMessages, downloadedList), outboxPending)
        val pendingAfter = cache.messages.value.count(::isPendingOutboxMessage)

        val acksSent = ackNewInboundMessages(
            conversationId = conversationId,
            messages = downloadedList.filter { message ->
                message.id != null && message.stableKey !in beforeKeys
            },
            trigger = "ChatRepositoryImpl.reconcileConversation:gap:$trigger",
        )

        ReceiverSyncTrace.stage(
            stage = "GAP_REPAIR_COMPLETE",
            conversationId = conversationId,
            lastKnownMessageId = localLatestMessageId,
            latestServerMessageId = serverLatestMessageId,
            trigger = trigger,
            extra = mapOf(
                "messagesDownloaded" to downloadedList.size.toString(),
                "messagesMerged" to messagesMerged.toString(),
                "duplicatesIgnored" to duplicatesIgnored.toString(),
                "acksSent" to acksSent.toString(),
            ),
        )

        return ReconcileResult(
            conversationId = conversationId,
            strategy = ReconcileStrategy.GAP_REPAIR,
            localLatestMessageId = localLatestMessageId,
            serverLatestMessageId = serverLatestMessageId,
            messagesDownloaded = downloadedList.size,
            messagesMerged = messagesMerged,
            duplicatesIgnored = duplicatesIgnored,
            pendingMessagesPreserved = pendingAfter,
            acksSent = acksSent,
        )
    }

    private suspend fun fetchGapRange(
        conversationId: Int,
        afterId: Int,
        beforeId: Int,
    ): List<Message> {
        val collected = mutableListOf<Message>()
        var after = afterId
        while (after < beforeId) {
            val remaining = beforeId - after
            val limit = minOf(remaining, ChatApi.DEFAULT_LIMIT)
            val page = chatApi.getMessages(
                conversationId = conversationId,
                after = after,
                limit = limit,
            ).getOrThrow()
            val messages = page.messages.map { it.toDomain() }
            if (messages.isEmpty()) break
            collected += messages
            val maxId = messages.mapNotNull { it.id }.maxOrNull() ?: break
            if (maxId >= beforeId - 1) break
            if (maxId <= after) break
            after = maxId
        }
        return collected
    }

    private suspend fun ackNewInboundMessages(
        conversationId: Int,
        messages: List<Message>,
        trigger: String,
    ): Int {
        var acksSent = 0
        val sorted = messages.filter { it.id != null }.sortedBy { it.id }
        for (message in sorted) {
            val messageId = message.id ?: continue
            deliveryAckService.onMessageReceived(
                source = "rest_sync",
                conversationId = conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
            deliveryAckService.onMessagePersisted(
                source = "rest_sync",
                conversationId = conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = trigger,
            )
            acksSent += 1
        }
        return acksSent
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
        return runCatching {
            outboxGateway.value.enqueue(conversationId, senderId, content)
        }
    }

    override suspend fun retryOutgoingMessage(clientMessageId: String): Result<Unit> {
        return runCatching {
            outboxGateway.value.retry(clientMessageId)
        }
    }

    override suspend fun deleteOutgoingMessage(clientMessageId: String): Result<Unit> {
        return runCatching {
            outboxGateway.value.delete(clientMessageId)
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
        OutboxPipelineTrace.stage(
            stage = "CHAT_CACHE_UPDATED",
            clientMessageId = message.clientMessageId,
            serverMessageId = message.id,
            conversationId = message.conversationId,
            extra = mapOf("source" to "upsertIncomingMessage"),
        )
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

    override suspend fun upsertOutboxMessage(message: Message) {
        mutex.withLock {
            val cache = cacheFor(message.conversationId)
            cache.messages.value = mergeMessages(listOf(message), cache.messages.value)
        }
    }

    override suspend fun markOutboxSending(clientMessageId: String) {
        updateOutboxMessage(clientMessageId) { message ->
            message.copy(status = MessageStatus.SENDING, isPending = true)
        }
    }

    override suspend fun markOutboxSent(clientMessageId: String, serverMessage: Message) {
        mutex.withLock {
            val cache = cacheFor(serverMessage.conversationId)
            val resolved = serverMessage.copy(
                isPending = false,
                clientMessageId = serverMessage.clientMessageId ?: clientMessageId,
            )
            val remaining = cache.messages.value.filterNot { message ->
                messagesRepresentSameLogicalMessage(message, resolved)
            }
            cache.messages.value = mergeMessages(remaining, listOf(resolved))
        }
        OutboxPipelineTrace.stage(
            stage = "CHAT_CACHE_UPDATED",
            clientMessageId = clientMessageId,
            serverMessageId = serverMessage.id,
            conversationId = serverMessage.conversationId,
            extra = mapOf("source" to "markOutboxSent"),
        )
    }

    override suspend fun markOutboxFailed(clientMessageId: String) {
        updateOutboxMessage(clientMessageId) { message ->
            message.copy(status = MessageStatus.FAILED, isPending = false)
        }
    }

    override suspend fun removeOutboxMessage(clientMessageId: String) {
        mutex.withLock {
            caches.values.forEach { cache ->
                cache.messages.value = cache.messages.value.filterNot { it.clientMessageId == clientMessageId }
            }
        }
    }

    override suspend fun pendingOutboxMessages(conversationId: Int): List<Message> {
        return mutex.withLock {
            cacheFor(conversationId).messages.value.filter { message ->
                message.clientMessageId != null &&
                    (message.isPending || message.status == MessageStatus.FAILED || message.status == MessageStatus.QUEUED || message.status == MessageStatus.SENDING)
            }
        }
    }

    private suspend fun updateOutboxMessage(
        clientMessageId: String,
        transform: (Message) -> Message,
    ) {
        mutex.withLock {
            caches.values.forEach { cache ->
                cache.messages.value = cache.messages.value.map { message ->
                    if (message.clientMessageId == clientMessageId) transform(message) else message
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

    private fun isPendingOutboxMessage(message: Message): Boolean {
        return message.clientMessageId != null &&
            (
                message.isPending ||
                    message.status == MessageStatus.FAILED ||
                    message.status == MessageStatus.QUEUED ||
                    message.status == MessageStatus.SENDING
                )
    }

    private fun cacheFor(conversationId: Int): ConversationCache {
        return caches.getOrPut(conversationId) { ConversationCache() }
    }

    private fun mergeMessages(primary: List<Message>, secondary: List<Message>): List<Message> {
        val merged = LinkedHashMap<String, Message>()
        (primary + secondary).forEach { message ->
            val matchKey = findMatchingKey(merged, message)
            val existing = matchKey?.let { merged[it] }
            val mergedMessage = if (existing == null) {
                message
            } else {
                mergeMessageFields(existing, message)
            }
            if (matchKey != null && matchKey != mergedMessage.stableKey) {
                merged.remove(matchKey)
            }
            merged[mergedMessage.stableKey] = mergedMessage
            merged.entries.removeAll { (key, candidate) ->
                key != mergedMessage.stableKey &&
                    messagesRepresentSameLogicalMessage(candidate, mergedMessage)
            }
        }
        return merged.values.sortedWith(
            compareBy<Message> { it.id ?: Int.MAX_VALUE }
                .thenBy { it.createdAt },
        )
    }

    private fun findMatchingKey(merged: Map<String, Message>, message: Message): String? {
        return merged.entries.firstOrNull { (_, existing) ->
            messagesRepresentSameLogicalMessage(existing, message)
        }?.key
    }

    private fun messagesRepresentSameLogicalMessage(first: Message, second: Message): Boolean {
        if (first.clientMessageId != null && first.clientMessageId == second.clientMessageId) {
            return true
        }
        if (first.id != null && first.id == second.id) {
            return true
        }
        return false
    }

    private fun mergeMessageFields(existing: Message, incoming: Message): Message {
        val base = if (incoming.id != null) incoming else existing
        return base.copy(
            clientMessageId = incoming.clientMessageId ?: existing.clientMessageId,
            status = mergeMessageStatus(existing.status, incoming.status),
            isPending = existing.isPending && incoming.isPending,
        )
    }
}
