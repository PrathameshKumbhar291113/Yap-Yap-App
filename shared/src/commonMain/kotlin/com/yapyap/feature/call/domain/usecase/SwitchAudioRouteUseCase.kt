package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.repository.CallMediaRepository

class SwitchAudioRouteUseCase(
    private val callMediaRepository: CallMediaRepository,
) {
    suspend operator fun invoke(route: CallAudioRoute): Result<Unit> {
        // TODO(Phase 2): Route audio via platform media controller.
        return callMediaRepository.setAudioRoute(route)
    }
}
