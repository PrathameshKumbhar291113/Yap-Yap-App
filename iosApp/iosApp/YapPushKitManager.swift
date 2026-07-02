import Foundation
import PushKit
import Shared

final class YapPushKitManager: NSObject, PKPushRegistryDelegate {
    static let shared = YapPushKitManager()

    private var registry: PKPushRegistry?

    func start() {
        guard YapProvisioningCapabilities.isPushKitAvailable else {
            print("[YapPushKit] disabled — missing VoIP background mode or Debug Personal Team configuration")
            return
        }
        let registry = PKPushRegistry(queue: DispatchQueue.main)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
        self.registry = registry
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didUpdate pushCredentials: PKPushCredentials,
        for type: PKPushType,
    ) {
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        print("[YapPushKit] voip token len=\(token.count)")
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void,
    ) {
        let data = payload.dictionaryPayload
        var normalized: [String: String] = [:]
        for (key, value) in data {
            if let key = key as? String {
                normalized[key] = "\(value)"
            }
        }
        normalized["type"] = "incoming_call"
        let callSessionId = normalized["callSessionId"] ?? normalized["call_session_id"] ?? ""
        let callerName = normalized["callerName"] ?? normalized["caller_name"] ?? "Incoming call"
        let callType = normalized["callType"] ?? normalized["call_type"] ?? "voice"
        let isVideo = callType.lowercased() == "video"
        guard !callSessionId.isEmpty else {
            completion()
            return
        }
        YapCallKitManager.shared.reportIncoming(
            callSessionId: callSessionId,
            callerDisplayName: callerName,
            isVideoCall: isVideo,
        )
        let delivery = PushDeliveryContext(mode: .dataonly, osNotificationHandled: false)
        let message = PushInboundMessage(data: normalized, delivery: delivery)
        IosPushBridge.shared.deliverMessage(message: message)
        completion()
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        print("[YapPushKit] token invalidated")
    }
}
