@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.yapyap.feature.chat.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.FullScreenError
import com.yapyap.core.ui.YapInsets
import com.yapyap.core.ui.chatMessageInputInsets
import com.yapyap.core.ui.YapScreenHorizontalPadding
import com.yapyap.core.ui.haptic.rememberSendHapticEffect
import com.yapyap.core.util.todayDateKey
import com.yapyap.core.util.yesterdayDateKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import org.jetbrains.compose.resources.stringResource

private enum class ChatScrollMode {
    PendingInitialPosition,
    AnchoredAtUnread,
    PinnedToBottom,
    FreeScroll,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    openAtLatest: Boolean = false,
    onBack: () -> Unit,
) {
    val parsedConversationId = conversationId?.toIntOrNull()?.takeIf { it > 0 }
    if (parsedConversationId == null) {
        Scaffold(
            contentWindowInsets = YapInsets.screenContent,
            topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.chats)) },
                windowInsets = YapInsets.topAppBarStandalone,
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
        openAtLatest = openAtLatest,
        viewModel = koinViewModel(parsedConversationId),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    conversationId: String,
    openAtLatest: Boolean,
    onBack: () -> Unit,
    viewModel: ChatViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversationTitle by viewModel.conversationTitle.collectAsState()
    val titleLabel = conversationTitle.ifBlank { stringResource(Res.string.unknown_user) }
    val input by viewModel.messageInput.collectAsState()
    val listState = rememberLazyListState()
    val hapticSend = rememberSendHapticEffect()
    var scrollMode by remember { mutableStateOf(ChatScrollMode.PendingInitialPosition) }
    var initialScrollDone by remember { mutableStateOf(false) }
    var previousUnreadCount by remember { mutableIntStateOf(-1) }
    var anchorUnreadCount by remember { mutableIntStateOf(0) }
    var lastAutoScrollKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState, initialScrollDone) {
        if (!initialScrollDone) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 1) {
                    viewModel.loadOlderMessages()
                }
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = YapInsets.screenContent,
        topBar = {
            TopAppBar(
                title = { Text(titleLabel) },
                windowInsets = YapInsets.topAppBarStandalone,
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
                    scrollMode = ChatScrollMode.PinnedToBottom
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
                    val headerItemCount = when {
                        state.isLoadingOlder || state.hasMore -> 1
                        else -> 0
                    }
                    val trailingItemCount = if (state.typingUserCount > 0) 1 else 0
                    val latestMessageKey = state.messages.lastOrNull()?.stableKey

                    LaunchedEffect(state.unreadCount, openAtLatest) {
                        if (!openAtLatest && anchorUnreadCount == 0 && state.unreadCount > 0) {
                            anchorUnreadCount = state.unreadCount
                        }
                    }

                    fun listIndexToLazyIndex(listItemIndex: Int): Int =
                        (headerItemCount + listItemIndex).coerceAtLeast(0)

                    fun lazyBottomIndex(): Int =
                        (headerItemCount + listItems.size + trailingItemCount - 1).coerceAtLeast(0)

                    LaunchedEffect(
                        state.messages,
                        anchorUnreadCount,
                        state.hasMore,
                        state.isLoadingOlder,
                        openAtLatest,
                    ) {
                        if (initialScrollDone || state.messages.isEmpty()) return@LaunchedEffect
                        if (
                            !openAtLatest &&
                            needsOlderMessagesForUnread(state.messages, anchorUnreadCount, state.hasMore)
                        ) {
                            if (!state.isLoadingOlder) {
                                viewModel.loadOlderMessages()
                            }
                        }
                    }

                    LaunchedEffect(
                        state.messages,
                        anchorUnreadCount,
                        listItems.size,
                        headerItemCount,
                        trailingItemCount,
                        state.hasMore,
                        state.isLoadingOlder,
                        openAtLatest,
                    ) {
                        if (initialScrollDone || state.messages.isEmpty() || listItems.isEmpty()) {
                            return@LaunchedEffect
                        }
                        if (
                            !openAtLatest &&
                            needsOlderMessagesForUnread(state.messages, anchorUnreadCount, state.hasMore)
                        ) {
                            return@LaunchedEffect
                        }
                        if (state.isLoadingOlder) return@LaunchedEffect

                        val unreadForAnchor = if (openAtLatest) {
                            0
                        } else {
                            anchorUnreadCount.takeIf { it > 0 } ?: state.unreadCount
                        }
                        val listItemIndex = if (unreadForAnchor > 0) {
                            findFirstUnreadListIndex(listItems, state.messages, unreadForAnchor)
                        } else {
                            listItems.lastIndex
                        }
                        val targetLazyIndex = listIndexToLazyIndex(listItemIndex)
                        val requiredItems = headerItemCount + listItems.size + trailingItemCount

                        snapshotFlow { listState.layoutInfo.totalItemsCount }
                            .first { it >= requiredItems }

                        listState.scrollToItem(targetLazyIndex)
                        initialScrollDone = true
                        scrollMode = if (unreadForAnchor > 0) {
                            ChatScrollMode.AnchoredAtUnread
                        } else {
                            ChatScrollMode.PinnedToBottom
                        }
                        lastAutoScrollKey = latestMessageKey
                        if (openAtLatest) {
                            viewModel.onReachedBottom()
                        }
                    }

                    LaunchedEffect(state.unreadCount) {
                        val previous = previousUnreadCount
                        previousUnreadCount = state.unreadCount
                        if (!initialScrollDone || previous <= 0 || state.unreadCount != 0) return@LaunchedEffect
                        scrollMode = ChatScrollMode.PinnedToBottom
                        listState.animateScrollToItem(lazyBottomIndex())
                        lastAutoScrollKey = latestMessageKey
                    }

                    LaunchedEffect(listState, headerItemCount, listItems.size, trailingItemCount) {
                        snapshotFlow {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            if (totalItems == 0) {
                                false
                            } else {
                                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= totalItems - 2
                            }
                        }
                            .distinctUntilChanged()
                            .collect { atBottom ->
                                if (!initialScrollDone) return@collect
                                if (atBottom) {
                                    if (scrollMode != ChatScrollMode.PendingInitialPosition) {
                                        scrollMode = ChatScrollMode.PinnedToBottom
                                        viewModel.onReachedBottom()
                                    }
                                } else if (scrollMode == ChatScrollMode.PinnedToBottom) {
                                    scrollMode = ChatScrollMode.FreeScroll
                                }
                            }
                    }

                    LaunchedEffect(latestMessageKey, scrollMode, listItems.size, trailingItemCount, openAtLatest) {
                        if (!initialScrollDone || latestMessageKey == null) return@LaunchedEffect
                        if (scrollMode != ChatScrollMode.PinnedToBottom && !openAtLatest) return@LaunchedEffect
                        if (latestMessageKey == lastAutoScrollKey) return@LaunchedEffect
                        val listItemIndex = findLatestMessageListIndex(listItems, latestMessageKey)
                        listState.animateScrollToItem(listIndexToLazyIndex(listItemIndex))
                        lastAutoScrollKey = latestMessageKey
                        if (openAtLatest) {
                            scrollMode = ChatScrollMode.PinnedToBottom
                            viewModel.onReachedBottom()
                        }
                    }

                    LaunchedEffect(state.typingUserCount, scrollMode, trailingItemCount, listItems.size) {
                        if (!initialScrollDone || state.typingUserCount <= 0) return@LaunchedEffect
                        val layoutInfo = listState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val nearBottom = totalItems > 0 &&
                            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= totalItems - 3
                        if (scrollMode == ChatScrollMode.PinnedToBottom || nearBottom) {
                            listState.animateScrollToItem(lazyBottomIndex())
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(YapSpacing.xs),
                        contentPadding = PaddingValues(
                            start = YapScreenHorizontalPadding,
                            end = YapScreenHorizontalPadding,
                            top = YapSpacing.xs,
                            bottom = if (state.typingUserCount > 0) YapSpacing.md else YapSpacing.xs,
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

                        items(listItems, key = { it.key }) { item ->
                            when (item) {
                                is ChatListItem.DateSeparator -> DateSeparatorChip(label = item.label)
                                is ChatListItem.MessageItem -> MessageBubble(
                                    message = item.message,
                                    onRetry = item.message.clientMessageId?.let { id ->
                                        { viewModel.retryMessage(id) }
                                    },
                                    onDelete = item.message.clientMessageId?.let { id ->
                                        { viewModel.deleteMessage(id) }
                                    },
                                )
                            }
                        }

                        if (state.typingUserCount > 0) {
                            item(key = "typing_indicator") {
                                TypingIndicatorBubble(
                                    label = if (state.typingUserCount == 1) {
                                        stringResource(Res.string.typing_single)
                                    } else {
                                        stringResource(Res.string.typing_multiple, state.typingUserCount)
                                    },
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
private fun TypingIndicatorBubble(
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YapScreenHorizontalPadding),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 72.dp, max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = YapSpacing.sm,
                        topEnd = YapSpacing.sm,
                        bottomStart = YapSpacing.xxs,
                        bottomEnd = YapSpacing.sm,
                    ),
                )
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = YapSpacing.sm, vertical = YapSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(YapSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypingDots()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TypingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dotDelaysMs = listOf(0, 160, 320)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(YapSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dotDelaysMs.forEachIndexed { index, delayMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "typing_dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
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
private fun MessageBubble(
    message: MessageUiModel,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val fallback = stringResource(Res.string.message_fallback)
    val displayContent = message.content.ifBlank { fallback }
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val canManageFailed = message.isFailed && message.isOutgoing

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
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .combinedClickable(
                        onClick = {
                            if (canManageFailed) {
                                onRetry?.invoke()
                            }
                        },
                        onLongClick = {
                            if (message.isOutgoing) {
                                menuExpanded = true
                            }
                        },
                    )
                    .padding(horizontal = YapSpacing.sm - YapSpacing.xxs, vertical = YapSpacing.xs),
            ) {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isOutgoing) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (canManageFailed) {
                    DropdownMenuItem(
                        text = { Text("Retry") },
                        onClick = {
                            menuExpanded = false
                            onRetry?.invoke()
                        },
                    )
                }
                if (canManageFailed) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete?.invoke()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        menuExpanded = false
                        clipboardManager.setText(AnnotatedString(displayContent))
                    },
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
        receipt == ReadReceiptVisual.Read -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    when (receipt) {
        ReadReceiptVisual.Sending -> SendingIndicator(tint = tint)
        else -> {
            val symbol = when (receipt) {
                ReadReceiptVisual.Queued -> "🕐"
                ReadReceiptVisual.Failed -> "!"
                ReadReceiptVisual.Sent -> "✓"
                ReadReceiptVisual.Delivered -> "✓✓"
                ReadReceiptVisual.Read -> "✓✓"
                ReadReceiptVisual.Sending -> "…"
            }
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = tint,
            )
        }
    }
}

@Composable
private fun SendingIndicator(tint: Color) {
    val transition = rememberInfiniteTransition(label = "sending_indicator")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sending_alpha",
    )
    Text(
        text = "…",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = tint.copy(alpha = alpha),
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
            .chatMessageInputInsets()
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
