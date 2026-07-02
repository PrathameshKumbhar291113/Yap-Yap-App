import Foundation

/// Build-configuration gates for push/CallKit features that require a paid Apple Developer account.
/// Debug (Personal Team) sets the Yap*Enabled keys to false in Info-Debug.plist and uses empty entitlements.
/// Release keeps push, VoIP, CallKit, and `YapYap.entitlements` for paid provisioning profiles.
enum YapProvisioningCapabilities {
    static var isPushKitAvailable: Bool {
        plistEnabled("YapPushKitEnabled") && hasVoipBackgroundMode
    }

    static var isCallKitAvailable: Bool {
        plistEnabled("YapCallKitEnabled")
    }

    static var isRemoteNotificationsAvailable: Bool {
        plistEnabled("YapRemoteNotificationsEnabled") && hasRemoteNotificationBackgroundMode
    }

    static var hasVoipBackgroundMode: Bool {
        backgroundModes.contains("voip")
    }

    static var hasRemoteNotificationBackgroundMode: Bool {
        backgroundModes.contains("remote-notification")
    }

    private static var backgroundModes: [String] {
        Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String] ?? []
    }

    private static func plistEnabled(_ key: String) -> Bool {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) else {
            return false
        }
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            switch string.lowercased() {
            case "1", "true", "yes": return true
            default: return false
            }
        }
        return false
    }
}
