package com.yapyap.feature.call.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.yapyap.feature.call.presentation.YapCallTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yapyap.core.di.koinViewModel
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapInsets
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.entity.CallType
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.tab_calls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    onOpenChat: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallLogViewModel = koinViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(listState, hasMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }.collect { nearEnd ->
            if (nearEnd && hasMore) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = YapInsets.scaffoldContent,
        topBar = {
            YapCallTopAppBar(title = stringResource(Res.string.tab_calls))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
        ) {
            items(entries, key = { it.id }) { entry ->
                CallLogRow(
                    entry = entry,
                    onOpenChat = onOpenChat,
                    onStartVoiceCall = { viewModel.startCall(it, CallType.Voice) },
                    onStartVideoCall = { viewModel.startCall(it, CallType.Video) },
                )
            }
        }
    }
}

@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    onOpenChat: (conversationId: String) -> Unit,
    onStartVoiceCall: (CallLogEntry) -> Unit,
    onStartVideoCall: (CallLogEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                entry.conversationId?.let { onOpenChat(it.toString()) }
            }
            .padding(horizontal = YapSpacing.md, vertical = YapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YapSpacing.md),
    ) {
        YapAvatar(label = entry.remoteUser.displayName)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.remoteUser.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(entry.status.name.lowercase())
                    entry.durationSec?.takeIf { it > 0 }?.let { append(" · ${formatCallDuration(it.toLong())}") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(YapSpacing.sm)) {
            YapIcon(
                resource = YapDrawable.Call,
                contentDescription = null,
                modifier = Modifier.clickable { onStartVoiceCall(entry) },
            )
            YapIcon(
                resource = YapDrawable.Videocam,
                contentDescription = null,
                modifier = Modifier.clickable { onStartVideoCall(entry) },
            )
        }
    }
}
