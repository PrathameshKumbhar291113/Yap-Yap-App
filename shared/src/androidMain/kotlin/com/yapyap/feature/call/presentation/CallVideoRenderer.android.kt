package com.yapyap.feature.call.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yapyap.core.call.AndroidLiveKitRoomHolder
import com.yapyap.core.call.CallDiagnostics
import com.yapyap.core.call.CallMediaHealthProbe
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import com.yapyap.feature.call.domain.media.asVideoTrack
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.EglRenderer
import livekit.org.webrtc.RendererCommon

private val videoRendererInitTag = Any()

@Composable
actual fun CallVideoRenderer(
    track: CallRenderableVideoTrack?,
    modifier: Modifier,
    mirror: Boolean,
    fit: CallVideoFit,
) {
    val videoTrack = track?.asVideoTrack()
    val trackKey = videoTrack?.sid?.toString().orEmpty()
    key(trackKey) {
        CallVideoRendererInternal(
            videoTrack = videoTrack,
            modifier = modifier,
            mirror = mirror,
            fit = fit,
        )
    }
}

@Composable
private fun CallVideoRendererInternal(
    videoTrack: VideoTrack?,
    modifier: Modifier,
    mirror: Boolean,
    fit: CallVideoFit,
) {
    val scalingType = when (fit) {
        CallVideoFit.Fill -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
        CallVideoFit.Fit -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
    }
    var attachedTrack by remember { mutableStateOf<VideoTrack?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                ensureVideoRendererInitialized(this)
                setScalingType(scalingType)
                setEnableHardwareScaler(true)
                registerFrameListener(this)
                CallDiagnostics.stage(
                    stage = "CALL_RENDERER_CREATED",
                    extra = mapOf("trackSid" to (videoTrack?.sid?.toString().orEmpty())),
                )
            }
        },
        update = { renderer ->
            ensureVideoRendererInitialized(renderer)
            renderer.setMirror(mirror)
            renderer.setScalingType(scalingType)
            if (attachedTrack !== videoTrack) {
                attachedTrack?.removeRenderer(renderer)
                videoTrack?.addRenderer(renderer)
                attachedTrack = videoTrack
                val isLocal = isLocalPreviewTrack(videoTrack)
                CallMediaHealthProbe.onRendererAttached(isLocal)
                CallDiagnostics.stage(
                    stage = "CALL_RENDERER_ATTACHED",
                    extra = mapOf(
                        "trackSid" to (videoTrack?.sid?.toString().orEmpty()),
                        "hasTrack" to (videoTrack != null).toString(),
                        "isLocal" to isLocal.toString(),
                    ),
                )
            }
        },
        onRelease = { renderer ->
            attachedTrack?.removeRenderer(renderer)
            renderer.release()
            attachedTrack = null
            CallDiagnostics.stage(stage = "CALL_RENDERER_RELEASED")
        },
    )
}

private fun ensureVideoRendererInitialized(renderer: SurfaceViewRenderer) {
    if (renderer.tag === videoRendererInitTag) return
    val room = AndroidLiveKitRoomHolder.room ?: return
    room.initVideoRenderer(renderer)
    renderer.tag = videoRendererInitTag
}

private fun isLocalPreviewTrack(videoTrack: VideoTrack?): Boolean {
    videoTrack ?: return false
    val localTrack = AndroidLiveKitRoomHolder.room
        ?.localParticipant
        ?.getTrackPublication(Track.Source.CAMERA)
        ?.track
    return localTrack?.sid == videoTrack.sid
}

private fun registerFrameListener(renderer: SurfaceViewRenderer) {
    runCatching {
        renderer.addFrameListener(
            EglRenderer.FrameListener { _ ->
                CallMediaHealthProbe.onFirstVideoFrame()
            },
            1f,
        )
    }
}
