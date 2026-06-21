package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(identifier: String, password: String): Result<User> {
        return authRepository.login(identifier, password)
    }
}
