package com.yapyap.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapReadReceipt
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.FullScreenError
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.YapScreenHorizontalPadding
import com.yapyap.core.ui.haptic.rememberSendHapticEffect
import com.yapyap.core.util.todayDateKey
import com.yapyap.core.util.yesterdayDateKey
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.cd_back
import yapyap.shared.generated.resources.cd_send
import yapyap.shared.generated.resources.chats
import yapyap.shared.generated.resources.failed_load_messages
import yapyap.shared.generated.resources.failed_send_message
import yapyap.shared.generated.resources.invalid_conversation
import yapyap.shared.generated.resources.message
import yapyap.shared.generated.resources.message_fallback
import yapyap.shared.generated.resources.scroll_for_older
import yapyap.shared.generated.resources.today
import yapyap.shared.generated.resources.typing_multiple
import yapyap.shared.generated.resources.typing_single
import yapyap.shared.generated.resources.unknown_user
import yapyap.shared.generated.resources.yesterday

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onBack: () -> Unit,
) {
    val parsedConversationId = conversationId?.toIntOrNull()?.takeIf { it > 0 }
    if (parsedConversationId == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.chats)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            YapIcon(
                                resource = YapDrawable.ArrowBack,
                                contentDescription = stringResource(Res.string.cd_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            FullScreenError(
                message = stringResource(Res.string.invalid_conversation),
                onRetry = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
        return
    }

    ChatScreenContent(
        conversationId = parsedConversationId.toString(),
        viewModel = koinViewModel(parsedConversationId),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversationTitle by viewModel.conversationTitle.collectAsState()
    val titleLabel = conversationTitle.ifBlank { stringResource(Res.string.unknown_user) }
    val input by viewModel.messageInput.collectAsState()
    val listState = rememberLazyListState()
    val hapticSend = rememberSendHapticEffect()
    var lastAutoScrollKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 1) {
                    viewModel.loadOlderMessages()
                }
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = YapInsets.content,
        topBar = {
            TopAppBar(
                title = { Text(titleLabel) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        YapIcon(
                            resource = YapDrawable.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            val content = uiState as? ChatUiState.Content
            MessageInputBar(
                value = input,
                onValueChange = viewModel::onInputChange,
                onSend = {
                    hapticSend()
                    viewModel.sendMessage()
                },
                sendError = content?.sendError?.ifBlank { stringResource(Res.string.failed_send_message) },
                enabled = content?.isSending != true,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                is ChatUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ChatUiState.Error -> {
                    FullScreenError(
                        message = state.message.ifBlank { stringResource(Res.string.failed_load_messages) },
                        onRetry = viewModel::refreshLatest,
                    )
                }
                is ChatUiState.Content -> {
                    val todayKey = remember { todayDateKey() }
                    val yesterdayKey = remember { yesterdayDateKey() }
                    val todayLabel = stringResource(Res.string.today)
                    val yesterdayLabel = stringResource(Res.string.yesterday)
                    val listItems = remember(state.messages, todayKey, yesterdayKey, todayLabel, yesterdayLabel) {
                        state.messages.toChatListItems(
                            todayKey = todayKey,
                            yesterdayKey = yesterdayKey,
                            todayLabel = todayLabel,
                            yesterdayLabel = yesterdayLabel,
                        )
                    }

                    val latestMessageKey = state.messages.lastOrNull()?.stableKey
                    LaunchedEffect(latestMessageKey) {
                        if (latestMessageKey == null || latestMessageKey == lastAutoScrollKey) return@LaunchedEffect
                        val targetIndex = listItems.indexOfLast {
                            it is ChatListItem.MessageItem && it.message.stableKey == latestMessageKey
                        }
                        if (targetIndex >= 0) {
                            listState.animateScrollToItem(targetIndex)
                            lastAutoScrollKey = latestMessageKey
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(YapSpacing.xs),
                        contentPadding = PaddingValues(
                            horizontal = YapScreenHorizontalPadding,
                            vertical = YapSpacing.xs,
                        ),
                    ) {
                        if (state.isLoadingOlder) {
                            item(key = "loading_older") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(YapSpacing.xs),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(YapSpacing.xs))
                                }
                            }
                        } else if (state.hasMore) {
                            item(key = "load_more_hint") {
                                Text(
                                    text = stringResource(Res.string.scroll_for_older),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(YapSpacing.xs),
                                )
                            }
                        }

                        if (state.typingUserCount > 0) {
                            item(key = "typing_indicator") {
                                Text(
                                    text = if (state.typingUserCount == 1) {
                                        stringResource(Res.string.typing_single)
                                    } else {
                                        stringResource(Res.string.typing_multiple, state.typingUserCount)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = YapSpacing.sm, vertical = YapSpacing.xxs),
                                )
                            }
                        }

                        items(listItems, key = { it.key }) { item ->
                            when (item) {
                                is ChatListItem.DateSeparator -> DateSeparatorChip(label = item.label)
                                is ChatListItem.MessageItem -> MessageBubble(message = item.message)
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
private fun DateSeparatorChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = YapSpacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(YapSpacing.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(horizontal = YapSpacing.sm, vertical = YapSpacing.xxs),
        )
    }
}

@Composable
private fun MessageBubble(message: MessageUiModel) {
    val fallback = stringResource(Res.string.message_fallback)
    val displayContent = message.content.ifBlank { fallback }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YapScreenHorizontalPadding),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = YapSpacing.sm,
                            topEnd = YapSpacing.sm,
                            bottomStart = if (message.isOutgoing) YapSpacing.sm else YapSpacing.xxs,
                            bottomEnd = if (message.isOutgoing) YapSpacing.xxs else YapSpacing.sm,
                        ),
                    )
                    .background(
                        if (message.isOutgoing) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .padding(horizontal = YapSpacing.sm - YapSpacing.xxs, vertical = YapSpacing.xs),
            ) {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(YapSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = YapSpacing.xxs, start = YapSpacing.xxs, end = YapSpacing.xxs),
            ) {
                message.timestamp?.let { timestamp ->
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message.readReceipt?.let { receipt ->
                    ReadReceiptIndicator(receipt = receipt, isFailed = message.isFailed)
                }
            }
        }
    }
}

@Composable
private fun ReadReceiptIndicator(
    receipt: ReadReceiptVisual,
    isFailed: Boolean,
) {
    val tint = when {
        isFailed -> MaterialTheme.colorScheme.error
        receipt == ReadReceiptVisual.Read -> YapReadReceipt
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val symbol = when (receipt) {
        ReadReceiptVisual.Pending -> "…"
        ReadReceiptVisual.Failed -> "!"
        ReadReceiptVisual.Sent -> "✓"
        ReadReceiptVisual.Delivered, ReadReceiptVisual.Read -> "✓✓"
    }
    Text(
        text = symbol,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = tint,
    )
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sendError: String?,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = YapSpacing.sm, vertical = YapSpacing.xs),
    ) {
        sendError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = YapSpacing.xxs),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YapSpacing.xs),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(Res.string.message)) },
                maxLines = 4,
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                YapIcon(
                    resource = YapDrawable.Send,
                    contentDescription = stringResource(Res.string.cd_send),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
