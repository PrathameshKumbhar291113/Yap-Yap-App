package com.yapyap.feature.conversations.domain.entity

data class LastMessage(
    val id: Int,
    val content: String?,
    val messageType: String,
    val senderId: Int,
    val createdAt: String,
)
