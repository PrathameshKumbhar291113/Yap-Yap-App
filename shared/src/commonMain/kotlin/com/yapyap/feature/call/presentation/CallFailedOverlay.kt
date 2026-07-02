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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapCallGreen
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapInsets
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_dismiss

@Composable
fun CallFailedOverlay(
    uiState: CallUiState,
    onEndCall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YapCallGreen),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = YapSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YapSpacing.md),
        ) {
            YapAvatar(
                label = uiState.remoteDisplayName,
                size = 128.dp,
            )
            if (uiState.remoteDisplayName.isNotBlank()) {
                Text(
                    text = uiState.remoteDisplayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(YapInsets.fullScreenOverlay)
                .padding(YapSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(
                YapSpacing.xl,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.call_dismiss),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            HangupButton(onClick = onEndCall)
        }
    }
}
