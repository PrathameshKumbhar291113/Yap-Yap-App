package com.yapyap.feature.call.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapCallGreen
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapAvatar
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapInsets
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.cd_back

@Composable
fun ActiveCallScreen(
    uiState: CallUiState,
    controls: @Composable () -> Unit,
    onMinimize: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hasRemoteVideo = uiState.isVideoCall && uiState.remoteVideoTrack != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YapCallGreen),
    ) {
        if (uiState.isVideoCall) {
            if (hasRemoteVideo) {
                CallVideoRenderer(
                    track = uiState.remoteVideoTrack,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1B3A34),
                                    Color(0xFF0B141A),
                                ),
                            ),
                        ),
                )
            }
            if (uiState.isVideoCall && uiState.localVideoTrack != null) {
                CallVideoRenderer(
                    track = uiState.localVideoTrack,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(YapSpacing.md)
                        .size(width = 112.dp, height = 160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    mirror = true,
                    fit = CallVideoFit.Fit,
                )
            }
        }

        if (uiState.isVideoCall) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .windowInsetsPadding(YapInsets.fullScreenOverlay)
                    .padding(horizontal = YapSpacing.lg, vertical = YapSpacing.md),
            ) {
                if (onMinimize != null) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        YapIcon(
                            resource = YapDrawable.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                            tint = Color.White,
                        )
                    }
                }
                CallHeaderContent(
                    uiState = uiState,
                    showAvatar = false,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            if (onMinimize != null) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(YapInsets.fullScreenOverlay)
                        .padding(start = YapSpacing.sm, top = YapSpacing.sm),
                ) {
                    YapIcon(
                        resource = YapDrawable.ArrowBack,
                        contentDescription = stringResource(Res.string.cd_back),
                        tint = Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = YapSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CallHeaderContent(uiState = uiState, showAvatar = true)
            }
        }

        uiState.actionError?.let { error ->
            Text(
                text = error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp, start = YapSpacing.lg, end = YapSpacing.lg),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }

        CallDiagnosticsOverlay(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 120.dp, start = YapSpacing.sm),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
                )
                .windowInsetsPadding(YapInsets.fullScreenOverlay)
                .padding(horizontal = YapSpacing.lg, vertical = YapSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            controls()
        }
    }
}

@Composable
private fun CallHeaderContent(
    uiState: CallUiState,
    showAvatar: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YapSpacing.sm),
    ) {
        if (showAvatar) {
            YapAvatar(
                label = uiState.remoteDisplayName,
                size = 128.dp,
            )
        }
        Text(
            text = uiState.remoteDisplayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        val subtitle = when {
            uiState.showTimer -> formatCallDuration(uiState.elapsedSeconds ?: 0)
            uiState.statusMessage.isNotBlank() -> uiState.statusMessage
            uiState.callState is com.yapyap.feature.call.domain.entity.CallState.Connected -> "Connected"
            else -> ""
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
