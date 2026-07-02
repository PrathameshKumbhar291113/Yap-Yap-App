package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.repository.CallRepository

class RefreshCallTokenUseCase(
    private val callRepository: CallRepository,
) {
    suspend operator fun invoke(callSessionId: String) =
        callRepository.refreshCallToken(callSessionId)
}
