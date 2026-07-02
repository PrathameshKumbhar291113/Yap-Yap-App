package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.repository.CallMediaRepository

class ToggleMuteUseCase(
    private val callMediaRepository: CallMediaRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        // TODO(Phase 2): Flip mute state via CallMediaRepository once WebRTC is integrated.
        val next = !callMediaRepository.isMuted.value
        return callMediaRepository.setMuted(next)
    }
}
