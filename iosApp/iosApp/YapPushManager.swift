import Foundation
import Shared
import UserNotifications
import UIKit

#if canImport(FirebaseCore)
import FirebaseCore
#endif
#if canImport(FirebaseMessaging)
import FirebaseMessaging
#endif

final class YapPushManager: NSObject {
    static let shared = YapPushManager()

    private override init() {
        super.init()
    }

    func configure() {
        #if canImport(FirebaseCore)
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        #endif
        #if canImport(FirebaseMessaging)
        Messaging.messaging().delegate = self
        #endif
        UNUserNotificationCenter.current().delegate = self
    }

    func installBridgeHandlers() {
        IosPushBridge.shared.registerRequestPermissionHandler { [weak self] in
            self?.requestPermission()
        }
        IosPushBridge.shared.registerGetTokenHandler { [weak self] in
            self?.fetchToken()
        }
        IosPushBridge.shared.registerDeleteTokenHandler { [weak self] in
            self?.deleteToken()
        }
        IosPushBridge.shared.registerShowNotificationHandler { title, body, payloadJson in
            YapPushManager.shared.showLocalNotification(title: title, body: body, payloadJson: payloadJson)
        }
    }

    func registerForRemoteNotifications() {
        #if targetEnvironment(simulator)
        // APNs is unavailable on Simulator — local notifications still work.
        return
        #else
        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
        #endif
    }

    func handleDeviceToken(_ deviceToken: Data) {
        #if canImport(FirebaseMessaging)
        Messaging.messaging().apnsToken = deviceToken
        #endif
    }

    private func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            IosPushBridge.shared.deliverPermissionResult(granted: granted)
            if granted {
                self.registerForRemoteNotifications()
            }
        }
    }

    private func fetchToken() {
        #if canImport(FirebaseMessaging) && !targetEnvironment(simulator)
        Messaging.messaging().token { token, _ in
            IosPushBridge.shared.deliverToken(token: token)
        }
        #else
        IosPushBridge.shared.deliverToken(token: nil)
        #endif
    }

    private func deleteToken() {
        #if canImport(FirebaseMessaging) && !targetEnvironment(simulator)
        Messaging.messaging().deleteToken { _ in }
        #endif
    }

    private func showLocalNotification(title: String, body: String, payloadJson: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        if let data = payloadJson.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            content.userInfo = object
        }
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    fileprivate func deliverDataDictionary(_ data: [AnyHashable: Any]) {
        var normalized: [String: String] = [:]
        for (key, value) in data {
            if let key = key as? String {
                normalized[key] = "\(value)"
            }
        }
        guard !normalized.isEmpty else { return }
        IosPushBridge.shared.deliverMessage(data: normalized)
    }
}

#if canImport(FirebaseMessaging)
extension YapPushManager: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        IosPushBridge.shared.deliverTokenRefresh(token: token)
    }
}
#endif

extension YapPushManager: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        deliverDataDictionary(notification.request.content.userInfo)
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .list, .sound])
        } else {
            completionHandler([.alert, .sound])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        var normalized: [String: String] = [:]
        for (key, value) in response.notification.request.content.userInfo {
            if let key = key as? String {
                normalized[key] = "\(value)"
            }
        }
        IosPushBridge.shared.deliverNotificationOpened(data: normalized)
        completionHandler()
    }
}

enum YapPushBridgeInstaller {
    static func install() {
        YapPushManager.shared.configure()
        YapPushManager.shared.installBridgeHandlers()
    }
}
