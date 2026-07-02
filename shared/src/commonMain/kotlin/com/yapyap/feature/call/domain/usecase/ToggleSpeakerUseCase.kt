package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.repository.CallMediaRepository

class ToggleSpeakerUseCase(
    private val callMediaRepository: CallMediaRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        // TODO(Phase 2): Toggle between earpiece and speaker via SwitchAudioRouteUseCase logic.
        val next = when (callMediaRepository.audioRoute.value) {
            CallAudioRoute.Speaker -> CallAudioRoute.Earpiece
            else -> CallAudioRoute.Speaker
        }
        return callMediaRepository.setAudioRoute(next)
    }
}
