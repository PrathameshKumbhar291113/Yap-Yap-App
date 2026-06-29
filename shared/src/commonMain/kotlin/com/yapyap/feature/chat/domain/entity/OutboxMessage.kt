package com.yapyap.feature.chat.domain.entity

enum class OutboxStatus {
    QUEUED,
    SENDING,
    FAILED,
    ;

    fun toStorageValue(): String = name

    companion object {
        fun fromStorage(value: String): OutboxStatus = when (value.uppercase()) {
            "SENDING" -> SENDING
            "FAILED" -> FAILED
            else -> QUEUED
        }
    }
}

data class OutboxMessage(
    val clientMessageId: String,
    val conversationId: Int,
    val senderId: Int,
    val content: String,
    val messageType: MessageType,
    val status: OutboxStatus,
    val createdAt: String,
    val retryCount: Int,
    val nextRetryAtMs: Long,
    val enqueueOrder: Long,
)
