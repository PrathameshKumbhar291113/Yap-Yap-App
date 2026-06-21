package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.repository.AuthRepository

class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.logout()
    }
}
