package com.yapyap.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.core.network.HealthApi
import com.yapyap.feature.auth.domain.usecase.LoginUseCase
import com.yapyap.feature.auth.domain.usecase.LogoutUseCase
import com.yapyap.feature.auth.domain.usecase.RegisterUseCase
import com.yapyap.core.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val healthApi: HealthApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null, submissionState = AuthSubmissionState.Idle) }
    }

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                submissionState = AuthSubmissionState.Idle,
            )
        }
    }

    fun onLoginIdentifierChange(identifier: String) {
        _uiState.update {
            it.copy(
                loginIdentifier = identifier,
                loginIdentifierError = null,
                submissionState = AuthSubmissionState.Idle,
            )
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                submissionState = AuthSubmissionState.Idle,
            )
        }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phone = phone, phoneError = null, submissionState = AuthSubmissionState.Idle) }
    }

    fun setMode(mode: AuthMode) {
        _uiState.update { AuthUiState(mode = mode) }
    }

    fun resetSubmissionState() {
        _uiState.update { it.copy(submissionState = AuthSubmissionState.Idle) }
    }

    fun login() {
        submit(isRegister = false)
    }

    fun register() {
        submit(isRegister = true)
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            logoutUseCase()
            onComplete()
        }
    }

    suspend fun checkBackendHealth(): Result<String> = healthApi.checkRoot()

    private fun submit(isRegister: Boolean) {
        val state = _uiState.value
        val passwordError = AuthValidator.validatePassword(state.password)

        if (isRegister) {
            val nameError = AuthValidator.validateName(state.name)
            val emailError = AuthValidator.validateEmail(state.email)
            val phoneError = AuthValidator.validatePhone(state.phone)
            if (nameError != null || emailError != null || phoneError != null || passwordError != null) {
                _uiState.update {
                    it.copy(
                        nameError = nameError,
                        emailError = emailError,
                        phoneError = phoneError,
                        passwordError = passwordError,
                        submissionState = AuthSubmissionState.Idle,
                    )
                }
                return
            }
        } else {
            val loginIdentifierError = AuthValidator.validateLoginIdentifier(state.loginIdentifier)
            if (loginIdentifierError != null || passwordError != null) {
                _uiState.update {
                    it.copy(
                        loginIdentifierError = loginIdentifierError,
                        passwordError = passwordError,
                        submissionState = AuthSubmissionState.Idle,
                    )
                }
                return
            }
        }

        if (state.submissionState is AuthSubmissionState.Loading) return

        viewModelScope.launch {
            _uiState.update { it.copy(submissionState = AuthSubmissionState.Loading) }
            val result = if (isRegister) {
                registerUseCase(
                    email = state.email.trim(),
                    password = state.password,
                    name = state.name.trim(),
                    phone = state.phone.trim(),
                )
            } else {
                loginUseCase(state.loginIdentifier.trim(), state.password)
            }

            result
                .onSuccess {
                    _uiState.update { current -> current.copy(submissionState = AuthSubmissionState.Success) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            submissionState = AuthSubmissionState.Error(
                                message = error.toUserMessage(),
                            ),
                        )
                    }
                }
        }
    }
}
