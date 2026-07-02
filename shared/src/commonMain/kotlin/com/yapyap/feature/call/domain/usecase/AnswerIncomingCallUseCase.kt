package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator

class AnswerIncomingCallUseCase(
    private val callOrchestrator: CallOrchestrator,
) {
    suspend operator fun invoke(): Result<Unit> = callOrchestrator.answerIncoming()
}
