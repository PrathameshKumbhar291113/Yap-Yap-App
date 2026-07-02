package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import kotlinx.coroutines.CoroutineScope

class IosLiveKitBridge(
    @Suppress("unused") private val scope: CoroutineScope,
) {
    private var onConnectionState: ((CallMediaConnectionState) -> Unit)? = null
    private var onMuted: ((Boolean) -> Unit)? = null
    private var onCameraEnabled: ((Boolean) -> Unit)? = null
    private var onLocalTrack: ((String?) -> Unit)? = null
    private var onRemoteTrack: ((String?) -> Unit)? = null
    private var onRemoteConnected: ((Boolean) -> Unit)? = null

    fun bindState(
        onConnectionState: (CallMediaConnectionState) -> Unit,
        onMuted: (Boolean) -> Unit,
        onCameraEnabled: (Boolean) -> Unit,
        onLocalTrack: (String?) -> Unit,
        onRemoteTrack: (String?) -> Unit,
        onRemoteConnected: (Boolean) -> Unit,
    ) {
        this.onConnectionState = onConnectionState
        this.onMuted = onMuted
        this.onCameraEnabled = onCameraEnabled
        this.onLocalTrack = onLocalTrack
        this.onRemoteTrack = onRemoteTrack
        this.onRemoteConnected = onRemoteConnected
        IosLiveKitBridgeRegistry.bridge = this
    }

    suspend fun connect(
        callSessionId: String,
        credentials: CallRoomCredentials,
        videoEnabled: Boolean,
        reconnecting: Boolean,
        restoreMuted: Boolean = false,
        restoreCameraEnabled: Boolean = videoEnabled,
    ): Result<Unit> = IosLiveKitBridgeRegistry.connect(
        ConnectRequest(
            callSessionId = callSessionId,
            livekitUrl = credentials.livekitUrl,
            providerToken = credentials.providerToken,
            roomName = credentials.roomName,
            videoEnabled = videoEnabled,
            reconnecting = reconnecting,
            restoreMuted = restoreMuted,
            restoreCameraEnabled = restoreCameraEnabled,
        ),
    )

    fun disconnect(callSessionId: String) {
        IosLiveKitBridgeRegistry.disconnect(callSessionId)
    }

    fun setMuted(muted: Boolean) {
        IosLiveKitBridgeRegistry.setMuted(muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        IosLiveKitBridgeRegistry.setCameraEnabled(enabled)
    }

    fun switchCamera() {
        IosLiveKitBridgeRegistry.switchCamera()
    }

    internal fun emitConnectionState(state: CallMediaConnectionState) {
        onConnectionState?.invoke(state)
    }

    internal fun emitMuted(muted: Boolean) {
        onMuted?.invoke(muted)
    }

    internal fun emitCameraEnabled(enabled: Boolean) {
        onCameraEnabled?.invoke(enabled)
    }

    internal fun emitLocalTrack(trackId: String?) {
        onLocalTrack?.invoke(trackId)
    }

    internal fun emitRemoteTrack(trackId: String?) {
        onRemoteTrack?.invoke(trackId)
    }

    internal fun emitRemoteConnected(connected: Boolean) {
        onRemoteConnected?.invoke(connected)
    }

    data class ConnectRequest(
        val callSessionId: String,
        val livekitUrl: String,
        val providerToken: String,
        val roomName: String,
        val videoEnabled: Boolean,
        val reconnecting: Boolean,
        val restoreMuted: Boolean,
        val restoreCameraEnabled: Boolean,
    )
}
