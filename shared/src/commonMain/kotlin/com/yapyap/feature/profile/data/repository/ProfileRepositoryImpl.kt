package com.yapyap.feature.profile.data.repository

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.profile.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val authRepository: AuthRepository,
) : ProfileRepository {
    override suspend fun getCurrentUser(): Result<User?> {
        return Result.success(
            when (val state = authRepository.authState.value) {
                is AuthState.Authenticated -> state.user
                else -> null
            },
        )
    }
}
