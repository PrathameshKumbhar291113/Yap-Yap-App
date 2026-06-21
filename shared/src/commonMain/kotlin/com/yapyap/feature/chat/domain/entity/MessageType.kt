package com.yapyap.feature.chat.domain.entity

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    ;

    companion object {
        fun fromApi(value: String): MessageType = when (value.lowercase()) {
            "image" -> IMAGE
            "audio" -> AUDIO
            "video" -> VIDEO
            "document" -> DOCUMENT
            else -> TEXT
        }

        fun toApi(value: MessageType): String = when (value) {
            TEXT -> "text"
            IMAGE -> "image"
            AUDIO -> "audio"
            VIDEO -> "video"
            DOCUMENT -> "document"
        }
    }
}

enum class MessageStatus {
    PENDING,
    FAILED,
    SENT,
    DELIVERED,
    READ,
    ;

    companion object {
        fun fromApi(value: String?): MessageStatus = when (value?.lowercase()) {
            "delivered" -> DELIVERED
            "read" -> READ
            "sent" -> SENT
            else -> SENT
        }
    }
}
