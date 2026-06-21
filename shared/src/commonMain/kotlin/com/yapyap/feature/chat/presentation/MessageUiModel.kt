package com.yapyap.feature.chat.presentation

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.core.util.formatDateLabel
import com.yapyap.core.util.formatMessageTime
import com.yapyap.core.util.isoToLocalDateKey
import com.yapyap.feature.chat.domain.entity.MessageType

enum class ReadReceiptVisual {
    Pending,
    Sent,
    Delivered,
    Read,
    Failed,
}

sealed interface ChatListItem {
    val key: String

    data class DateSeparator(
        val label: String,
        override val key: String,
    ) : ChatListItem

    data class MessageItem(
        val message: MessageUiModel,
        override val key: String = message.stableKey,
    ) : ChatListItem
}

data class MessageUiModel(
    val stableKey: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestamp: String?,
    val dateKey: String,
    val readReceipt: ReadReceiptVisual?,
    val isPending: Boolean,
    val isFailed: Boolean,
)

fun Message.toUiModel(currentUserId: Int): MessageUiModel = MessageUiModel(
    stableKey = stableKey,
    content = displayText(),
    isOutgoing = senderId == currentUserId,
    timestamp = formatMessageTime(createdAt),
    dateKey = isoToLocalDateKey(createdAt),
    readReceipt = if (senderId == currentUserId) status.toReadReceipt() else null,
    isPending = isPending,
    isFailed = status == MessageStatus.FAILED,
)

fun List<MessageUiModel>.toChatListItems(
    todayKey: String,
    yesterdayKey: String,
    todayLabel: String,
    yesterdayLabel: String,
): List<ChatListItem> {
    if (isEmpty()) return emptyList()
    val items = mutableListOf<ChatListItem>()
    var lastDateKey: String? = null
    forEach { message ->
        if (message.dateKey != lastDateKey) {
            items += ChatListItem.DateSeparator(
                label = formatDateLabel(
                    dateKey = message.dateKey,
                    todayKey = todayKey,
                    yesterdayKey = yesterdayKey,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel,
                ),
                key = "date_${message.dateKey}",
            )
            lastDateKey = message.dateKey
        }
        items += ChatListItem.MessageItem(message = message)
    }
    return items
}

private fun Message.displayText(): String = when {
    !content.isNullOrBlank() -> content
    messageType != MessageType.TEXT -> messageType.name.lowercase().let { name ->
        if (name.isEmpty()) "" else name.replaceFirstChar { it.uppercaseChar() }
    }
    else -> ""
}

private fun MessageStatus.toReadReceipt(): ReadReceiptVisual = when (this) {
    MessageStatus.PENDING -> ReadReceiptVisual.Pending
    MessageStatus.FAILED -> ReadReceiptVisual.Failed
    MessageStatus.SENT -> ReadReceiptVisual.Sent
    MessageStatus.DELIVERED -> ReadReceiptVisual.Delivered
    MessageStatus.READ -> ReadReceiptVisual.Read
}

