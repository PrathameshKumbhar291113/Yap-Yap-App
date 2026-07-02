package com.yapyap.feature.call.data.mapper

import com.yapyap.feature.call.data.dto.CallHistoryEntryDto
import com.yapyap.feature.call.data.dto.CallReconciliationDto
import com.yapyap.feature.call.data.dto.CallSessionDto
import com.yapyap.feature.call.data.dto.CallSessionResponseDto
import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.entity.CallLogStatus
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallReconcileAction
import com.yapyap.feature.call.domain.entity.CallReconciliation
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.entity.CallServerStatus
import com.yapyap.feature.call.domain.entity.CallType

fun CallSessionResponseDto.toRoomCredentials(): CallRoomCredentials? {
    val token = providerToken?.trim().orEmpty()
    val url = livekitUrl?.trim().orEmpty()
    val room = roomName?.trim().orEmpty()
        .ifBlank { session.providerRoom.orEmpty() }
        .ifBlank { session.id }
    val credentials = CallRoomCredentials(
        livekitUrl = url,
        providerToken = token,
        roomName = room,
    )
    return credentials.takeIf { it.isValid() }
}

fun CallSessionResponseDto.toReconcileSnapshot(): ActiveCallSnapshot = ActiveCallSnapshot(
    session = session.toDomain(),
    status = CallServerStatus.fromRaw(session.status),
    durationMs = session.durationSec?.times(1_000L),
    reconciliation = reconciliation?.toDomain(),
    credentials = toRoomCredentials(),
    tokenExpiresAt = tokenExpiresAt,
)

fun CallReconciliationDto.toDomain(): CallReconciliation = CallReconciliation(
    action = CallReconcileAction.fromRaw(action),
    forceEndLocal = forceEndLocal,
    canJoinMedia = canJoinMedia,
    reason = reason,
)

fun CallHistoryEntryDto.toDomain(): CallLogEntry = CallLogEntry(
    id = id,
    conversationId = conversationId,
    latestMessageId = latestMessageId,
    callType = CallType.fromRaw(callType),
    direction = direction.toCallDirection(),
    status = status.toCallLogStatus(),
    answered = answered,
    remoteUser = CallParticipant(
        userId = participant?.id ?: 0,
        displayName = participant?.name.orEmpty(),
    ),
    startedAtIso = startedAt ?: timestamp,
    durationSec = durationSec,
    endReason = endReason,
    roomName = roomName,
)

fun CallEndReason.toLogStatus(): CallLogStatus = when (this) {
    CallEndReason.Missed -> CallLogStatus.Missed
    CallEndReason.Rejected -> CallLogStatus.Rejected
    CallEndReason.Failed -> CallLogStatus.Failed
    CallEndReason.Cancelled -> CallLogStatus.Cancelled
    else -> CallLogStatus.Completed
}

private fun String.toCallDirection(): CallDirection = when (lowercase()) {
    "incoming" -> CallDirection.Incoming
    else -> CallDirection.Outgoing
}

private fun String.toCallLogStatus(): CallLogStatus = when (lowercase()) {
    "missed" -> CallLogStatus.Missed
    "rejected" -> CallLogStatus.Rejected
    "failed" -> CallLogStatus.Failed
    "cancelled", "canceled" -> CallLogStatus.Cancelled
    else -> CallLogStatus.Completed
}
