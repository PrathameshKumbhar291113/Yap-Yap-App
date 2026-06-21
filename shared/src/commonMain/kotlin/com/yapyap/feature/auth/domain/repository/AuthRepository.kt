package com.yapyap.feature.auth.domain.repository

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun login(identifier: String, password: String): Result<User>
    suspend fun register(
        email: String,
        password: String,
        name: String,
        phone: String,
    ): Result<User>
    suspend fun refreshToken(): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun updateProfile(update: ProfileUpdate): Result<User>
}
