package com.yapyap.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import com.yapyap.core.ui.YapZeroWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapContentContainer
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.YapScreenHorizontalPadding
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.edit_profile
import yapyap.shared.generated.resources.failed_update_profile
import yapyap.shared.generated.resources.name
import yapyap.shared.generated.resources.not_signed_in
import yapyap.shared.generated.resources.phone
import yapyap.shared.generated.resources.profile
import yapyap.shared.generated.resources.profile_updated
import yapyap.shared.generated.resources.save_changes
import yapyap.shared.generated.resources.sign_out

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = YapZeroWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile)) },
                windowInsets = YapInsets.topAppBar,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            ProfileUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(YapSpacing.md),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message.ifBlank { stringResource(Res.string.not_signed_in) },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is ProfileUiState.Content -> {
                YapContentContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    ProfileContent(
                        state = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = YapScreenHorizontalPadding,
                                vertical = YapSpacing.lg,
                            ),
                        onNameChange = viewModel::onNameChange,
                        onPhoneChange = viewModel::onPhoneChange,
                        onSave = viewModel::saveProfile,
                        onLogout = { viewModel.logout(onLogout) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Content,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onLogout: () -> Unit,
) {
    val displayName = state.user.name ?: state.user.email

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        YapAvatar(label = displayName, size = 88.dp)

        Spacer(modifier = Modifier.height(YapSpacing.sm))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = state.user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = YapSpacing.xxs),
        )
        state.user.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            Text(
                text = phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
                Text(
                    text = stringResource(Res.string.edit_profile),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(YapSpacing.sm))

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.name)) },
                singleLine = true,
                enabled = !state.isSaving && !state.isLoggingOut,
            )

            Spacer(modifier = Modifier.height(YapSpacing.xs + YapSpacing.xxs))

            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !state.isSaving && !state.isLoggingOut,
            )

            state.saveError?.let { error ->
                Spacer(modifier = Modifier.height(YapSpacing.xs))
                Text(
                    text = error.ifBlank { stringResource(Res.string.failed_update_profile) },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.saveSuccess) {
                Spacer(modifier = Modifier.height(YapSpacing.xs))
                Text(
                    text = stringResource(Res.string.profile_updated),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(YapSpacing.sm))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving && !state.isLoggingOut,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.save_changes))
                }
            }

            Spacer(modifier = Modifier.height(YapSpacing.md))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving && !state.isLoggingOut,
            ) {
                if (state.isLoggingOut) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.sign_out))
                }
            }
            }
        }
    }
}
