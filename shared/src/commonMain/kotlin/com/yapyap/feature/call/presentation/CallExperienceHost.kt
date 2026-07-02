package com.yapyap.feature.call.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.yapyap.core.di.koinViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CallExperienceHost(
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = koinViewModel(),
    permissionPromptController: CallPermissionPromptController = koinInject(),
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingPermissionStatus by permissionPromptController.pendingStatus.collectAsState()
    val pendingPermissionCallType by permissionPromptController.pendingCallType.collectAsState()
    val scope = rememberCoroutineScope()

    ProvideOngoingCallChrome(
        chrome = OngoingCallChrome(
            showGreenAppBar = uiState.showsOngoingCallBanner,
            callType = uiState.callType,
            onReturnToCall = viewModel::expandCallScreen,
        ),
    ) {
        content()
    }

    CallOverlayHost(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        viewModel = viewModel,
    )

    pendingPermissionStatus?.let { status ->
        pendingPermissionCallType?.let { callType ->
            CallPermissionDialog(
                callType = callType,
                status = status,
                onDismiss = permissionPromptController::dismiss,
                onGrant = {
                    scope.launch {
                        permissionPromptController.retryPermissions(callType)
                    }
                },
                onOpenSettings = permissionPromptController::openSettings,
            )
        }
    }
}
