package com.yapyap.core.call

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object IosLiveKitBridgeRegistry {
    var bridge: IosLiveKitBridge? = null
    private var swiftHandler: IosLiveKitSwiftHandler? = null
    private var activeConnectCallback: ((Result<Unit>) -> Unit)? = null

    fun registerSwiftHandler(handler: IosLiveKitSwiftHandler) {
        swiftHandler = handler
    }

    suspend fun connect(request: IosLiveKitBridge.ConnectRequest): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val handler = swiftHandler
                ?: return@suspendCancellableCoroutine continuation.resume(
                    Result.failure(IllegalStateException("LiveKit Swift handler not registered")),
                )
            val sessionId = request.callSessionId
            activeConnectCallback = { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation {
                activeConnectCallback = null
                CallDiagnostics.stage(stage = "CALL_COROUTINE_CANCELLED", callSessionId = sessionId)
                disconnect(sessionId)
            }
            handler.connect(request)
        }

    fun disconnectAll() {
        swiftHandler?.disconnectAll()
    }

    fun disconnect(callSessionId: String) {
        swiftHandler?.disconnect(callSessionId)
    }

    fun setMuted(muted: Boolean) {
        swiftHandler?.setMuted(muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        swiftHandler?.setCameraEnabled(enabled)
    }

    fun switchCamera() {
        swiftHandler?.switchCamera()
    }

    fun onConnectCompleted(success: Boolean, errorMessage: String?) {
        activeConnectCallback?.invoke(
            if (success) Result.success(Unit)
            else Result.failure(IllegalStateException(errorMessage ?: "LiveKit connect failed")),
        )
        activeConnectCallback = null
    }

    fun onConnectionStateChanged(raw: String) {
        val state = when (raw.lowercase()) {
            "connecting" -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Connecting
            "connected" -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Connected
            "reconnecting" -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Reconnecting
            "disconnected" -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Disconnected
            "poor" -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.PoorConnection
            else -> com.yapyap.feature.call.domain.entity.CallMediaConnectionState.Idle
        }
        bridge?.emitConnectionState(state)
    }

    fun onMutedChanged(muted: Boolean) = bridge?.emitMuted(muted)
    fun onCameraEnabledChanged(enabled: Boolean) = bridge?.emitCameraEnabled(enabled)
    fun onLocalTrackChanged(trackId: String?) = bridge?.emitLocalTrack(trackId)
    fun onRemoteTrackChanged(trackId: String?) = bridge?.emitRemoteTrack(trackId)
    fun onRemoteParticipantConnected(connected: Boolean) = bridge?.emitRemoteConnected(connected)
}

interface IosLiveKitSwiftHandler {
    fun connect(request: IosLiveKitBridge.ConnectRequest)
    fun disconnect(callSessionId: String)
    fun disconnectAll()
    fun setMuted(muted: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    fun switchCamera()
}

fun registerIosLiveKitHandler(handler: IosLiveKitSwiftHandler) {
    IosLiveKitBridgeRegistry.registerSwiftHandler(handler)
}

fun iosLiveKitOnConnectCompleted(success: Boolean, errorMessage: String?) {
    IosLiveKitBridgeRegistry.onConnectCompleted(success, errorMessage)
}

fun iosLiveKitOnConnectionState(raw: String) {
    IosLiveKitBridgeRegistry.onConnectionStateChanged(raw)
}

fun iosLiveKitOnMutedChanged(muted: Boolean) {
    IosLiveKitBridgeRegistry.onMutedChanged(muted)
}

fun iosLiveKitOnCameraEnabledChanged(enabled: Boolean) {
    IosLiveKitBridgeRegistry.onCameraEnabledChanged(enabled)
}

fun iosLiveKitOnLocalTrackChanged(trackId: String?) {
    IosLiveKitBridgeRegistry.onLocalTrackChanged(trackId)
}

fun iosLiveKitOnRemoteTrackChanged(trackId: String?) {
    IosLiveKitBridgeRegistry.onRemoteTrackChanged(trackId)
}

fun iosLiveKitOnRemoteParticipantConnected(connected: Boolean) {
    IosLiveKitBridgeRegistry.onRemoteParticipantConnected(connected)
}

fun iosLiveKitCreateVideoViewController(trackId: String?, mirror: Boolean, fit: String): platform.UIKit.UIViewController {
    return IosLiveKitVideoViewRegistry.create(trackId, mirror, fit)
}

object IosLiveKitVideoViewRegistry {
    private var factory: ((String?, Boolean, String) -> platform.UIKit.UIViewController)? = null

    fun register(factory: (String?, Boolean, String) -> platform.UIKit.UIViewController) {
        this.factory = factory
    }

    fun create(trackId: String?, mirror: Boolean, fit: String): platform.UIKit.UIViewController {
        return factory?.invoke(trackId, mirror, fit) ?: platform.UIKit.UIViewController()
    }
}

fun registerIosLiveKitVideoViewFactory(
    factory: (String?, Boolean, String) -> platform.UIKit.UIViewController,
) {
    IosLiveKitVideoViewRegistry.register(factory)
}

fun iosLiveKitLogStage(stage: String, callSessionId: String? = null) {
    CallDiagnostics.stage(stage = stage, callSessionId = callSessionId)
}

fun iosLiveKitDisconnectAll() {
    IosLiveKitBridgeRegistry.disconnectAll()
}

fun iosLiveKitMediaHealthProbeReset() {
    CallMediaHealthProbe.reset()
}

fun iosLiveKitMediaHealthProbeRendererAttached(isLocal: Boolean) {
    CallMediaHealthProbe.onRendererAttached(isLocal)
}

fun iosLiveKitMediaHealthProbeFirstVideoFrame() {
    CallMediaHealthProbe.onFirstVideoFrame()
}

fun iosLiveKitMediaHealthProbeRendererStatus(): String = CallMediaHealthProbe.rendererStatus()

fun iosLiveKitMediaHealthProbeHasRenderedFrame(): Boolean = CallMediaHealthProbe.hasRenderedVideoFrame()

fun iosLiveKitEmitMediaHealthReport(
    callSessionId: String?,
    participantCount: Int,
    localIdentity: String,
    remoteIdentities: String,
    publishedLocalTracks: Int,
    subscribedRemoteTracks: Int,
    localAudioState: String,
    localVideoState: String,
    remoteAudioState: String,
    remoteVideoState: String,
    cameraCapturerState: String,
    rendererAttached: String,
    videoFrameRendered: Boolean,
    videoCallExpected: Boolean,
    trackFramesDetected: Boolean,
) {
    CallMediaHealthReport.emit(
        callSessionId = callSessionId,
        snapshot = CallMediaHealthSnapshot(
            participantCount = participantCount,
            localIdentity = localIdentity,
            remoteIdentities = remoteIdentities.split(",").filter { it.isNotBlank() },
            publishedLocalTrackCount = publishedLocalTracks,
            subscribedRemoteTrackCount = subscribedRemoteTracks,
            localAudioState = localAudioState,
            localVideoState = localVideoState,
            remoteAudioState = remoteAudioState,
            remoteVideoState = remoteVideoState,
            cameraCapturerState = cameraCapturerState,
            rendererAttached = rendererAttached,
            videoFrameRendered = videoFrameRendered,
            videoCallExpected = videoCallExpected,
            trackFramesDetected = trackFramesDetected,
        ),
    )
}
