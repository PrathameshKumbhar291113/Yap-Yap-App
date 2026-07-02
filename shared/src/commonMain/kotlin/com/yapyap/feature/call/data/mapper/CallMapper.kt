package com.yapyap.feature.call.data.mapper

import com.yapyap.feature.call.data.dto.CallSessionDto
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallType

fun CallSessionDto.toDomain(): CallSession = CallSession(
    id = id,
    conversationId = conversationId,
    callType = CallType.fromRaw(callType),
    caller = CallParticipant(
        userId = callerId,
        displayName = callerName.orEmpty(),
    ),
    callee = CallParticipant(
        userId = calleeId,
        displayName = calleeName.orEmpty(),
    ),
    createdAtIso = createdAt,
)
