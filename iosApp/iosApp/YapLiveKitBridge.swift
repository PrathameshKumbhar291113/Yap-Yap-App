import AVFoundation
import Foundation
import LiveKit
import UIKit
import Shared

final class YapLiveKitBridge: NSObject, IosLiveKitSwiftHandler {
    static let shared = YapLiveKitBridge()

    private(set) var room: Room?
    private var remoteVideoTrack: VideoTrack?
    private(set) var activeSessionId: String?
    private var isDelegateRegistered = false
    private var connectTask: Task<Void, Never>?
    private var healthReportTask: Task<Void, Never>?

    private static let mediaHealthReportDelayNs: UInt64 = 3_000_000_000

    private override init() {
        super.init()
    }

    static func install() {
        IosLiveKitBridgeRegistryKt.registerIosLiveKitHandler(handler: YapLiveKitBridge.shared)
        IosLiveKitBridgeRegistryKt.registerIosLiveKitVideoViewFactory { trackId, mirror, fit in
            YapLiveKitVideoViewController(
                trackId: trackId,
                mirror: mirror.boolValue,
                fit: fit,
            )
        }
    }

    func connect(request: IosLiveKitBridge.ConnectRequest) {
        connectTask?.cancel()
        connectTask = Task { @MainActor in
            do {
                await cleanupRoom(logStage: false)
                activeSessionId = request.callSessionId
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: request.reconnecting ? "CALL_ROOM_RECONNECTING" : "CALL_ROOM_CONNECTING",
                    callSessionId: request.callSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectionState(
                    raw: request.reconnecting ? "reconnecting" : "connecting",
                )
                try AVAudioSession.sharedInstance().setCategory(
                    .playAndRecord,
                    mode: .voiceChat,
                    options: [.allowBluetooth, .defaultToSpeaker],
                )
                try AVAudioSession.sharedInstance().setActive(true)
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(stage: "CALL_AUDIOFOCUS_REQUESTED", callSessionId: request.callSessionId)
                let livekitRoom = Room()
                room = livekitRoom
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_ROOM_CREATED",
                    callSessionId: request.callSessionId,
                )
                if !isDelegateRegistered {
                    livekitRoom.add(delegate: self)
                    isDelegateRegistered = true
                }
                try Task.checkCancellation()
                try await livekitRoom.connect(url: request.livekitUrl, token: request.providerToken)
                try Task.checkCancellation()
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_ROOM_CONNECTED",
                    callSessionId: request.callSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeReset()
                try await livekitRoom.localParticipant.setMicrophone(enabled: !request.restoreMuted)
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_LOCAL_AUDIO_CREATED",
                    callSessionId: request.callSessionId,
                )
                if request.videoEnabled && request.restoreCameraEnabled {
                    try await livekitRoom.localParticipant.setCamera(enabled: true)
                    IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                        stage: "CALL_CAMERA_STARTED",
                        callSessionId: request.callSessionId,
                    )
                } else {
                    try await livekitRoom.localParticipant.setCamera(enabled: false)
                }
                publishTrackIds(room: livekitRoom)
                logRoomSnapshot(stage: "CALL_ROOM_POST_CONNECT", room: livekitRoom, callSessionId: request.callSessionId)
                IosLiveKitBridgeRegistryKt.iosLiveKitOnMutedChanged(muted: request.restoreMuted)
                IosLiveKitBridgeRegistryKt.iosLiveKitOnCameraEnabledChanged(
                    enabled: request.videoEnabled && request.restoreCameraEnabled,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectionState(raw: "connected")
                IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectCompleted(success: true, errorMessage: nil)
                scheduleMediaHealthReport(
                    callSessionId: request.callSessionId,
                    videoCallExpected: request.videoEnabled,
                )
            } catch is CancellationError {
                await cleanupRoom(sessionId: request.callSessionId, logStage: true)
                IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectCompleted(
                    success: false,
                    errorMessage: "cancelled",
                )
            } catch {
                await cleanupRoom(sessionId: request.callSessionId, logStage: true)
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_ROOM_FAILED",
                    callSessionId: request.callSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectCompleted(
                    success: false,
                    errorMessage: error.localizedDescription,
                )
            }
        }
    }

    func disconnect(callSessionId: String) {
        connectTask?.cancel()
        connectTask = Task { @MainActor in
            guard activeSessionId == nil || activeSessionId == callSessionId else { return }
            await cleanupRoom(sessionId: callSessionId, logStage: true)
        }
    }

    func disconnectAll() {
        connectTask?.cancel()
        connectTask = Task { @MainActor in
            await cleanupRoom(logStage: true)
        }
    }

    func setMuted(muted: Bool) {
        Task { @MainActor in
            try? await room?.localParticipant.setMicrophone(enabled: !muted)
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: muted ? "CALL_MIC_MUTED" : "CALL_MIC_UNMUTED",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitOnMutedChanged(muted: muted)
        }
    }

    func setCameraEnabled(enabled: Bool) {
        Task { @MainActor in
            do {
                try await room?.localParticipant.setCamera(enabled: enabled)
                publishTrackIds(room: room)
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: enabled ? "CALL_CAMERA_ENABLED" : "CALL_CAMERA_DISABLED",
                    callSessionId: activeSessionId,
                )
                if enabled {
                    IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                        stage: "CALL_CAMERA_STARTED",
                        callSessionId: activeSessionId,
                    )
                    IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                        stage: "CALL_LOCAL_VIDEO_PUBLICATION_SUCCESS",
                        callSessionId: activeSessionId,
                    )
                }
                IosLiveKitBridgeRegistryKt.iosLiveKitOnCameraEnabledChanged(enabled: enabled)
            } catch {
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_VIDEO_PUBLICATION_FAILED",
                    callSessionId: activeSessionId,
                )
            }
        }
    }

    func switchCamera() {
        Task { @MainActor in
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_CAMERA_SWITCH_REQUEST",
                callSessionId: activeSessionId,
            )
            guard let cameraTrack = room?.localParticipant.firstCameraVideoTrack as? LocalVideoTrack,
                  let capturer = cameraTrack.capturer as? CameraCapturer else {
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_CAMERA_SWITCH_FAILED",
                    callSessionId: activeSessionId,
                )
                return
            }
            do {
                try await capturer.switchCameraPosition()
                publishTrackIds(room: room)
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_CAMERA_SWITCH_SUCCESS",
                    callSessionId: activeSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_CAMERA_SWITCHED",
                    callSessionId: activeSessionId,
                )
            } catch {
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_CAMERA_SWITCH_FAILED",
                    callSessionId: activeSessionId,
                )
            }
        }
    }

    @MainActor
    private func cleanupRoom(sessionId: String? = nil, logStage: Bool) async {
        healthReportTask?.cancel()
        healthReportTask = nil
        IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeReset()
        let endedSessionId = sessionId ?? activeSessionId
        if let livekitRoom = room {
            if isDelegateRegistered {
                livekitRoom.remove(delegate: self)
                isDelegateRegistered = false
            }
            try? await livekitRoom.localParticipant.setCamera(enabled: false)
            try? await livekitRoom.localParticipant.setMicrophone(enabled: false)
            try? await livekitRoom.disconnect()
        }
        room = nil
        remoteVideoTrack = nil
        activeSessionId = nil
        IosLiveKitBridgeRegistryKt.iosLiveKitOnLocalTrackChanged(trackId: nil)
        IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: nil)
        IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteParticipantConnected(connected: false)
        IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectionState(raw: "disconnected")
        deactivateAudioSession()
        if logStage {
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_ROOM_DISCONNECTED",
                callSessionId: endedSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_ROOM_RELEASED",
                callSessionId: endedSessionId,
            )
        }
    }

    @MainActor
    private func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: .notifyOthersOnDeactivation,
        )
        IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(stage: "CALL_AUDIOFOCUS_RELEASED", callSessionId: activeSessionId)
    }

    @MainActor
    private func logRoomSnapshot(stage: String, room: Room, callSessionId: String?) {
        let localIdentity = room.localParticipant.identity?.stringValue ?? ""
        let remoteIdentities = room.remoteParticipants.values
            .map { $0.identity?.stringValue ?? "" }
            .joined(separator: ",")
        let participantCount = 1 + room.remoteParticipants.count
        IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
            stage: "\(stage) participantCount=\(participantCount) localIdentity=\(localIdentity) remoteIdentities=\(remoteIdentities.isEmpty ? "none" : remoteIdentities) connectionState=\(room.connectionState)",
            callSessionId: callSessionId,
        )
    }

    @MainActor
    private func publishTrackIds(room: Room?) {
        let localId = room?.localParticipant.firstCameraPublication?.track?.sid?.stringValue
        if localId != nil {
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_LOCAL_VIDEO_CREATED",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_LOCAL_VIDEO_PUBLISHED",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_LOCAL_VIDEO_PUBLICATION_SUCCESS",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_LOCAL_VIDEO_FRAME",
                callSessionId: activeSessionId,
            )
        }
        IosLiveKitBridgeRegistryKt.iosLiveKitOnLocalTrackChanged(trackId: localId)
        let remoteId = remoteVideoTrack?.sid?.stringValue
        IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: remoteId)
    }

    @MainActor
    private func scheduleMediaHealthReport(callSessionId: String, videoCallExpected: Bool) {
#if DEBUG
        healthReportTask?.cancel()
        healthReportTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.mediaHealthReportDelayNs)
            guard !Task.isCancelled, activeSessionId == callSessionId else { return }
            emitMediaHealthReport(callSessionId: callSessionId, videoCallExpected: videoCallExpected)
        }
