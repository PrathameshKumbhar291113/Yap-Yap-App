package com.yapyap.feature.conversations.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.FullScreenError
import com.yapyap.core.ui.ShimmerConversationList
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapContentContainer
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapScreenHorizontalPadding
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.YapZeroWindowInsets
import com.yapyap.feature.auth.presentation.AuthValidator
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.cancel
import yapyap.shared.generated.resources.cd_new_chat
import yapyap.shared.generated.resources.chats
import yapyap.shared.generated.resources.contact
import yapyap.shared.generated.resources.enter_contact
import yapyap.shared.generated.resources.error_contact_required
import yapyap.shared.generated.resources.error_invalid_contact
import yapyap.shared.generated.resources.new_chat
import yapyap.shared.generated.resources.no_chats_hint
import yapyap.shared.generated.resources.no_chats_yet
import yapyap.shared.generated.resources.no_messages_yet
import yapyap.shared.generated.resources.start
import yapyap.shared.generated.resources.start_new_chat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    onConversationClick: (String) -> Unit,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isPullRefreshing.collectAsState()
    val isCreatingChat by viewModel.isCreatingDirectChat.collectAsState()
    val newChatError by viewModel.newChatError.collectAsState()
    var showNewChatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    if (showNewChatDialog) {
        NewChatDialog(
            isLoading = isCreatingChat,
            errorMessage = newChatError,
            onDismiss = {
                showNewChatDialog = false
                viewModel.clearCreateChatError()
            },
            onConfirm = { contact ->
                viewModel.startDirectChat(contact) { conversationId ->
                    showNewChatDialog = false
                    onConversationClick(conversationId)
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = YapZeroWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.chats)) },
                windowInsets = YapInsets.topAppBarNested,
                actions = {
                    TextButton(onClick = { showNewChatDialog = true }) {
                        Text(stringResource(Res.string.new_chat))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                YapIcon(
                    resource = YapDrawable.Add,
                    contentDescription = stringResource(Res.string.cd_new_chat),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        YapContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = uiState) {
                    is UiState.Loading -> ShimmerConversationList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = YapScreenHorizontalPadding),
                    )
                    is UiState.Error -> FullScreenError(
                        message = state.message,
                        onRetry = viewModel::refresh,
                    )
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            EmptyConversationsState()
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = YapScreenHorizontalPadding,
                                    vertical = YapSpacing.sm,
                                ),
                                verticalArrangement = Arrangement.spacedBy(YapSpacing.xs),
                            ) {
                                items(state.data, key = { it.id }) { conversation ->
                                    ConversationRow(
                                        conversation = conversation,
                                        onClick = { onConversationClick(conversation.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(YapSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YapAvatar(label = conversation.title)

            Spacer(modifier = Modifier.width(YapSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    conversation.timestamp?.let { timestamp ->
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = YapSpacing.xs),
                        )
                    }
                }
                Text(
                    text = conversation.lastMessagePreview ?: stringResource(Res.string.no_messages_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = YapSpacing.xxs),
                )
            }

            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.width(YapSpacing.xs))
                BadgedBox(
                    badge = {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = conversation.unreadCount.coerceAtMost(99).toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = YapSpacing.xxs),
                            )
                        }
                    },
                ) {
                    Spacer(modifier = Modifier.width(YapSpacing.xs))
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(YapSpacing.lg),
        ) {
            Text(
                text = stringResource(Res.string.no_chats_yet),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.no_chats_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = YapSpacing.xs),
            )
        }
    }
}

@Composable
private fun NewChatDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var contactInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<ContactValidationError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.start_new_chat)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.enter_contact),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = contactInput,
                    onValueChange = {
                        contactInput = it
                        validationError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = YapSpacing.sm),
                    label = { Text(stringResource(Res.string.contact)) },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { error ->
                        {
                            Text(
                                text = when (error) {
                                    ContactValidationError.Required ->
                                        stringResource(Res.string.error_contact_required)
                                    ContactValidationError.Invalid ->
                                        stringResource(Res.string.error_invalid_contact)
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    enabled = !isLoading,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = YapSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = contactInput.trim()
                    validationError = when {
                        trimmed.isBlank() -> ContactValidationError.Required
                        !isValidContact(trimmed) -> ContactValidationError.Invalid
                        else -> null
                    }
                    if (validationError == null) {
                        onConfirm(trimmed)
                    }
                },
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(YapSpacing.xxs))
                } else {
                    Text(stringResource(Res.string.start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

private enum class ContactValidationError {
    Required,
    Invalid,
}

private fun isValidContact(contact: String): Boolean {
    return if ('@' in contact) {
        AuthValidator.validateEmail(contact) == null
    } else {
        AuthValidator.validatePhone(contact) == null
    }
}
