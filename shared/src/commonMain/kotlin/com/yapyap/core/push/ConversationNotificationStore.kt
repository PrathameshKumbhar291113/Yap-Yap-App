package com.yapyap.core.push

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.model.ConversationNotificationMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class ConversationNotificationState(
    val conversationId: String,
    val senderName: String,
    val messages: List<ConversationNotificationMessage>,
)

/**
 * In-memory per-conversation notification history for grouped tray UI.
 * One conversation → one notification entry (updated via stable tag/id).
 */
class ConversationNotificationStore {
    private val mutex = Mutex()
    private val messagesByConversation = linkedMapOf<String, MutableList<ConversationNotificationMessage>>()
    private val senderNameByConversation = mutableMapOf<String, String>()

    suspend fun appendMessage(
        conversationId: String,
        messageId: String?,
        senderName: String,
        text: String,
    ): ConversationNotificationState? = mutex.withLock {
        val resolvedMessageId = messageId?.takeIf { it.isNotBlank() } ?: return@withLock null
        if (messagesByConversation.values.any { list -> list.any { it.messageId == resolvedMessageId } }) {
            RuntimeDiagnostics.event(
                "Notification",
                "NOTIFICATION_DEDUP conversationId=$conversationId messageId=$resolvedMessageId",
            )
            return@withLock stateLocked(conversationId)
        }
        val list = messagesByConversation.getOrPut(conversationId) { mutableListOf() }
        list.add(
            ConversationNotificationMessage(
                messageId = resolvedMessageId,
                senderName = senderName,
                text = text,
                timestamp = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        while (list.size > MAX_MESSAGES) {
            list.removeAt(0)
        }
        senderNameByConversation[conversationId] = senderName
        if (list.size == 1) {
            RuntimeDiagnostics.event(
                "Notification",
                "NOTIFICATION_CREATED conversationId=$conversationId messageId=$resolvedMessageId",
            )
        }
        RuntimeDiagnostics.event(
            "Notification",
            "NOTIFICATION_STORE_APPEND conversationId=$conversationId messageId=$resolvedMessageId count=${list.size}",
        )
        stateLocked(conversationId)
    }

    suspend fun clearConversation(conversationId: String) = mutex.withLock {
        messagesByConversation.remove(conversationId)
        senderNameByConversation.remove(conversationId)
    }

    suspend fun activeConversationCount(): Int = mutex.withLock {
        messagesByConversation.count { it.value.isNotEmpty() }
    }

    suspend fun getState(conversationId: String): ConversationNotificationState? = mutex.withLock {
        stateLocked(conversationId)
    }

    private fun stateLocked(conversationId: String): ConversationNotificationState? {
        val messages = messagesByConversation[conversationId]?.toList().orEmpty()
        if (messages.isEmpty()) return null
        return ConversationNotificationState(
            conversationId = conversationId,
            senderName = senderNameByConversation[conversationId] ?: messages.last().senderName,
            messages = messages,
        )
    }

    private companion object {
        const val MAX_MESSAGES = 25
    }
}