#endif
    }

    @MainActor
    private func emitMediaHealthReport(callSessionId: String, videoCallExpected: Bool) {
        guard let livekitRoom = room else { return }
        let local = livekitRoom.localParticipant
        let remoteIdentities = livekitRoom.remoteParticipants.values
            .compactMap { $0.identity?.stringValue }
            .filter { !$0.isEmpty }
        let publishedLocalTracks = local.trackPublications.count
        var subscribedRemoteTracks = 0
        var trackFramesDetected = false
        for participant in livekitRoom.remoteParticipants.values {
            for publication in participant.trackPublications.values {
                if publication.isSubscribed, publication.track != nil {
                    subscribedRemoteTracks += 1
                }
                if let videoTrack = publication.track as? VideoTrack, videoTrackHasFrames(videoTrack) {
                    trackFramesDetected = true
                }
            }
        }
        if let localVideo = local.firstCameraPublication?.track as? VideoTrack, videoTrackHasFrames(localVideo) {
            trackFramesDetected = true
        }
        IosLiveKitBridgeRegistryKt.iosLiveKitEmitMediaHealthReport(
            callSessionId: callSessionId,
            participantCount: Int32(1 + livekitRoom.remoteParticipants.count),
            localIdentity: local.identity?.stringValue ?? "",
            remoteIdentities: remoteIdentities.joined(separator: ","),
            publishedLocalTracks: Int32(publishedLocalTracks),
            subscribedRemoteTracks: Int32(subscribedRemoteTracks),
            localAudioState: localTrackState(participant: local, source: .microphone),
            localVideoState: localTrackState(participant: local, source: .camera),
            remoteAudioState: remoteTrackState(room: livekitRoom, kind: .audio),
            remoteVideoState: remoteTrackState(room: livekitRoom, kind: .video),
            cameraCapturerState: cameraCapturerState(participant: local),
            rendererAttached: IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeRendererStatus(),
            videoFrameRendered: IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeHasRenderedFrame(),
            videoCallExpected: videoCallExpected,
            trackFramesDetected: trackFramesDetected
        )
    }

    private func localTrackState(participant: LocalParticipant, source: Track.Source) -> String {
        let publication: TrackPublication?
        switch source {
        case .camera:
            publication = participant.firstCameraPublication
        case .microphone:
            publication = participant.firstAudioPublication
        default:
            publication = nil
        }
        guard let publication else { return "missing" }
        if publication.track == nil { return "missing" }
        return publication.isMuted ? "disabled" : "enabled"
    }

    private func remoteTrackState(room: Room, kind: Track.Kind) -> String {
        var published = false
        var subscribed = false
        for participant in room.remoteParticipants.values {
            for publication in participant.trackPublications.values where publication.kind == kind {
                published = true
                if publication.isSubscribed, publication.track != nil {
                    subscribed = true
                }
            }
        }
        if subscribed { return "subscribed" }
        if published { return "published_not_subscribed" }
        return "missing"
    }

    private func cameraCapturerState(participant: LocalParticipant) -> String {
        guard let publication = participant.firstCameraPublication else { return "stopped" }
        if publication.track == nil { return "stopped" }
        return publication.isMuted ? "disabled" : "running"
    }

    private func videoTrackHasFrames(_ track: VideoTrack) -> Bool {
        guard let dimensions = track.dimensions else { return false }
        return dimensions.width > 0 && dimensions.height > 0
    }
}

