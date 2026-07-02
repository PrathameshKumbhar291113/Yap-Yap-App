package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.yapyap.core.call.CallDiagnostics
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack

@Composable
actual fun CallVideoRenderer(
    track: CallRenderableVideoTrack?,
    modifier: Modifier,
    mirror: Boolean,
    fit: CallVideoFit,
) {
    key(track?.nativeTrackId) {
        IosLiveKitVideoView(
            trackId = track?.nativeTrackId,
            modifier = modifier,
            mirror = mirror,
            fit = fit,
        )
    }
}

@Composable
internal fun IosLiveKitVideoView(
    trackId: String?,
    modifier: Modifier,
    mirror: Boolean,
    fit: CallVideoFit = CallVideoFit.Fill,
) {
    androidx.compose.ui.viewinterop.UIKitViewController(
        modifier = modifier,
        factory = {
            CallDiagnostics.stage(stage = "CALL_RENDERER_ATTACHED")
            com.yapyap.core.call.iosLiveKitVideoViewController(trackId, mirror, fit.name)
        },
        onRelease = {
            CallDiagnostics.stage(stage = "CALL_RENDERER_RELEASED")
        },
    )
}
