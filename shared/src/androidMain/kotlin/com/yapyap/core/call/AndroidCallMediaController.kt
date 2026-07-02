package com.yapyap.core.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.yapyap.core.config.AppBuildConfig
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import com.yapyap.feature.call.domain.media.asRenderable
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidCallMediaController(
    private val appContext: Context,
    dispatcherProvider: DispatcherProvider,
) : CallMediaController {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusManager = CallAudioFocusManager(appContext)

    private val _isMuted = MutableStateFlow(false)
    private val _isCameraEnabled = MutableStateFlow(false)
    private val _audioRoute = MutableStateFlow(CallAudioRoute.Earpiece)
    private val _connectionState = MutableStateFlow(CallMediaConnectionState.Idle)
    private val _localVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteParticipantConnected = MutableStateFlow(false)

    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    override val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()
    override val audioRoute: StateFlow<CallAudioRoute> = _audioRoute.asStateFlow()
    override val connectionState: StateFlow<CallMediaConnectionState> = _connectionState.asStateFlow()
    override val localVideoTrack: StateFlow<CallRenderableVideoTrack?> = _localVideoTrack.asStateFlow()
    override val remoteVideoTrack: StateFlow<CallRenderableVideoTrack?> = _remoteVideoTrack.asStateFlow()
    override val remoteParticipantConnected: StateFlow<Boolean> = _remoteParticipantConnected.asStateFlow()

    private var room: Room? = null
    private var eventsJob: Job? = null
    private var republishJob: Job? = null
    private var healthReportJob: Job? = null
    private var activeSessionId: String? = null
    private var videoEnabledForSession: Boolean = false

    override suspend fun prepareCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit> {
        return mutex.withLock {
            runCatching {
                if (activeSessionId == callSessionId && room?.state == Room.State.CONNECTED) {
                    return@runCatching
                }
                releaseInternal(resetAudio = true)
                connectRoom(
                    callSessionId = callSessionId,
                    videoEnabled = videoEnabled,
                    credentials = credentials,
                    reconnecting = false,
                    restoreMuted = false,
                    restoreCameraEnabled = videoEnabled,
                )
            }
        }
    }

    override suspend fun reconnectCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ): Result<Unit> {
        return mutex.withLock {
            runCatching {
                _connectionState.value = CallMediaConnectionState.Reconnecting
                if (room?.state == Room.State.CONNECTED && activeSessionId == callSessionId) {
                    applyMediaState(restoreMuted, restoreCameraEnabled, videoEnabled)
                    _connectionState.value = CallMediaConnectionState.Connected
                    CallDiagnostics.stage(stage = "CALL_MEDIA_RECONNECTED", callSessionId = callSessionId)
                    return@runCatching
                }
                releaseInternal(resetAudio = true)
                connectRoom(
                    callSessionId = callSessionId,
                    videoEnabled = videoEnabled,
                    credentials = credentials,
                    reconnecting = true,
                    restoreMuted = restoreMuted,
                    restoreCameraEnabled = restoreCameraEnabled,
                )
            }
        }
    }

    override suspend fun releaseCall(callSessionId: String): Result<Unit> {
        return mutex.withLock {
            runCatching {
                if (activeSessionId != null && activeSessionId != callSessionId) return@runCatching
                releaseInternal(resetAudio = true)
                CallDiagnostics.stage(stage = "CALL_ROOM_RELEASED", callSessionId = callSessionId)
                CallDiagnostics.stage(stage = "CALL_MEDIA_RELEASED", callSessionId = callSessionId)
            }
        }
    }

    override suspend fun setMuted(muted: Boolean): Result<Unit> {
        return mutex.withLock {
            runCatching {
                room?.localParticipant?.setMicrophoneEnabled(!muted)
                _isMuted.value = muted
                CallDiagnostics.stage(
                    stage = "CALL_MIC_STATE",
                    callSessionId = activeSessionId,
                    extra = mapOf("muted" to muted.toString()),
                )
            }
        }
    }

    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> {
        return mutex.withLock {
            runCatching {
                val participant = room?.localParticipant ?: error("No active room")
                val published = participant.setCameraEnabled(enabled)
                if (!published && enabled) {
                    CallDiagnostics.stage(
                        stage = "CALL_VIDEO_PUBLICATION_FAILED",
                        callSessionId = activeSessionId,
                        extra = mapOf("reason" to "setCameraEnabled returned false"),
                    )
                    error("Camera publication failed")
                }
                _isCameraEnabled.value = enabled
                refreshLocalVideoTrack()
                CallDiagnostics.stage(
                    stage = if (enabled) "CALL_CAMERA_ENABLED" else "CALL_CAMERA_DISABLED",
                    callSessionId = activeSessionId,
                )
                CallDiagnostics.stage(
                    stage = "CALL_CAMERA_STATE",
                    callSessionId = activeSessionId,
                    extra = mapOf("enabled" to enabled.toString()),
                )
                logLocalPublications(room ?: return@runCatching)
            }.onFailure { error ->
                CallDiagnostics.stage(
                    stage = "CALL_VIDEO_PUBLICATION_FAILED",
                    callSessionId = activeSessionId,
                    extra = mapOf(
                        "enabled" to enabled.toString(),
                        "error" to (error.message ?: error::class.simpleName.orEmpty()),
                        "stack" to error.stackTraceToString().take(500),
                    ),
                )
            }
        }
    }

    override suspend fun switchCamera(): Result<Unit> {
        return mutex.withLock {
            runCatching {
                CallDiagnostics.stage(stage = "CALL_CAMERA_SWITCH_REQUEST", callSessionId = activeSessionId)
                val localParticipant = room?.localParticipant ?: error("No active room")
                val publication = localParticipant.getTrackPublication(Track.Source.CAMERA)
                val track = publication?.track as? LocalVideoTrack
                    ?: error("No local camera track")
                track.switchCamera()
                refreshLocalVideoTrack()
                CallDiagnostics.stage(stage = "CALL_CAMERA_SWITCH_SUCCESS", callSessionId = activeSessionId)
                CallDiagnostics.stage(stage = "CALL_CAMERA_SWITCHED", callSessionId = activeSessionId)
            }.onFailure { error ->
                CallDiagnostics.stage(
                    stage = "CALL_CAMERA_SWITCH_FAILED",
                    callSessionId = activeSessionId,
                    extra = mapOf(
                        "error" to (error.message ?: error::class.simpleName.orEmpty()),
                        "stack" to error.stackTraceToString().take(500),
                    ),
                )
            }
        }
    }

    override suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit> {
        return mutex.withLock {
            runCatching {
                configureAudioRoute(route)
                CallDiagnostics.stage(
                    stage = "CALL_AUDIO_ROUTE",
                    callSessionId = activeSessionId,
                    extra = mapOf("route" to route.name),
                )
            }
        }
    }

    private suspend fun connectRoom(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        reconnecting: Boolean,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ) {
        _connectionState.value = if (reconnecting) {
            CallMediaConnectionState.Reconnecting
        } else {
            CallMediaConnectionState.Connecting
        }
        CallDiagnostics.stage(stage = "CALL_ROOM_CONNECTING", callSessionId = callSessionId)
        CallDiagnostics.stage(stage = "CALL_ROOM_JOIN", callSessionId = callSessionId)
        activeSessionId = callSessionId
        videoEnabledForSession = videoEnabled
        audioFocusManager.requestFocus()

        val livekitRoom = LiveKit.create(appContext)
        room = livekitRoom
        AndroidLiveKitRoomHolder.room = livekitRoom
        CallDiagnostics.stage(stage = "CALL_ROOM_CREATED", callSessionId = callSessionId)
        observeRoomEvents(livekitRoom)

        try {
            livekitRoom.connect(
                url = credentials.livekitUrl,
                token = credentials.providerToken,
            )
        } catch (error: Throwable) {
            CallDiagnostics.stage(
                stage = "CALL_ROOM_FAILED",
                callSessionId = callSessionId,
                extra = mapOf("error" to (error.message ?: error::class.simpleName.orEmpty())),
            )
            releaseInternal(resetAudio = true)
            throw error
        }

        CallDiagnostics.stage(stage = "CALL_ROOM_CONNECTED", callSessionId = callSessionId)
        CallMediaHealthProbe.reset()
        applyMediaState(restoreMuted, restoreCameraEnabled, videoEnabled)
        logRoomSnapshot(stage = "CALL_ROOM_POST_CONNECT", livekitRoom = livekitRoom, expectedRoomName = credentials.roomName)
        logLocalPublications(livekitRoom)
        attachExistingRemoteTracks(livekitRoom)
        logRoomSnapshot(stage = "CALL_ROOM_AFTER_ATTACH", livekitRoom = livekitRoom, expectedRoomName = credentials.roomName)
        configureAudioRoute(_audioRoute.value)
        _connectionState.value = CallMediaConnectionState.Connected
        CallDiagnostics.stage(
            stage = if (reconnecting) "CALL_MEDIA_RECONNECTED" else "CALL_MEDIA_PREPARED",
            callSessionId = callSessionId,
            extra = mapOf(
                "videoEnabled" to videoEnabled.toString(),
                "roomName" to credentials.roomName,
            ),
        )
        scheduleMediaHealthReport(callSessionId, videoEnabled)
    }

    private fun scheduleMediaHealthReport(callSessionId: String, videoCallExpected: Boolean) {
        if (!AppBuildConfig.IS_DEBUG) return
        healthReportJob?.cancel()
        healthReportJob = scope.launch {
            delay(MEDIA_HEALTH_REPORT_DELAY_MS)
            mutex.withLock {
                val livekitRoom = room ?: return@withLock
                if (activeSessionId != callSessionId) return@withLock
                val snapshot = buildMediaHealthSnapshot(livekitRoom, videoCallExpected)
                CallMediaHealthReport.emit(callSessionId, snapshot)
            }
        }
    }

    private fun buildMediaHealthSnapshot(
        livekitRoom: Room,
        videoCallExpected: Boolean,
    ): CallMediaHealthSnapshot {
        val local = livekitRoom.localParticipant
        val remoteIdentities = livekitRoom.remoteParticipants.values
            .map { it.identity?.value.orEmpty() }
            .filter { it.isNotBlank() }
        val publishedLocalTrackCount = local.trackPublications.size
        var subscribedRemoteTrackCount = 0
        livekitRoom.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values.forEach { publication ->
                if (publication.subscribed && publication.track != null) {
                    subscribedRemoteTrackCount++
                }
            }
        }
        val trackFramesDetected = CallMediaHealthProbe.hasRenderedVideoFrame()
        return CallMediaHealthSnapshot(
            participantCount = 1 + livekitRoom.remoteParticipants.size,
            localIdentity = local.identity?.value.orEmpty(),
            remoteIdentities = remoteIdentities,
            publishedLocalTrackCount = publishedLocalTrackCount,
            subscribedRemoteTrackCount = subscribedRemoteTrackCount,
            localAudioState = localTrackState(local, Track.Source.MICROPHONE),
            localVideoState = localTrackState(local, Track.Source.CAMERA),
            remoteAudioState = remoteTrackState(livekitRoom, Track.Kind.AUDIO),
            remoteVideoState = remoteTrackState(livekitRoom, Track.Kind.VIDEO),
            cameraCapturerState = cameraCapturerState(local),
            rendererAttached = CallMediaHealthProbe.rendererStatus(),
            videoFrameRendered = CallMediaHealthProbe.hasRenderedVideoFrame(),
            videoCallExpected = videoCallExpected,
            trackFramesDetected = trackFramesDetected,
        )
    }

    private fun localTrackState(local: LocalParticipant, source: Track.Source): String {
        val publication = local.getTrackPublication(source) ?: return "missing"
        return when {
            publication.track == null -> "missing"
            publication.muted -> "disabled"
            else -> "enabled"
        }
    }

    private fun remoteTrackState(livekitRoom: Room, kind: Track.Kind): String {
        var published = false
        var subscribed = false
        livekitRoom.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values
                .filter { it.kind == kind }
                .forEach { publication ->
                    published = true
                    if (publication.subscribed && publication.track != null) {
                        subscribed = true
                    }
                }
        }
        return when {
            subscribed -> "subscribed"
            published -> "published_not_subscribed"
            else -> "missing"
        }
    }

    private fun cameraCapturerState(local: LocalParticipant): String {
        val publication = local.getTrackPublication(Track.Source.CAMERA)
        val track = publication?.track as? LocalVideoTrack
        return when {
            track == null -> "stopped"
            publication.muted -> "disabled"
            else -> "running"
        }
    }

    private suspend fun applyMediaState(
        muted: Boolean,
        cameraEnabled: Boolean,
        videoEnabled: Boolean,
    ) {
        val participant = room?.localParticipant ?: return
        participant.setMicrophoneEnabled(!muted)
        if (videoEnabled) {
            CallDiagnostics.stage(stage = "CALL_CAMERA_PERMISSION_GRANTED", callSessionId = activeSessionId)
            try {
                val published = participant.setCameraEnabled(cameraEnabled)
                if (cameraEnabled) {
                    CallDiagnostics.stage(stage = "CALL_CAMERA_STARTED", callSessionId = activeSessionId)
                }
                if (cameraEnabled && !published) {
                    CallDiagnostics.stage(
                        stage = "CALL_VIDEO_PUBLICATION_FAILED",
                        callSessionId = activeSessionId,
                        extra = mapOf("reason" to "initial camera publish returned false"),
                    )
                } else if (cameraEnabled) {
                    CallDiagnostics.stage(
                        stage = "CALL_LOCAL_VIDEO_PUBLICATION_SUCCESS",
                        callSessionId = activeSessionId,
                    )
                }
            } catch (error: Throwable) {
                CallDiagnostics.stage(
                    stage = "CALL_VIDEO_PUBLICATION_FAILED",
                    callSessionId = activeSessionId,
                    extra = mapOf(
                        "error" to (error.message ?: error::class.simpleName.orEmpty()),
                        "stack" to error.stackTraceToString().take(500),
                    ),
                )
                throw error
            }
        } else {
            participant.setCameraEnabled(false)
        }
        _isMuted.value = muted
        _isCameraEnabled.value = videoEnabled && cameraEnabled
        refreshLocalVideoTrack()
        room?.let { logLocalPublications(it) }
    }

    private fun logRoomSnapshot(stage: String, livekitRoom: Room, expectedRoomName: String) {
        val localIdentity = livekitRoom.localParticipant.identity?.value.orEmpty()
        val remoteIdentities = livekitRoom.remoteParticipants.values
            .map { it.identity?.value.orEmpty() }
        val participantCount = 1 + livekitRoom.remoteParticipants.size
        val localVideoPub = livekitRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)
        val localAudioPub = livekitRoom.localParticipant.getTrackPublication(Track.Source.MICROPHONE)
        val publishedVideo = localVideoPub != null && !localVideoPub.muted
        val publishedAudio = localAudioPub != null && !localAudioPub.muted
        var subscribedVideo = false
        var subscribedAudio = false
        livekitRoom.remoteParticipants.values.forEach { remote ->
            remote.trackPublications.values.forEach { publication ->
                when (publication.kind) {
                    Track.Kind.VIDEO -> if (publication.subscribed) subscribedVideo = true
                    Track.Kind.AUDIO -> if (publication.subscribed) subscribedAudio = true
                    else -> Unit
                }
            }
        }
        CallDiagnostics.stage(
            stage = stage,
            callSessionId = activeSessionId,
            extra = mapOf(
                "roomName" to livekitRoom.name.orEmpty().ifBlank { expectedRoomName },
                "expectedRoomName" to expectedRoomName,
                "connectionState" to livekitRoom.state.name,
                "participantCount" to participantCount.toString(),
                "localIdentity" to localIdentity,
                "remoteIdentities" to remoteIdentities.joinToString(",").ifBlank { "none" },
                "publishedVideo" to publishedVideo.toString(),
                "publishedAudio" to publishedAudio.toString(),
                "subscribedVideo" to subscribedVideo.toString(),
                "subscribedAudio" to subscribedAudio.toString(),
            ),
        )
        if (stage == "CALL_ROOM_POST_CONNECT") {
            CallDiagnostics.stage(
                stage = "CALL_ROOM_STATE",
                callSessionId = activeSessionId,
                extra = mapOf(
                    "room" to livekitRoom.name.orEmpty().ifBlank { expectedRoomName },
                    "participants" to participantCount.toString(),
                    "local" to localIdentity,
                    "remote" to remoteIdentities.joinToString(",").ifBlank { "none" },
                    "publishedVideo" to publishedVideo.toString(),
                    "publishedAudio" to publishedAudio.toString(),
                    "subscribedVideo" to subscribedVideo.toString(),
                    "subscribedAudio" to subscribedAudio.toString(),
                ),
            )
        }
    }

    private fun logLocalPublications(livekitRoom: Room) {
        livekitRoom.localParticipant.trackPublications.values.forEach { publication ->
            val sid = publication.sid?.toString().orEmpty()
            val kind = publication.kind.name
            val source = publication.source.name
            val stage = when (publication.kind) {
                Track.Kind.AUDIO -> "CALL_LOCAL_AUDIO_PUBLISHED"
                Track.Kind.VIDEO -> "CALL_LOCAL_VIDEO_PUBLISHED"
                else -> "CALL_LOCAL_TRACK_PUBLISHED"
            }
            CallDiagnostics.stage(
                stage = stage,
                callSessionId = activeSessionId,
                extra = mapOf(
                    "publicationSid" to sid,
                    "source" to source,
                    "kind" to kind,
                    "muted" to publication.muted.toString(),
                ),
            )
        }
    }

    private fun publicationExtras(publication: io.livekit.android.room.track.TrackPublication): Map<String, String> {
        return mapOf(
            "publicationSid" to publication.sid?.toString().orEmpty(),
            "source" to publication.source.name,
            "kind" to publication.kind.name,
            "muted" to publication.muted.toString(),
        )
    }

    private fun isRemoteParticipant(participant: io.livekit.android.room.participant.Participant): Boolean {
        return participant !is io.livekit.android.room.participant.LocalParticipant
    }

    private fun observeRoomEvents(livekitRoom: Room) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            livekitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        if (isRemoteParticipant(event.participant)) {
                            _remoteParticipantConnected.value = true
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_PARTICIPANT_JOINED",
                                callSessionId = activeSessionId,
                                extra = mapOf(
                                    "identity" to event.participant.identity?.value.orEmpty(),
                                ),
                            )
                            logRoomSnapshot(
                                stage = "CALL_ROOM_PARTICIPANT_JOINED",
                                livekitRoom = livekitRoom,
                                expectedRoomName = "",
                            )
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        if (isRemoteParticipant(event.participant)) {
                            _remoteParticipantConnected.value = livekitRoom.remoteParticipants.isNotEmpty()
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_PARTICIPANT_LEFT",
                                callSessionId = activeSessionId,
                            )
                            if (livekitRoom.remoteParticipants.isEmpty()) {
                                _remoteVideoTrack.value = null
                            }
                        }
                    }
                    is RoomEvent.TrackPublished -> {
                        val isLocal = event.participant is io.livekit.android.room.participant.LocalParticipant
                        val stage = when (event.publication.kind) {
                            Track.Kind.VIDEO -> if (isLocal) "CALL_LOCAL_VIDEO_PUBLISHED" else "CALL_REMOTE_VIDEO_PUBLISHED"
                            Track.Kind.AUDIO -> if (isLocal) "CALL_LOCAL_AUDIO_PUBLISHED" else "CALL_REMOTE_AUDIO_PUBLISHED"
                            else -> if (isLocal) "CALL_LOCAL_TRACK_PUBLISHED" else "CALL_REMOTE_TRACK_PUBLISHED"
                        }
                        CallDiagnostics.stage(
                            stage = stage,
                            callSessionId = activeSessionId,
                            extra = buildMap {
                                put("identity", event.participant.identity?.value.orEmpty())
                                putAll(publicationExtras(event.publication))
                            },
                        )
                        if (isLocal) {
                            if (event.publication.kind == Track.Kind.VIDEO) {
                                CallDiagnostics.stage(stage = "CALL_LOCAL_VIDEO_CREATED", callSessionId = activeSessionId)
                                CallDiagnostics.stage(
                                    stage = "CALL_LOCAL_VIDEO_PUBLICATION_SUCCESS",
                                    callSessionId = activeSessionId,
                                    extra = publicationExtras(event.publication),
                                )
                                refreshLocalVideoTrack()
                            } else if (event.publication.kind == Track.Kind.AUDIO) {
                                CallDiagnostics.stage(stage = "CALL_LOCAL_AUDIO_CREATED", callSessionId = activeSessionId)
                            }
                        }
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (isRemoteParticipant(event.participant) && track is VideoTrack) {
                            _remoteVideoTrack.value = track.asRenderable()
                            _remoteParticipantConnected.value = true
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_VIDEO_TRACK_SUBSCRIBED",
                                callSessionId = activeSessionId,
                                extra = mapOf(
                                    "identity" to event.participant.identity?.value.orEmpty(),
                                    "trackSid" to track.sid?.toString().orEmpty(),
                                ),
                            )
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_VIDEO_TRACK_ASSIGNED",
                                callSessionId = activeSessionId,
                                extra = mapOf("trackSid" to track.sid?.toString().orEmpty()),
                            )
                            CallDiagnostics.stage(
                                stage = "CALL_FIRST_REMOTE_VIDEO_FRAME",
                                callSessionId = activeSessionId,
                                extra = mapOf("trackSid" to track.sid?.toString().orEmpty()),
                            )
                        } else if (isRemoteParticipant(event.participant) && track.kind == Track.Kind.AUDIO) {
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_AUDIO_SUBSCRIBED",
                                callSessionId = activeSessionId,
                                extra = mapOf(
                                    "identity" to event.participant.identity?.value.orEmpty(),
                                    "trackSid" to track.sid?.toString().orEmpty(),
                                ),
                            )
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        if (isRemoteParticipant(event.participant) && event.track is VideoTrack) {
                            _remoteVideoTrack.value = null
                            CallDiagnostics.stage(
                                stage = "CALL_REMOTE_VIDEO_UNSUBSCRIBED",
                                callSessionId = activeSessionId,
                            )
                        }
                    }
                    is RoomEvent.TrackMuted -> {
                        val stage = if (event.publication.kind == Track.Kind.VIDEO) {
                            if (isRemoteParticipant(event.participant)) {
                                _remoteVideoTrack.value = null
                                "CALL_REMOTE_VIDEO_MUTED"
                            } else {
                                _localVideoTrack.value = null
                                "CALL_LOCAL_VIDEO_MUTED"
                            }
                        } else {
                            if (isRemoteParticipant(event.participant)) "CALL_REMOTE_AUDIO_MUTED"
                            else "CALL_LOCAL_AUDIO_MUTED"
                        }
                        CallDiagnostics.stage(stage = stage, callSessionId = activeSessionId)
                    }
                    is RoomEvent.TrackUnmuted -> {
                        val stage = if (event.publication.kind == Track.Kind.VIDEO) {
                            if (isRemoteParticipant(event.participant)) {
                                (event.publication.track as? VideoTrack)?.let {
                                    _remoteVideoTrack.value = it.asRenderable()
                                }
                                "CALL_REMOTE_VIDEO_UNMUTED"
                            } else {
                                refreshLocalVideoTrack()
                                "CALL_LOCAL_VIDEO_UNMUTED"
                            }
                        } else {
                            if (isRemoteParticipant(event.participant)) "CALL_REMOTE_AUDIO_UNMUTED"
                            else "CALL_LOCAL_AUDIO_UNMUTED"
                        }
                        CallDiagnostics.stage(stage = stage, callSessionId = activeSessionId)
                    }
                    is RoomEvent.ConnectionQualityChanged -> {
                        _connectionState.value = when (event.quality) {
                            io.livekit.android.room.participant.ConnectionQuality.POOR ->
                                CallMediaConnectionState.PoorConnection
                            else -> CallMediaConnectionState.Connected
                        }
                    }
                    is RoomEvent.Disconnected -> {
                        _connectionState.value = CallMediaConnectionState.Disconnected
                        CallDiagnostics.stage(
                            stage = "CALL_ROOM_DISCONNECTED",
                            callSessionId = activeSessionId,
                            extra = mapOf("reason" to event.reason.toString()),
                        )
                    }
                    is RoomEvent.Reconnecting -> {
                        _connectionState.value = CallMediaConnectionState.Reconnecting
                        CallDiagnostics.stage(stage = "CALL_ROOM_RECONNECTING", callSessionId = activeSessionId)
                    }
                    is RoomEvent.Reconnected -> {
                        _connectionState.value = CallMediaConnectionState.Connected
                        CallDiagnostics.stage(stage = "CALL_ROOM_RECONNECTED", callSessionId = activeSessionId)
                        republishJob?.cancel()
                        republishJob = scope.launch {
                            mutex.withLock {
                                if (room !== livekitRoom || activeSessionId == null) return@withLock
                                applyMediaState(
                                    muted = _isMuted.value,
                                    cameraEnabled = _isCameraEnabled.value,
                                    videoEnabled = videoEnabledForSession,
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun attachExistingRemoteTracks(livekitRoom: Room) {
        livekitRoom.remoteParticipants.values.forEach { participant ->
            participant.trackPublications.values.forEach { publication ->
                when (publication.kind) {
                    Track.Kind.VIDEO -> {
                        val track = publication.track as? VideoTrack ?: return@forEach
                        _remoteVideoTrack.value = track.asRenderable()
                        _remoteParticipantConnected.value = true
                        CallDiagnostics.stage(
                            stage = "CALL_REMOTE_VIDEO_TRACK_ASSIGNED",
                            callSessionId = activeSessionId,
                            extra = mapOf(
                                "publicationSid" to publication.sid?.toString().orEmpty(),
                                "subscribed" to publication.subscribed.toString(),
                                "trackSid" to track.sid?.toString().orEmpty(),
                            ),
                        )
                    }
                    Track.Kind.AUDIO -> {
                        CallDiagnostics.stage(
                            stage = "CALL_REMOTE_AUDIO",
                            callSessionId = activeSessionId,
                            extra = mapOf(
                                "publicationSid" to publication.sid?.toString().orEmpty(),
                                "subscribed" to publication.subscribed.toString(),
                            ),
                        )
                        _remoteParticipantConnected.value = true
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun refreshLocalVideoTrack() {
        val track = room?.localParticipant
            ?.getTrackPublication(Track.Source.CAMERA)
            ?.track as? VideoTrack
        _localVideoTrack.value = track?.asRenderable()
        if (track != null) {
            CallDiagnostics.stage(
                stage = "CALL_LOCAL_VIDEO_FRAME",
                callSessionId = activeSessionId,
                extra = mapOf("trackSid" to track.sid?.toString().orEmpty()),
            )
        }
    }

    private fun configureAudioRoute(route: CallAudioRoute) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        when (route) {
            CallAudioRoute.Speaker -> audioManager.isSpeakerphoneOn = true
            CallAudioRoute.Earpiece -> audioManager.isSpeakerphoneOn = false
            CallAudioRoute.Bluetooth,
            CallAudioRoute.WiredHeadset,
            -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val targetType = when (route) {
                        CallAudioRoute.Bluetooth -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        else -> AudioDeviceInfo.TYPE_WIRED_HEADSET
                    }
                    audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == targetType }
                        ?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION")
                    if (route == CallAudioRoute.Bluetooth) {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                }
            }
        }
        _audioRoute.value = route
        CallDiagnostics.stage(
            stage = "CALL_AUDIO_ROUTE",
            callSessionId = activeSessionId,
            extra = mapOf(
                "route" to route.name,
                "speakerphoneOn" to audioManager.isSpeakerphoneOn.toString(),
                "mode" to audioManager.mode.toString(),
            ),
        )
    }

    private fun resetAudioMode() {
        audioFocusManager.abandonFocus()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }
    }

    private fun releaseInternal(resetAudio: Boolean = false) {
        healthReportJob?.cancel()
        healthReportJob = null
        CallMediaHealthProbe.reset()
        republishJob?.cancel()
        republishJob = null
        eventsJob?.cancel()
        eventsJob = null
        runCatching { room?.disconnect() }
        room?.release()
        room = null
        AndroidLiveKitRoomHolder.room = null
        activeSessionId = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _remoteParticipantConnected.value = false
        _connectionState.value = CallMediaConnectionState.Idle
        _isMuted.value = false
        _isCameraEnabled.value = false
        _audioRoute.value = CallAudioRoute.Earpiece
        if (resetAudio) {
            resetAudioMode()
        }
    }

    private companion object {
        private const val MEDIA_HEALTH_REPORT_DELAY_MS = 3_000L
    }
}