private func yapVideoTrackHasFrames(_ track: VideoTrack) -> Bool {
    guard let dimensions = track.dimensions else { return false }
    return dimensions.width > 0 && dimensions.height > 0
}

extension YapLiveKitBridge: RoomDelegate {
    nonisolated func room(_ room: Room, participantDidConnect participant: RemoteParticipant) {
        Task { @MainActor in
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_REMOTE_PARTICIPANT_JOINED",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteParticipantConnected(connected: true)
        }
    }

    nonisolated func room(_ room: Room, participantDidDisconnect participant: RemoteParticipant) {
        Task { @MainActor in
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_REMOTE_PARTICIPANT_LEFT",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteParticipantConnected(
                connected: !room.remoteParticipants.isEmpty,
            )
        }
    }

    nonisolated func room(
        _ room: Room,
        participant: RemoteParticipant,
        didSubscribeTrack publication: RemoteTrackPublication,
    ) {
        Task { @MainActor in
            if publication.kind == .video, let track = publication.track as? VideoTrack {
                remoteVideoTrack = track
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_REMOTE_VIDEO_TRACK_SUBSCRIBED",
                    callSessionId: activeSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_REMOTE_VIDEO_TRACK_ASSIGNED",
                    callSessionId: activeSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_FIRST_REMOTE_VIDEO_FRAME",
                    callSessionId: activeSessionId,
                )
                IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: track.sid?.stringValue)
            } else if publication.kind == .audio {
                IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                    stage: "CALL_REMOTE_AUDIO_TRACK",
                    callSessionId: activeSessionId,
                )
            }
            IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteParticipantConnected(connected: true)
        }
    }

    nonisolated func room(
        _ room: Room,
        participant: RemoteParticipant,
        didUnsubscribeTrack publication: RemoteTrackPublication,
    ) {
        guard publication.kind == .video else { return }
        Task { @MainActor in
            remoteVideoTrack = nil
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_REMOTE_VIDEO_UNSUBSCRIBED",
                callSessionId: activeSessionId,
            )
            IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: nil)
        }
    }

    nonisolated func room(
        _ room: Room,
        participant: Participant,
        trackPublication: TrackPublication,
        didUpdateIsMuted isMuted: Bool,
    ) {
        Task { @MainActor in
            let prefix = participant is LocalParticipant ? "CALL_LOCAL" : "CALL_REMOTE"
            let kind = trackPublication.kind == .video ? "VIDEO" : "AUDIO"
            let stage = isMuted ? "\(prefix)_\(kind)_MUTED" : "\(prefix)_\(kind)_UNMUTED"
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(stage: stage, callSessionId: activeSessionId)
            if trackPublication.kind == .video {
                if participant is RemoteParticipant {
                    if isMuted {
                        remoteVideoTrack = nil
                        IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: nil)
                    } else if let track = trackPublication.track as? VideoTrack {
                        remoteVideoTrack = track
                        IosLiveKitBridgeRegistryKt.iosLiveKitOnRemoteTrackChanged(trackId: track.sid?.stringValue)
                    }
                } else if isMuted {
                    IosLiveKitBridgeRegistryKt.iosLiveKitOnLocalTrackChanged(trackId: nil)
                } else {
                    publishTrackIds(room: room)
                }
            }
        }
    }

    nonisolated func room(
        _ room: Room,
        didUpdateConnectionState connectionState: ConnectionState,
        from oldValue: ConnectionState,
    ) {
        let rawState: String
        let stage: String
        switch connectionState {
        case .connected:
            rawState = "connected"
            stage = "CALL_ROOM_CONNECTED"
        case .reconnecting:
            rawState = "reconnecting"
            stage = "CALL_ROOM_RECONNECTING"
        case .disconnected:
            rawState = "disconnected"
            stage = "CALL_ROOM_DISCONNECTED"
        default:
            rawState = "connecting"
            stage = "CALL_ROOM_CONNECTING"
        }
        Task { @MainActor in
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(stage: stage, callSessionId: activeSessionId)
            IosLiveKitBridgeRegistryKt.iosLiveKitOnConnectionState(raw: rawState)
            if connectionState == .connected && oldValue == .reconnecting {
                publishTrackIds(room: room)
            }
        }
    }
}

