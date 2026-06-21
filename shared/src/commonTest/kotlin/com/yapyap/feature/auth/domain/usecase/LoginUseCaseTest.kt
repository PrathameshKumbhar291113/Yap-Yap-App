package com.yapyap.feature.auth.domain.usecase

import com.yapyap.core.network.ApiException
import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import com.yapyap.feature.auth.domain.repository.AuthRepository
import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginUseCaseTest {

    @Test
    fun returnsAuthenticatedUserOnSuccess() = runTest {
        val fakeRepository = FakeAuthRepository(LoginBehavior.Success)
        val useCase = LoginUseCase(fakeRepository)

        val result = useCase("alice@test.com", "password123")

        assertTrue(result.isSuccess)
        assertEquals("alice@test.com", fakeRepository.lastLoginIdentifier)
        assertEquals("password123", fakeRepository.lastLoginPassword)
        assertEquals("alice@test.com", result.getOrNull()?.email)
        assertEquals(1, result.getOrNull()?.id)
    }

    @Test
    fun propagatesInvalidCredentialsFailure() = runTest {
        val fakeRepository = FakeAuthRepository(LoginBehavior.InvalidCredentials)
        val useCase = LoginUseCase(fakeRepository)

        val result = useCase("alice@test.com", "wrong-password")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<ApiException.AuthInvalid>(error)
        assertEquals(ApiException.ERROR_AUTH_INVALID, error.errorCode)
    }

    @Test
    fun propagatesNetworkFailure() = runTest {
        val fakeRepository = FakeAuthRepository(LoginBehavior.NetworkError)
        val useCase = LoginUseCase(fakeRepository)

        val result = useCase("alice@test.com", "password123")

        assertTrue(result.isFailure)
        assertIs<ConnectTimeoutException>(result.exceptionOrNull())
    }

    private enum class LoginBehavior {
        Success,
        InvalidCredentials,
        NetworkError,
    }

    private class FakeAuthRepository(
        private val behavior: LoginBehavior,
    ) : AuthRepository {
        var lastLoginIdentifier: String? = null
        var lastLoginPassword: String? = null

        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState

        override suspend fun login(identifier: String, password: String): Result<User> {
            lastLoginIdentifier = identifier
            lastLoginPassword = password
            return when (behavior) {
                LoginBehavior.Success -> {
                    val user = User(
                        id = 1,
                        uid = "user_test",
                        email = identifier,
                        name = "Alice",
                    )
                    _authState.value = AuthState.Authenticated(user)
                    Result.success(user)
                }
                LoginBehavior.InvalidCredentials -> Result.failure(
                    ApiException.AuthInvalid(
                        message = "Invalid email or password",
                        httpStatus = 401,
                        errorCode = ApiException.ERROR_AUTH_INVALID,
                    ),
                )
                LoginBehavior.NetworkError -> Result.failure(
                    ConnectTimeoutException("Connection timed out"),
                )
            }
        }

        override suspend fun register(
            email: String,
            password: String,
            name: String,
            phone: String,
        ): Result<User> = Result.failure(UnsupportedOperationException())

        override suspend fun refreshToken(): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun logout(): Result<Unit> = Result.success(Unit)

        override suspend fun updateProfile(update: ProfileUpdate): Result<User> =
            Result.failure(UnsupportedOperationException())
    }
}
