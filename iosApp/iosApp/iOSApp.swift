import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        YapSocketBridgeInstaller.install()
        YapNetworkBridgeInstaller.install()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
