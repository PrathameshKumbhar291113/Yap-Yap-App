import AVFoundation
import Shared

final class YapCameraPermissionBridge: NSObject, IosCameraPermissionSwiftHandler {
    static let shared = YapCameraPermissionBridge()

    private override init() {
        super.init()
    }

    static func install() {
        IosCameraPermissionBridgeRegistryKt.registerIosCameraPermissionHandler(handler: shared)
    }

    func currentAuthorizationStatusRaw() -> String {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return "authorized"
        case .denied:
            return "denied"
        case .restricted:
            return "restricted"
        case .notDetermined:
            return "not_determined"
        @unknown default:
            return "not_determined"
        }
    }

    func requestAccess() {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            IosCameraPermissionBridgeRegistryKt.iosCameraPermissionAccessCompleted(granted: granted)
        }
    }
}