final class YapLiveKitVideoViewController: UIViewController {
    private let trackId: String?
    private let mirror: Bool
    private let fit: String
    private var videoView: VideoView?

    init(trackId: String?, mirror: Bool, fit: String) {
        self.trackId = trackId
        self.mirror = mirror
        self.fit = fit
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { nil }

    deinit {
        videoView?.track = nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let videoView = VideoView()
        videoView.layoutMode = fit == "Fit" ? .fit : .fill
        videoView.mirrorMode = mirror ? .mirror : .off
        videoView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(videoView)
        NSLayoutConstraint.activate([
            videoView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            videoView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            videoView.topAnchor.constraint(equalTo: view.topAnchor),
            videoView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        self.videoView = videoView
        IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
            stage: "CALL_RENDERER_CREATED",
            callSessionId: YapLiveKitBridge.shared.activeSessionId
        )
        bindTrack()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        bindTrack()
    }

    private func bindTrack() {
        guard let room = YapLiveKitBridge.shared.room else {
            videoView?.track = nil
            return
        }
        if trackId == room.localParticipant.firstCameraPublication?.track?.sid?.stringValue,
           let track = room.localParticipant.firstCameraPublication?.track as? VideoTrack {
            videoView?.track = track
            IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeRendererAttached(isLocal: true)
            if yapVideoTrackHasFrames(track) {
                IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeFirstVideoFrame()
            }
            IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                stage: "CALL_RENDERER_ATTACHED",
                callSessionId: YapLiveKitBridge.shared.activeSessionId
            )
            return
        }
        for participant in room.remoteParticipants.values {
            for publication in participant.videoTracks {
                if publication.track?.sid?.stringValue == trackId,
                   let track = publication.track as? VideoTrack {
                    videoView?.track = track
                    IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeRendererAttached(isLocal: false)
                    if yapVideoTrackHasFrames(track) {
                        IosLiveKitBridgeRegistryKt.iosLiveKitMediaHealthProbeFirstVideoFrame()
                    }
                    IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(
                        stage: "CALL_RENDERER_ATTACHED",
                        callSessionId: YapLiveKitBridge.shared.activeSessionId
                    )
                    return
                }
            }
        }
        videoView?.track = nil
    }
}
