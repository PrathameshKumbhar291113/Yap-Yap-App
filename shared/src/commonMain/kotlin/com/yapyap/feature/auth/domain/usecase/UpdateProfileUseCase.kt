package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import com.yapyap.feature.auth.domain.repository.AuthRepository

class UpdateProfileUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(update: ProfileUpdate): Result<User> {
        return authRepository.updateProfile(update)
    }
}
