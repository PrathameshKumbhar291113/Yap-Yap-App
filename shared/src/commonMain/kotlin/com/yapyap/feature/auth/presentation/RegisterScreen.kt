package com.yapyap.feature.auth.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapInsets
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.already_have_account
import yapyap.shared.generated.resources.app_name
import yapyap.shared.generated.resources.cd_back
import yapyap.shared.generated.resources.create_account
import yapyap.shared.generated.resources.create_account_subtitle
import yapyap.shared.generated.resources.register

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.setMode(AuthMode.Register)
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.submissionState) {
        if (uiState.submissionState is AuthSubmissionState.Success) {
            onAuthSuccess()
            viewModel.resetSubmissionState()
        }
    }

    Scaffold(
        contentWindowInsets = YapInsets.screenContent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.create_account)) },
                windowInsets = YapInsets.topAppBarStandalone,
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        YapIcon(
                            resource = YapDrawable.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AuthForm(
            title = stringResource(Res.string.app_name),
            subtitle = stringResource(Res.string.create_account_subtitle),
            mode = AuthMode.Register,
            uiState = uiState,
            submitLabel = stringResource(Res.string.register),
            onNameChange = viewModel::onNameChange,
            onEmailChange = viewModel::onEmailChange,
            onLoginIdentifierChange = viewModel::onLoginIdentifierChange,
            onPasswordChange = viewModel::onPasswordChange,
            onPhoneChange = viewModel::onPhoneChange,
            onSubmit = viewModel::register,
            alternateActionLabel = stringResource(Res.string.already_have_account),
            onAlternateAction = onNavigateToLogin,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
