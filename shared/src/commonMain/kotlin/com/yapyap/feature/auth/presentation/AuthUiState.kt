package com.yapyap.feature.auth.presentation

enum class AuthMode {
    Login,
    Register,
}

sealed interface AuthSubmissionState {
    data object Idle : AuthSubmissionState
    data object Loading : AuthSubmissionState
    data object Success : AuthSubmissionState
    data class Error(val message: String?) : AuthSubmissionState
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val loginIdentifier: String = "",
    val nameError: AuthFieldError? = null,
    val emailError: AuthFieldError? = null,
    val phoneError: AuthFieldError? = null,
    val loginIdentifierError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null,
    val submissionState: AuthSubmissionState = AuthSubmissionState.Idle,
) {
    val isLoading: Boolean get() = submissionState is AuthSubmissionState.Loading

    val errorMessage: String?
        get() = (submissionState as? AuthSubmissionState.Error)?.message
}
