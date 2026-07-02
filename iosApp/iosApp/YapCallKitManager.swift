import AVFoundation
import CallKit
import Foundation
import Shared
import UIKit

final class YapCallKitManager: NSObject {
    static let shared = YapCallKitManager()

    private let provider: CXProvider
    private let callController = CXCallController()
    private var sessionByUuid = [UUID: String]()
    private var uuidBySession = [String: UUID]()

    private override init() {
        let config = CXProviderConfiguration(localizedName: "YapYap")
        config.supportsVideo = true
        config.maximumCallsPerCallGroup = 1
        config.supportedHandleTypes = [.generic]
        config.includesCallsInRecents = false
        if #available(iOS 14.0, *) {
            config.supportsVideo = true
        }
        provider = CXProvider(configuration: config)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    static func install() {
        if YapProvisioningCapabilities.isCallKitAvailable {
            _ = YapCallKitManager.shared
            IosCallNativeBridgeRegistryKt.registerIosCallNativeHandler(handler: YapCallKitBridge())
            print("[YapCallKit] enabled")
        } else {
            IosCallNativeBridgeRegistryKt.registerIosCallNativeHandler(handler: YapCallKitNoOpBridge())
            print("[YapCallKit] disabled — missing CallKit entitlements or Debug Personal Team configuration")
        }
    }

    private var activeSessions = Set<String>()

    func reportIncoming(
        callSessionId: String,
        callerDisplayName: String,
        isVideoCall: Bool,
    ) {
        let uuid = uuidForSession(callSessionId)
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: callerDisplayName)
        update.localizedCallerName = callerDisplayName
        update.hasVideo = isVideoCall
        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error {
                print("[YapCallKit] report incoming failed: \(error.localizedDescription)")
            }
        }
    }

    func setActive(callSessionId: String) {
        activeSessions.insert(callSessionId)
        guard let uuid = uuidBySession[callSessionId] else { return }
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    func dismiss(callSessionId: String) {
        activeSessions.remove(callSessionId)
        guard let uuid = uuidBySession.removeValue(forKey: callSessionId) else { return }
        sessionByUuid.removeValue(forKey: uuid)
        let end = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: end)
        callController.request(transaction) { _ in }
    }

    func showMissed(callSessionId: String, callerDisplayName: String) {
        dismiss(callSessionId: callSessionId)
    }

    private func uuidForSession(_ callSessionId: String) -> UUID {
        if let existing = uuidBySession[callSessionId] { return existing }
        let uuid = UUID()
        uuidBySession[callSessionId] = uuid
        sessionByUuid[uuid] = callSessionId
        return uuid
    }

    private func sessionId(for uuid: UUID) -> String? {
        sessionByUuid[uuid]
    }
}

extension YapCallKitManager: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        IosLiveKitBridgeRegistryKt.iosLiveKitDisconnectAll()
        activeSessions.removeAll()
        sessionByUuid.removeAll()
        uuidBySession.removeAll()
        IosLiveKitBridgeRegistryKt.iosLiveKitLogStage(stage: "CALL_NATIVE_CALL_ENDED", callSessionId: nil)
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        guard let sessionId = sessionId(for: action.callUUID) else {
            action.fail()
            return
        }
        IosCallNativeBridgeRegistryKt.iosCallNativeOnAnswer(callSessionId: sessionId)
        action.fulfill()
        openAppForCall(sessionId: sessionId)
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        guard let sessionId = sessionId(for: action.callUUID) else {
            action.fulfill()
            return
        }
        if activeSessions.contains(sessionId) {
            IosCallNativeBridgeRegistryKt.iosCallNativeOnEnd(callSessionId: sessionId)
        } else {
            IosCallNativeBridgeRegistryKt.iosCallNativeOnReject(callSessionId: sessionId)
        }
        sessionByUuid.removeValue(forKey: action.callUUID)
        uuidBySession.removeValue(forKey: sessionId)
        activeSessions.remove(sessionId)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        action.fulfill()
    }

    private func openAppForCall(sessionId: String) {
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let window = scene.windows.first,
                  let root = window.rootViewController else { return }
            root.presentedViewController?.dismiss(animated: false)
            NotificationCenter.default.post(
                name: NSNotification.Name("YapOpenCall"),
                object: nil,
                userInfo: ["callSessionId": sessionId],
            )
        }
    }
}

final class YapCallKitNoOpBridge: NSObject, IosCallNativeSwiftHandler {
    func showIncoming(callSessionId: String, callerDisplayName: String, isVideoCall: Bool) {}

    func setActive(callSessionId: String, isVideoCall: Bool) {}

    func dismiss(callSessionId: String) {}

    func showMissed(callSessionId: String, callerDisplayName: String) {}
}

final class YapCallKitBridge: NSObject, IosCallNativeSwiftHandler {
    func showIncoming(callSessionId: String, callerDisplayName: String, isVideoCall: Bool) {
        YapCallKitManager.shared.reportIncoming(
            callSessionId: callSessionId,
            callerDisplayName: callerDisplayName,
            isVideoCall: isVideoCall,
        )
    }

    func setActive(callSessionId: String, isVideoCall: Bool) {
        YapCallKitManager.shared.setActive(callSessionId: callSessionId)
    }

    func dismiss(callSessionId: String) {
        YapCallKitManager.shared.dismiss(callSessionId: callSessionId)
    }

    func showMissed(callSessionId: String, callerDisplayName: String) {
        YapCallKitManager.shared.showMissed(callSessionId: callSessionId, callerDisplayName: callerDisplayName)
    }
}
