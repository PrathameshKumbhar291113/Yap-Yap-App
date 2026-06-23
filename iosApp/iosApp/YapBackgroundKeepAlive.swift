import UIKit

/// Keeps the app runnable briefly after backgrounding so Socket.IO can deliver messages
/// and trigger local notifications (debug/local dev without APNs on Simulator).
final class YapBackgroundKeepAlive {
    static let shared = YapBackgroundKeepAlive()

    private var taskId: UIBackgroundTaskIdentifier = .invalid

    private init() {}

    func begin() {
        if taskId != .invalid { return }
        taskId = UIApplication.shared.beginBackgroundTask(withName: "YapYapRealtime") { [weak self] in
            self?.end()
        }
    }

    func end() {
        guard taskId != .invalid else { return }
        UIApplication.shared.endBackgroundTask(taskId)
        taskId = .invalid
    }
}
