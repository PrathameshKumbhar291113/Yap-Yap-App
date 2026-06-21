package com.yapyap.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.network.Environment
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.YapFormContainer
import com.yapyap.core.ui.YapScreenHorizontalPadding
import com.yapyap.core.util.toUserMessage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.app_name
import yapyap.shared.generated.resources.auth_failed
import yapyap.shared.generated.resources.create_an_account
import yapyap.shared.generated.resources.email
import yapyap.shared.generated.resources.email_or_phone
import yapyap.shared.generated.resources.health_check
import yapyap.shared.generated.resources.hide_password
import yapyap.shared.generated.resources.login_identifier_hint
import yapyap.shared.generated.resources.name
import yapyap.shared.generated.resources.password
import yapyap.shared.generated.resources.phone
import yapyap.shared.generated.resources.show_password
import yapyap.shared.generated.resources.sign_in
import yapyap.shared.generated.resources.sign_in_subtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onAuthSuccess: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: AuthViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.setMode(AuthMode.Login)
    }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.submissionState) {
        if (uiState.submissionState is AuthSubmissionState.Success) {
            onAuthSuccess()
            viewModel.resetSubmissionState()
        }
    }

    Scaffold(
        contentWindowInsets = YapInsets.content,
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.sign_in)) })
        },
    ) { innerPadding ->
        AuthForm(
            title = stringResource(Res.string.app_name),
            subtitle = stringResource(Res.string.sign_in_subtitle),
            mode = AuthMode.Login,
            uiState = uiState,
            submitLabel = stringResource(Res.string.sign_in),
            onNameChange = viewModel::onNameChange,
            onEmailChange = viewModel::onEmailChange,
            onLoginIdentifierChange = viewModel::onLoginIdentifierChange,
            onPasswordChange = viewModel::onPasswordChange,
            onPhoneChange = viewModel::onPhoneChange,
            onSubmit = viewModel::login,
            alternateActionLabel = stringResource(Res.string.create_an_account),
            onAlternateAction = onNavigateToRegister,
            showHealthCheck = Environment.isDebug,
            onHealthCheck = {
                scope.launch {
                    viewModel.checkBackendHealth()
                        .onSuccess { message -> snackbarHostState.showSnackbar(message) }
                        .onFailure { error -> snackbarHostState.showSnackbar(error.toUserMessage()) }
                }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun AuthForm(
    title: String,
    subtitle: String,
    mode: AuthMode,
    uiState: AuthUiState,
    submitLabel: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onLoginIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    alternateActionLabel: String,
    onAlternateAction: () -> Unit,
    showHealthCheck: Boolean = false,
    onHealthCheck: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isLogin = mode == AuthMode.Login

    YapFormContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                            .padding(
                                horizontal = YapScreenHorizontalPadding,
                                vertical = YapSpacing.md,
                            ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = YapSpacing.xs),
            )

            Spacer(modifier = Modifier.height(YapSpacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(YapSpacing.md),
                ) {
                    if (isLogin) {
                        OutlinedTextField(
                            value = uiState.loginIdentifier,
                            onValueChange = onLoginIdentifierChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.email_or_phone)) },
                            placeholder = { Text(stringResource(Res.string.login_identifier_hint)) },
                            singleLine = true,
                            isError = uiState.loginIdentifierError != null,
                            supportingText = uiState.loginIdentifierError?.let { error ->
                                { Text(error.toMessage(), color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !uiState.isLoading,
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = onNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.name)) },
                            singleLine = true,
                            isError = uiState.nameError != null,
                            supportingText = uiState.nameError?.let { error ->
                                { Text(error.toMessage(), color = MaterialTheme.colorScheme.error) }
                            },
                            enabled = !uiState.isLoading,
                        )
                        Spacer(modifier = Modifier.height(YapSpacing.sm))

                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.email)) },
                            singleLine = true,
                            isError = uiState.emailError != null,
                            supportingText = uiState.emailError?.let { error ->
                                { Text(error.toMessage(), color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !uiState.isLoading,
                        )
                        Spacer(modifier = Modifier.height(YapSpacing.sm))

                        OutlinedTextField(
                            value = uiState.phone,
                            onValueChange = onPhoneChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.phone)) },
                            singleLine = true,
                            isError = uiState.phoneError != null,
                            supportingText = uiState.phoneError?.let { error ->
                                { Text(error.toMessage(), color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled = !uiState.isLoading,
                        )
                    }

                    Spacer(modifier = Modifier.height(YapSpacing.sm))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.password)) },
                        singleLine = true,
                        isError = uiState.passwordError != null,
                        supportingText = uiState.passwordError?.let { error ->
                            { Text(error.toMessage(), color = MaterialTheme.colorScheme.error) }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    text = stringResource(
                                        if (passwordVisible) Res.string.hide_password else Res.string.show_password,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !uiState.isLoading,
                    )

                    uiState.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(YapSpacing.sm))
                        Text(
                            text = message.ifBlank { stringResource(Res.string.auth_failed) },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(YapSpacing.md))

                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(submitLabel)
                        }
                    }
                }
            }

            TextButton(
                onClick = onAlternateAction,
                enabled = !uiState.isLoading,
                modifier = Modifier.padding(top = YapSpacing.sm),
            ) {
                Text(alternateActionLabel)
            }

            if (showHealthCheck) {
                TextButton(
                    onClick = onHealthCheck,
                    enabled = !uiState.isLoading,
                ) {
                    Text(stringResource(Res.string.health_check))
                }
            }
        }
    }
}
