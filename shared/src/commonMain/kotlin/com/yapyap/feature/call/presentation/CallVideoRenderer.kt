package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack

enum class CallVideoFit {
    Fill,
    Fit,
}

@Composable
expect fun CallVideoRenderer(
    track: CallRenderableVideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    fit: CallVideoFit = CallVideoFit.Fill,
)
