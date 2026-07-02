package com.yapyap.feature.call.domain.orchestrator

import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallType

interface CallOrchestrator {
    suspend fun startOutgoing(
        conversationId: Int,
        callType: CallType,
        remoteUser: CallParticipant,
    ): Result<Unit>

    suspend fun redialFromHistory(
        callHistoryId: Int,
        remoteUser: CallParticipant,
    ): Result<Unit>

    suspend fun answerIncoming(): Result<Unit>

    suspend fun rejectIncoming(): Result<Unit>

    suspend fun endActiveCall(): Result<Unit>

    fun dismissOverlay()
}
