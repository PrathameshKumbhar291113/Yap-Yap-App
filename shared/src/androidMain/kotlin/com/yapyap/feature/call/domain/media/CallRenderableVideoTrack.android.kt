package com.yapyap.feature.call.domain.media

import io.livekit.android.room.track.VideoTrack

actual class CallRenderableVideoTrack internal constructor(
    val track: VideoTrack,
)

fun VideoTrack.asRenderable(): CallRenderableVideoTrack = CallRenderableVideoTrack(this)

fun CallRenderableVideoTrack.asVideoTrack(): VideoTrack = track
