package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.repository.AuthRepository

class RegisterUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        name: String,
        phone: String,
    ): Result<User> {
        return authRepository.register(email, password, name, phone)
    }
}
