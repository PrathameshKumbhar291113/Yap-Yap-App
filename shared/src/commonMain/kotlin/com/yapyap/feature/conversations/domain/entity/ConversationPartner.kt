package com.yapyap.feature.conversations.domain.entity

data class ConversationPartner(
    val id: Int,
    val name: String?,
    val email: String?,
    val phone: String?,
)

fun ConversationPartner.displayName(): String = name?.takeIf { it.isNotBlank() }
    ?: email?.takeIf { it.isNotBlank() }
    ?: phone?.takeIf { it.isNotBlank() }
    ?: "Unknown user"
