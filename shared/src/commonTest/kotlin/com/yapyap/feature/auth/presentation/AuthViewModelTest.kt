package com.yapyap.feature.auth.presentation

import com.yapyap.core.network.HealthApi
import com.yapyap.core.network.appJson
import com.yapyap.feature.auth.domain.entity.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.model.ProfileUpdate
import com.yapyap.feature.auth.domain.repository.AuthRepository
import com.yapyap.feature.auth.domain.usecase.LoginUseCase
import com.yapyap.feature.auth.domain.usecase.LogoutUseCase
import com.yapyap.feature.auth.domain.usecase.RegisterUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginTransitionsFromIdleToLoadingToSuccess() = runTest(testDispatcher) {
        val repository = DelayedSuccessAuthRepository()
        val viewModel = AuthViewModel(
            loginUseCase = LoginUseCase(repository),
            registerUseCase = RegisterUseCase(repository),
            logoutUseCase = LogoutUseCase(repository),
            healthApi = healthApiForTest(),
        )

        viewModel.onLoginIdentifierChange("alice@test.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        runCurrent()

        assertEquals(AuthSubmissionState.Loading, viewModel.uiState.value.submissionState)

        repository.releaseLogin()
        advanceUntilIdle()

        assertEquals(AuthSubmissionState.Success, viewModel.uiState.value.submissionState)
        assertTrue(viewModel.uiState.value.errorMessage == null)
    }

    private class DelayedSuccessAuthRepository : AuthRepository {
        private val loginGate = CompletableDeferred<Unit>()
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState

        suspend fun releaseLogin() {
            loginGate.complete(Unit)
        }

        override suspend fun login(identifier: String, password: String): Result<User> {
            loginGate.await()
            val user = User(
                id = 1,
                uid = "user_test",
                email = identifier,
                name = "Alice",
            )
            _authState.value = AuthState.Authenticated(user)
            return Result.success(user)
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

    private fun healthApiForTest(): HealthApi {
        val engine = MockEngine {
            respond(
                content = """{"success":true,"message":"OK","data":{"service":"test"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HealthApi(
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(appJson)
                }
            },
        )
    }
}
