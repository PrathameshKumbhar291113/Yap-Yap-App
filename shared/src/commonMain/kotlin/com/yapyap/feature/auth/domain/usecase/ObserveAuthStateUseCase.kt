package com.yapyap.feature.auth.domain.usecase

import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveAuthStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): StateFlow<AuthState> = authRepository.authState
}
