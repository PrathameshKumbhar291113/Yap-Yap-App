package com.yapyap.feature.auth.data.repository

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.network.appJson
import com.yapyap.feature.auth.data.dto.LoginRequestDto
import com.yapyap.feature.auth.data.dto.RefreshTokenRequestDto
import com.yapyap.feature.auth.data.dto.RegisterRequestDto
import com.yapyap.feature.auth.data.dto.UserDto
import com.yapyap.feature.auth.data.mapper.toDomain
import com.yapyap.feature.auth.data.mapper.toDto
import com.yapyap.feature.auth.data.remote.AuthApi
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.core.network.getOrThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    override suspend fun login(identifier: String, password: String): Result<User> {
        return runCatching {
            val trimmed = identifier.trim()
            val request = if ('@' in trimmed) {
                LoginRequestDto(email = trimmed.lowercase(), password = password)
            } else {
                LoginRequestDto(phone = trimmed, password = password)
            }
            val tokens = authApi.login(request).getOrThrow()
            handleAuthSuccess(tokens.accessToken, tokens.refreshToken, tokens.user.toDomain())
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        name: String,
        phone: String,
    ): Result<User> {
        return runCatching {
            val tokens = authApi.register(
                RegisterRequestDto(
                    email = email.trim().lowercase(),
                    password = password,
                    name = name.trim(),
                    phone = phone.trim(),
                ),
            ).getOrThrow()
            handleAuthSuccess(tokens.accessToken, tokens.refreshToken, tokens.user.toDomain())
        }
    }

    override suspend fun refreshToken(): Result<Unit> {
        return runCatching {
            val refreshToken = tokenStorage.getRefreshToken()
                ?: throw IllegalStateException("No refresh token available")
            val tokens = authApi.refreshToken(RefreshTokenRequestDto(refreshToken)).getOrThrow()
            tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return runCatching {
            runCatching { authApi.logout().getOrThrow() }
            clearSession()
        }
    }

    override suspend fun updateProfile(update: ProfileUpdate): Result<User> {
        return runCatching {
            val user = authApi.updateProfile(update.toDto()).getOrThrow().toDomain()
            tokenStorage.saveCachedUser(appJson.encodeToString(UserDto.serializer(), user.toDto()))
            _authState.value = AuthState.Authenticated(user)
            user
        }
    }

    private fun handleAuthSuccess(accessToken: String, refreshToken: String, user: User): User {
        persistSession(accessToken, refreshToken, user)
        _authState.value = AuthState.Authenticated(user)
        return user
    }

    private fun persistSession(accessToken: String, refreshToken: String, user: User) {
        tokenStorage.saveTokens(accessToken, refreshToken)
        tokenStorage.saveCachedUser(appJson.encodeToString(UserDto.serializer(), user.toDto()))
    }

    private fun clearSession() {
        tokenStorage.clear()
        _authState.value = AuthState.Unauthenticated
    }

    private fun restoreSession() {
        val cachedUserJson = tokenStorage.getCachedUser()
        val hasAccessToken = !tokenStorage.getAccessToken().isNullOrBlank()
        val hasRefreshToken = !tokenStorage.getRefreshToken().isNullOrBlank()
        _authState.value = if (hasAccessToken && cachedUserJson != null) {
            runCatching {
                AuthState.Authenticated(
                    appJson.decodeFromString(UserDto.serializer(), cachedUserJson).toDomain(),
                )
            }.getOrElse {
                clearSession()
                AuthState.Unauthenticated
            }
        } else {
            if (hasAccessToken || hasRefreshToken) {
                clearSession()
            }
            AuthState.Unauthenticated
        }
    }
}
