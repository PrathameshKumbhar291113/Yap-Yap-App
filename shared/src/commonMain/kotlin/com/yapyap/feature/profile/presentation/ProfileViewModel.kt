package com.yapyap.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.LogoutUseCase
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.auth.domain.usecase.UpdateProfileUseCase
import com.yapyap.core.util.UserMessages
import com.yapyap.core.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val pushNotificationCoordinator: PushNotificationCoordinator,
) : ViewModel() {
    private val nameOverride = MutableStateFlow<String?>(null)
    private val isSaving = MutableStateFlow(false)
    private val saveError = MutableStateFlow<String?>(null)
    private val saveSuccess = MutableStateFlow(false)
    private val isLoggingOut = MutableStateFlow(false)

    val uiState: StateFlow<ProfileUiState> = combine(
        observeAuthStateUseCase(),
        nameOverride,
        isSaving,
        saveError,
        saveSuccess,
        isLoggingOut,
    ) { values ->
        val authState = values[0] as AuthState
        val name = values[1] as String?
        val saving = values[2] as Boolean
        val error = values[3] as String?
        val success = values[4] as Boolean
        val loggingOut = values[5] as Boolean

        when (authState) {
            AuthState.Loading -> ProfileUiState.Loading
            AuthState.Unauthenticated -> ProfileUiState.Error("Not signed in")
            is AuthState.Authenticated -> {
                val (defaultName, _) = authState.user.toProfileFields()
                ProfileUiState.Content(
                    user = authState.user,
                    name = name ?: defaultName,
                    isSaving = saving,
                    saveError = error,
                    saveSuccess = success,
                    isLoggingOut = loggingOut,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ProfileUiState.Loading,
    )

    fun onNameChange(value: String) {
        nameOverride.value = value
        saveError.value = null
        saveSuccess.value = false
    }

    fun saveProfile() {
        val content = uiState.value as? ProfileUiState.Content ?: return
        viewModelScope.launch {
            isSaving.value = true
            saveError.value = null
            saveSuccess.value = false
            updateProfileUseCase(content.toProfileUpdate())
                .onSuccess {
                    nameOverride.value = null
                    saveSuccess.value = true
                }
                .onFailure { updateError ->
                    saveError.value = updateError.toUserMessage(UserMessages.UPDATE_PROFILE_FAILED)
                }
            isSaving.value = false
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            isLoggingOut.value = true
            chatRealtimeClient.disconnect()
            pushNotificationCoordinator.unregisterDevice()
            logoutUseCase()
            isLoggingOut.value = false
            onLoggedOut()
        }
    }
}
