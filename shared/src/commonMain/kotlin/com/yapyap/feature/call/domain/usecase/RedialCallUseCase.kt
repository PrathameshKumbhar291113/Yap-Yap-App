package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator

class RedialCallUseCase(
    private val callOrchestrator: CallOrchestrator,
) {
    suspend operator fun invoke(
        callHistoryId: Int,
        remoteUser: CallParticipant,
    ): Result<Unit> = callOrchestrator.redialFromHistory(
        callHistoryId = callHistoryId,
        remoteUser = remoteUser,
    )
}
