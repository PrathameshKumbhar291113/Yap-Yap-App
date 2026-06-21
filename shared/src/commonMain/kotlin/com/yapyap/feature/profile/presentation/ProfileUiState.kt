package com.yapyap.feature.profile.presentation

import com.yapyap.feature.auth.domain.entity.User
import com.yapyap.feature.auth.domain.model.ProfileUpdate

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Content(
        val user: User,
        val name: String,
        val phone: String,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val saveSuccess: Boolean = false,
        val isLoggingOut: Boolean = false,
    ) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}

internal fun User.toProfileFields(): Pair<String, String> {
    return (name.orEmpty()) to (phone.orEmpty())
}

internal fun ProfileUiState.Content.toProfileUpdate(): ProfileUpdate {
    return ProfileUpdate(
        name = name.trim().takeIf { it.isNotEmpty() },
        phone = phone.trim().takeIf { it.isNotEmpty() },
    )
}
