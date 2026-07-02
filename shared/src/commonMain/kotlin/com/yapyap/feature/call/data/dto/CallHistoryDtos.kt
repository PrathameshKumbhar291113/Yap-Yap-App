package com.yapyap.feature.call.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CallHistoryParticipantDto(
    val id: Int,
    val name: String? = null,
)

@Serializable
data class CallHistoryEntryDto(
    val id: String,
    val callSessionId: String? = null,
    val conversationId: Int? = null,
    val latestMessageId: String? = null,
    val participant: CallHistoryParticipantDto? = null,
    val callType: String? = null,
    val direction: String,
    val status: String,
    val answered: Boolean = false,
    val durationSec: Int? = null,
    val endReason: String? = null,
    val roomName: String? = null,
    val startedAt: String? = null,
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class CallHistoryPageDto(
    val items: List<CallHistoryEntryDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class CallHistoryEntryResponseDto(
    val entry: CallHistoryEntryDto,
)

@Serializable
data class RedialCallRequestDto(
    val call_history_id: Int,
)
