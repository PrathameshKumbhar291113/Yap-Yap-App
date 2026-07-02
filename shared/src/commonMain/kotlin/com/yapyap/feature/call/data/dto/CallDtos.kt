package com.yapyap.feature.call.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateCallRequestDto(
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("call_type") val callType: String,
    @SerialName("callee_user_id") val calleeUserId: Int,
)

@Serializable
data class CallSessionDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: Int? = null,
    @SerialName("call_type") val callType: String,
    @SerialName("caller_id") val callerId: Int,
    @SerialName("caller_name") val callerName: String? = null,
    @SerialName("callee_id") val calleeId: Int,
    @SerialName("callee_name") val calleeName: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("connected_at") val connectedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("provider_room") val providerRoom: String? = null,
)

@Serializable
data class CallReconciliationDto(
    val action: String,
    @SerialName("forceEndLocal") val forceEndLocal: Boolean = false,
    @SerialName("canJoinMedia") val canJoinMedia: Boolean = false,
    val reason: String? = null,
    val role: String? = null,
    @SerialName("isTerminal") val isTerminal: Boolean = false,
)

@Serializable
data class CallSessionResponseDto(
    val session: CallSessionDto,
    val reconciliation: CallReconciliationDto? = null,
    val providerToken: String? = null,
    val livekitUrl: String? = null,
    val roomName: String? = null,
    val tokenExpiresAt: String? = null,
)
