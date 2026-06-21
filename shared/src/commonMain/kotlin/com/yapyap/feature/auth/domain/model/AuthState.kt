package com.yapyap.feature.auth.domain.model

import com.yapyap.feature.auth.domain.entity.User

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
}
