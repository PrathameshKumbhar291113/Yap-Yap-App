package com.yapyap.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.yapyap.feature.call.presentation.YapCallTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.config.AppBuildConfig
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapContentContainer
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.YapScreenHorizontalPadding
import com.yapyap.core.ui.YapZeroWindowInsets
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.about_app_message
import yapyap.shared.generated.resources.application_version
import yapyap.shared.generated.resources.cancel
import yapyap.shared.generated.resources.coming_soon
import yapyap.shared.generated.resources.dialog_ok
import yapyap.shared.generated.resources.edit_profile
import yapyap.shared.generated.resources.failed_update_profile
import yapyap.shared.generated.resources.name
import yapyap.shared.generated.resources.not_signed_in
import yapyap.shared.generated.resources.profile
import yapyap.shared.generated.resources.profile_updated
import yapyap.shared.generated.resources.save_changes
import yapyap.shared.generated.resources.section_support
import yapyap.shared.generated.resources.section_user_settings
import yapyap.shared.generated.resources.setting_appearance
import yapyap.shared.generated.resources.setting_notifications
import yapyap.shared.generated.resources.setting_privacy
import yapyap.shared.generated.resources.setting_security
import yapyap.shared.generated.resources.setting_theme_system
import yapyap.shared.generated.resources.sign_out
import yapyap.shared.generated.resources.support_about
import yapyap.shared.generated.resources.support_help
import yapyap.shared.generated.resources.support_privacy_policy
import yapyap.shared.generated.resources.support_terms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditSheet by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    val contentState = uiState as? ProfileUiState.Content
    LaunchedEffect(contentState?.saveSuccess, showEditSheet) {
        if (showEditSheet && contentState?.saveSuccess == true) {
            showEditSheet = false
        }
    }

    infoDialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null },
            title = { Text(title) },
            text = { Text(infoDialogMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { infoDialogTitle = null }) {
                    Text(stringResource(Res.string.dialog_ok))
                }
            },
        )
    }

    if (showEditSheet) {
        val content = uiState as? ProfileUiState.Content
        if (content != null) {
            EditProfileBottomSheet(
                state = content,
                onDismiss = { showEditSheet = false },
                onNameChange = viewModel::onNameChange,
                onSave = viewModel::saveProfile,
            )
        }
    }

    Scaffold(
        contentWindowInsets = YapZeroWindowInsets,
        topBar = {
            YapCallTopAppBar(title = stringResource(Res.string.profile))
        },
    ) { innerPadding ->
        when (val state = uiState) {
            ProfileUiState.Loading -> LoadingState(modifier = Modifier.padding(innerPadding))
            is ProfileUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier.padding(innerPadding),
            )
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
                                vertical = YapSpacing.md,
                            ),
                        onEditProfile = { showEditSheet = true },
                        onLogout = { viewModel.logout(onLogout) },
                        onShowInfo = { title, message ->
                            infoDialogTitle = title
                            infoDialogMessage = message
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    state: ProfileUiState.Content,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayName = state.user.name ?: state.user.email

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YapSpacing.md)
                .padding(bottom = YapSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YapSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.edit_profile),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            YapAvatar(label = displayName, size = 72.dp)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.name)) },
                singleLine = true,
                enabled = !state.isSaving,
            )
            state.saveError?.let { error ->
                Text(
                    text = error.ifBlank { stringResource(Res.string.failed_update_profile) },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.saveSuccess) {
                Text(
                    text = stringResource(Res.string.profile_updated),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YapSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
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
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(YapSpacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.ifBlank { stringResource(Res.string.not_signed_in) },
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Content,
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onShowInfo: (String, String) -> Unit,
) {
    val displayName = state.user.name ?: state.user.email
    val comingSoon = stringResource(Res.string.coming_soon)
    val appVersion = AppBuildConfig.APP_VERSION
    val versionLabel = stringResource(Res.string.application_version)
    val aboutTitle = stringResource(Res.string.support_about)
    val aboutMessage = stringResource(Res.string.about_app_message)
    val notificationsTitle = stringResource(Res.string.setting_notifications)
    val privacyTitle = stringResource(Res.string.setting_privacy)
    val securityTitle = stringResource(Res.string.setting_security)
    val privacyPolicyTitle = stringResource(Res.string.support_privacy_policy)
    val termsTitle = stringResource(Res.string.support_terms)
    val helpTitle = stringResource(Res.string.support_help)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YapSpacing.md),
    ) {
        ProfileHeader(
            displayName = displayName,
            email = state.user.email,
            phone = state.user.phone,
        )

        ProfileSectionCard(title = stringResource(Res.string.section_user_settings)) {
            ProfileSettingRow(
                icon = YapDrawable.Edit,
                title = stringResource(Res.string.edit_profile),
                onClick = onEditProfile,
            )
            ProfileSettingRow(
                icon = YapDrawable.Notifications,
                title = notificationsTitle,
                subtitle = comingSoon,
                onClick = { onShowInfo(notificationsTitle, comingSoon) },
            )
            ProfileSettingRow(
                icon = YapDrawable.Palette,
                title = stringResource(Res.string.setting_appearance),
                subtitle = stringResource(Res.string.setting_theme_system),
                onClick = null,
            )
            ProfileSettingRow(
                icon = YapDrawable.Lock,
                title = privacyTitle,
                subtitle = comingSoon,
                onClick = { onShowInfo(privacyTitle, comingSoon) },
            )
            ProfileSettingRow(
                icon = YapDrawable.Shield,
                title = securityTitle,
                subtitle = comingSoon,
                onClick = { onShowInfo(securityTitle, comingSoon) },
            )
        }

        ProfileSectionCard(title = stringResource(Res.string.section_support)) {
            ProfileSettingRow(
                icon = YapDrawable.Description,
                title = privacyPolicyTitle,
                onClick = { onShowInfo(privacyPolicyTitle, comingSoon) },
            )
            ProfileSettingRow(
                icon = YapDrawable.Description,
                title = termsTitle,
                onClick = { onShowInfo(termsTitle, comingSoon) },
            )
            ProfileSettingRow(
                icon = YapDrawable.Help,
                title = helpTitle,
                onClick = { onShowInfo(helpTitle, comingSoon) },
            )
            ProfileSettingRow(
                icon = YapDrawable.Info,
                title = stringResource(Res.string.support_about),
                subtitle = "$versionLabel $appVersion",
                onClick = {
                    onShowInfo(
                        aboutTitle,
                        "$aboutMessage\n\n$versionLabel: $appVersion",
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(YapSpacing.xs))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving && !state.isLoggingOut,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.isLoggingOut) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = stringResource(Res.string.sign_out),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(YapSpacing.sm))
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    email: String,
    phone: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = YapSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        YapAvatar(label = displayName, size = 104.dp)
        Spacer(modifier = Modifier.height(YapSpacing.md))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = YapSpacing.xxs),
        )
        phone?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = YapSpacing.xxs),
            )
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = YapSpacing.md,
                    end = YapSpacing.md,
                    top = YapSpacing.md,
                    bottom = YapSpacing.xs,
                ),
            )
            content()
        }
    }
}

@Composable
private fun ProfileSettingRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = YapSpacing.md, vertical = YapSpacing.sm)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = YapSpacing.md, vertical = YapSpacing.sm)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YapSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            YapIcon(
                resource = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = YapSpacing.xxs),
                )
            }
        }
        if (onClick != null) {
            YapIcon(
                resource = YapDrawable.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = YapSpacing.md + 36.dp + YapSpacing.sm),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
