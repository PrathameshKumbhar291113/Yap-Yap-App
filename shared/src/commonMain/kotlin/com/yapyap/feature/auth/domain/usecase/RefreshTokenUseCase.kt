package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.repository.AuthRepository

class RefreshTokenUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.refreshToken()
    }
}
