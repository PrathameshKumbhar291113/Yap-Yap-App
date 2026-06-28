import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    private func diag(_ component: String, _ message: String) {
        print("[YapRuntimeDiag] [\(component)] \(message)")
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        YapPushBridgeInstaller.install()

        if let userInfo = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            YapPushManager.shared.handleLaunchNotification(userInfo: userInfo)
        }

        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        diag("Firebase", "APNs deviceToken bytes=\(deviceToken.count)")
        YapPushManager.shared.handleDeviceToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        diag("Firebase", "APNs registration failed: \(error.localizedDescription)")
        print("[PushNotifications] remote registration unavailable: \(error.localizedDescription)")
        IosPushBridge.shared.deliverToken(token: nil)
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        diag("RemoteNotification", "didReceiveRemoteNotification keys=\(userInfo.keys)")
        YapPushManager.shared.handleRemoteNotification(userInfo: userInfo)
        completionHandler(.newData)
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        diag("Lifecycle", "applicationDidBecomeActive")
        YapBackgroundKeepAlive.shared.end()
        IosAppLifecycleBridge.shared.notifyForeground()
    }

    func applicationWillResignActive(_ application: UIApplication) {
        diag("Lifecycle", "applicationWillResignActive")
        IosAppLifecycleBridge.shared.notifyWillResignActive()
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        diag("Lifecycle", "applicationDidEnterBackground")
        YapBackgroundKeepAlive.shared.begin()
        IosAppLifecycleBridge.shared.notifyDidEnterBackground()
    }

    func applicationWillTerminate(_ application: UIApplication) {
        diag("Lifecycle", "applicationWillTerminate")
    }
}
