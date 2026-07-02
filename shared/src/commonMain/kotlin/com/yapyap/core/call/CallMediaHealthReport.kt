package com.yapyap.core.call

import com.yapyap.core.config.AppBuildConfig

data class CallMediaHealthSnapshot(
    val participantCount: Int,
    val localIdentity: String,
    val remoteIdentities: List<String>,
    val publishedLocalTrackCount: Int,
    val subscribedRemoteTrackCount: Int,
    val localAudioState: String,
    val localVideoState: String,
    val remoteAudioState: String,
    val remoteVideoState: String,
    val cameraCapturerState: String,
    val rendererAttached: String,
    val videoFrameRendered: Boolean,
    val videoCallExpected: Boolean,
    val trackFramesDetected: Boolean = false,
)

object CallMediaHealthReport {
    private const val REPORT_STAGE = "CALL_MEDIA_HEALTH_REPORT"
    private const val FAILURE_STAGE = "CALL_MEDIA_HEALTH_STAGE_FAILED"

    fun emit(callSessionId: String?, snapshot: CallMediaHealthSnapshot) {
        if (!AppBuildConfig.IS_DEBUG) return

        val frameRendered = snapshot.videoFrameRendered || snapshot.trackFramesDetected
        val failures = analyzeFailures(snapshot.copy(videoFrameRendered = frameRendered))

        CallDiagnostics.stage(
            stage = REPORT_STAGE,
            callSessionId = callSessionId,
            extra = linkedMapOf(
                "participantCount" to snapshot.participantCount.toString(),
                "localIdentity" to snapshot.localIdentity.ifBlank { "unknown" },
                "remoteIdentities" to snapshot.remoteIdentities.joinToString(",").ifBlank { "none" },
                "publishedLocalTracks" to snapshot.publishedLocalTrackCount.toString(),
                "subscribedRemoteTracks" to snapshot.subscribedRemoteTrackCount.toString(),
                "localAudio" to snapshot.localAudioState,
                "localVideo" to snapshot.localVideoState,
                "remoteAudio" to snapshot.remoteAudioState,
                "remoteVideo" to snapshot.remoteVideoState,
                "cameraCapturer" to snapshot.cameraCapturerState,
                "rendererAttached" to snapshot.rendererAttached,
                "videoFrameRendered" to frameRendered.toString(),
                "videoCallExpected" to snapshot.videoCallExpected.toString(),
                "failureCount" to failures.size.toString(),
            ),
        )

        if (failures.isEmpty()) {
            CallDiagnostics.stage(
                stage = "CALL_MEDIA_HEALTH_OK",
                callSessionId = callSessionId,
            )
        } else {
            failures.forEach { failure ->
                CallDiagnostics.stage(
                    stage = FAILURE_STAGE,
                    callSessionId = callSessionId,
                    extra = mapOf(
                        "stage" to failure.stage,
                        "reason" to failure.reason,
                    ),
                )
            }
        }
    }

    private data class StageFailure(val stage: String, val reason: String)

    private fun analyzeFailures(snapshot: CallMediaHealthSnapshot): List<StageFailure> {
        val failures = mutableListOf<StageFailure>()
        val remotePresent = snapshot.participantCount > 1 || snapshot.remoteIdentities.isNotEmpty()

        if (snapshot.localIdentity.isBlank()) {
            failures += StageFailure(
                stage = "CALL_ROOM_CONNECT",
                reason = "local participant identity missing after connect",
            )
        }

        val localAudioEnabled = snapshot.localAudioState.equals("enabled", ignoreCase = true)
        if (!localAudioEnabled) {
            failures += StageFailure(
                stage = "CALL_LOCAL_AUDIO_PUBLISHED",
                reason = "local audio is ${snapshot.localAudioState}; mic may not be published",
            )
        }

        if (snapshot.videoCallExpected) {
            if (snapshot.publishedLocalTrackCount == 0 ||
                !snapshot.localVideoState.equals("enabled", ignoreCase = true)
            ) {
                failures += StageFailure(
                    stage = "CALL_LOCAL_VIDEO_PUBLISHED",
                    reason = when {
                        snapshot.publishedLocalTrackCount == 0 ->
                            "no local tracks published (expected camera track)"
                        snapshot.localVideoState.equals("disabled", ignoreCase = true) ->
                            "local video publication exists but camera is disabled"
                        else -> "local video state is ${snapshot.localVideoState}"
                    },
                )
            }

            if (!snapshot.cameraCapturerState.equals("running", ignoreCase = true)) {
                failures += StageFailure(
                    stage = "CALL_CAMERA_STARTED",
                    reason = "camera capturer is ${snapshot.cameraCapturerState}",
                )
            }

            if (!CallMediaHealthProbe.localRendererAttached()) {
                failures += StageFailure(
                    stage = "CALL_RENDERER_ATTACHED",
                    reason = "local preview renderer not attached",
                )
            }
        }

        if (remotePresent) {
            if (snapshot.subscribedRemoteTrackCount == 0) {
                failures += StageFailure(
                    stage = "CALL_REMOTE_TRACK_SUBSCRIBED",
                    reason = "no remote tracks subscribed (remote participant present)",
                )
            }
            if (!snapshot.remoteAudioState.equals("subscribed", ignoreCase = true) &&
                !snapshot.remoteAudioState.equals("enabled", ignoreCase = true)
            ) {
                failures += StageFailure(
                    stage = "CALL_REMOTE_AUDIO_TRACK_SUBSCRIBED",
                    reason = "remote audio is ${snapshot.remoteAudioState}",
                )
            }
            if (snapshot.videoCallExpected &&
                !snapshot.remoteVideoState.equals("subscribed", ignoreCase = true) &&
                !snapshot.remoteVideoState.equals("enabled", ignoreCase = true)
            ) {
                failures += StageFailure(
                    stage = "CALL_REMOTE_VIDEO_TRACK_SUBSCRIBED",
                    reason = "remote video is ${snapshot.remoteVideoState}",
                )
            }
            if (snapshot.videoCallExpected && !CallMediaHealthProbe.remoteRendererAttached()) {
                failures += StageFailure(
                    stage = "CALL_RENDERER_ATTACHED",
                    reason = "remote video renderer not attached",
                )
            }
        }

        if (snapshot.videoCallExpected && !snapshot.videoFrameRendered) {
            failures += StageFailure(
                stage = "CALL_FIRST_REMOTE_VIDEO_FRAME",
                reason = "no video frame rendered or detected on any video track",
            )
        }

        return failures
    }
}
