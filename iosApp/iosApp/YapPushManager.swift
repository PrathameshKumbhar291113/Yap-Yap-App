import Foundation
import Shared
import UserNotifications
import UIKit

import FirebaseCore
import FirebaseMessaging

final class YapPushManager: NSObject {
    static let shared = YapPushManager()

    private func diag(_ component: String, _ message: String) {
        print("[YapRuntimeDiag] [\(component)] \(message)")
    }

    private override init() {
        super.init()
    }

    func configure() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        Messaging.messaging().delegate = self
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
        IosPushBridge.shared.registerShowNotificationHandler { title, body, payloadJson, threadId, identifier, summaryArgument in
            YapPushManager.shared.showLocalNotification(
                title: title,
                body: body,
                payloadJson: payloadJson,
                threadId: threadId,
                identifier: identifier,
                summaryArgument: summaryArgument
            )
        }
        IosPushBridge.shared.registerDismissNotificationHandler { threadId in
            YapPushManager.shared.dismissNotifications(forThreadId: threadId)
        }
    }

    func registerForRemoteNotifications() {
        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func handleDeviceToken(_ deviceToken: Data) {
        diag("Firebase", "handleDeviceToken apnsBytes=\(deviceToken.count)")
        Messaging.messaging().apnsToken = deviceToken
        fetchToken()
    }

    func handleRemoteNotification(userInfo: [AnyHashable: Any]) {
        let normalized = normalizeUserInfo(userInfo)
        diag(
            "RemoteNotification",
            "handleRemoteNotification normalizedKeys=\(normalized.keys.sorted()) conversationId=\(normalized["conversationId"] ?? "nil")"
        )
        guard !normalized.isEmpty else { return }
        let osHandled = hasVisibleAlert(userInfo)
        deliverInboundMessage(data: normalized, osNotificationHandled: osHandled)
        Messaging.messaging().appDidReceiveMessage(userInfo)
    }

    func handleLaunchNotification(userInfo: [AnyHashable: Any]) {
        let normalized = normalizeUserInfo(userInfo)
        guard !normalized.isEmpty else { return }
        IosPushBridge.shared.deliverNotificationOpened(data: normalized)
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
        Messaging.messaging().token { token, _ in
            IosPushBridge.shared.deliverToken(token: token)
        }
    }

    private func deleteToken() {
        Messaging.messaging().deleteToken { _ in }
    }

    func dismissNotifications(forThreadId threadId: String) {
        diag("Notification", "dismissNotifications threadId=\(threadId)")
        let center = UNUserNotificationCenter.current()
        center.getDeliveredNotifications { notifications in
            let identifiers = notifications.compactMap { notification -> String? in
                let content = notification.request.content
                if content.threadIdentifier == threadId {
                    return notification.request.identifier
                }
                if notification.request.identifier == threadId {
                    return notification.request.identifier
                }
                if let conversationId = content.userInfo["conversationId"] as? String,
                   threadId == "conv_\(conversationId)" {
                    return notification.request.identifier
                }
                return nil
            }
            guard !identifiers.isEmpty else { return }
            center.removeDeliveredNotifications(withIdentifiers: identifiers)
        }
        center.getPendingNotificationRequests { requests in
            let identifiers = requests.compactMap { request -> String? in
                if request.content.threadIdentifier == threadId {
                    return request.identifier
                }
                if request.identifier == threadId {
                    return request.identifier
                }
                return nil
            }
            guard !identifiers.isEmpty else { return }
            center.removePendingNotificationRequests(withIdentifiers: identifiers)
        }
    }

    private func showLocalNotification(
        title: String,
        body: String,
        payloadJson: String,
        threadId: String?,
        identifier: String?,
        summaryArgument: String?
    ) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let allowed: Bool
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                allowed = true
            default:
                allowed = false
            }
            guard allowed else { return }

            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            if let threadId, !threadId.isEmpty {
                content.threadIdentifier = threadId
            }
            if let summaryArgument, !summaryArgument.isEmpty {
                content.summaryArgument = summaryArgument
            }
            if let data = payloadJson.data(using: .utf8),
               let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                content.userInfo = object
            }
            let requestId = identifier ?? UUID().uuidString
            self.diag(
                "Notification",
                "UNUserNotificationCenter.add requestId=\(requestId) threadId=\(threadId ?? "nil") title=\(title)"
            )
            let request = UNNotificationRequest(
                identifier: requestId,
                content: content,
                trigger: nil
            )
            DispatchQueue.main.async {
                UNUserNotificationCenter.current().add(request)
            }
        }
    }

    private func deliverInboundMessage(data: [String: String], osNotificationHandled: Bool) {
        let mode: PushDeliveryMode = osNotificationHandled ? .hybrid : .dataonly
        let delivery = PushDeliveryContext(mode: mode, osNotificationHandled: osNotificationHandled)
        let message = PushInboundMessage(data: data, delivery: delivery)
        IosPushBridge.shared.deliverMessage(message: message)
    }

    private func hasVisibleAlert(_ userInfo: [AnyHashable: Any]) -> Bool {
        guard let aps = userInfo["aps"] as? [String: Any] else { return false }
        if aps["alert"] != nil { return true }
        return false
    }

    fileprivate func normalizeUserInfo(_ data: [AnyHashable: Any]) -> [String: String] {
        var normalized: [String: String] = [:]
        for (key, value) in data {
            if let key = key as? String,
               !key.hasPrefix("google."),
               !key.hasPrefix("gcm."),
               key != "aps" {
                normalized[key] = "\(value)"
            }
        }
        if let aps = data["aps"] as? [String: Any],
           let alert = aps["alert"] as? [String: Any] {
            if let title = alert["title"] as? String, normalized["senderName"] == nil {
                normalized["senderName"] = title
            }
            if let body = alert["body"] as? String, normalized["preview"] == nil {
                normalized["preview"] = body
            }
            if normalized["type"] == nil {
                normalized["type"] = "new_message"
            }
        } else if let aps = data["aps"] as? [String: Any],
                  let alert = aps["alert"] as? String {
            if normalized["preview"] == nil {
                normalized["preview"] = alert
            }
            if normalized["type"] == nil {
                normalized["type"] = "new_message"
            }
        }
        return normalized
    }
}

extension YapPushManager: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        diag("Firebase", "didReceiveRegistrationToken prefix=\(String(token.prefix(12)))...")
        IosPushBridge.shared.deliverTokenRefresh(token: token)
    }
}

extension YapPushManager: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        diag(
            "RemoteNotification",
            "willPresent id=\(notification.request.identifier) threadId=\(notification.request.content.threadIdentifier) keys=\(userInfo.keys)"
        )
        let normalized = normalizeUserInfo(userInfo)
        // OS presents the banner — sync data only; coordinator must not create a duplicate local notification.
        deliverInboundMessage(data: normalized, osNotificationHandled: true)
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        diag(
            "RemoteNotification",
            "didReceive response id=\(response.notification.request.identifier) threadId=\(response.notification.request.content.threadIdentifier)"
        )
        let normalized = normalizeUserInfo(response.notification.request.content.userInfo)
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
