package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator

class RejectIncomingCallUseCase(
    private val callOrchestrator: CallOrchestrator,
) {
    suspend operator fun invoke(): Result<Unit> = callOrchestrator.rejectIncoming()
}
