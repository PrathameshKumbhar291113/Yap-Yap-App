package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator

class StartOutgoingCallUseCase(
    private val callOrchestrator: CallOrchestrator,
) {
    suspend operator fun invoke(
        conversationId: Int,
        callType: CallType,
        remoteUser: CallParticipant,
    ): Result<Unit> {
        return callOrchestrator.startOutgoing(
            conversationId = conversationId,
            callType = callType,
            remoteUser = remoteUser,
        )
    }
}
