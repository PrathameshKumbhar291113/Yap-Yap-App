package com.yapyap.feature.call.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapCallGreen
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapInsets
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_incoming
import yapyap.shared.generated.resources.call_incoming_video

@Composable
fun IncomingCallOverlay(
    uiState: CallUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YapCallGreen),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YapSpacing.lg),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(YapSpacing.lg),
        ) {
            Text(
                text = if (uiState.isVideoCall) {
                    stringResource(Res.string.call_incoming_video)
                } else {
                    stringResource(Res.string.call_incoming)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            YapAvatar(
                label = uiState.remoteDisplayName,
                size = 128.dp,
            )
            Text(
                text = uiState.remoteDisplayName.ifBlank { uiState.statusMessage },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(YapSpacing.xl, Alignment.CenterHorizontally),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(YapInsets.fullScreenOverlay)
                .padding(YapSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HangupButton(onClick = onDecline)
            AcceptCallButton(
                onClick = onAccept,
                enabled = !uiState.isAcceptInProgress,
            )
        }
    }
}

@Composable
private fun AcceptCallButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
            com.yapyap.core.ui.YapIcon(
                resource = com.yapyap.core.ui.YapDrawable.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
