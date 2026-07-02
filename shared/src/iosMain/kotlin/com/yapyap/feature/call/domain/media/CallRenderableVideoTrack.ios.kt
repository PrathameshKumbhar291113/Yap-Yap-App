package com.yapyap.feature.call.domain.media

/**
 * Wraps LiveKit Swift video track pointer exposed via [YapLiveKitBridge].
 */
actual class CallRenderableVideoTrack internal constructor(
    val nativeTrackId: String,
)
