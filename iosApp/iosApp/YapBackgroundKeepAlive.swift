import UIKit

/// Keeps the app runnable briefly after backgrounding so Socket.IO can deliver messages
/// and trigger local notifications (debug/local dev without APNs on Simulator).
final class YapBackgroundKeepAlive {
    static let shared = YapBackgroundKeepAlive()

    private var taskId: UIBackgroundTaskIdentifier = .invalid

    private init() {}

    private func diag(_ message: String) {
        print("[YapRuntimeDiag] [BackgroundTask] \(message)")
    }

    func begin() {
        if taskId != .invalid {
            diag("begin() skipped — task already active id=\(taskId.rawValue)")
            return
        }
        taskId = UIApplication.shared.beginBackgroundTask(withName: "YapYapRealtime") { [weak self] in
            self?.diag("expiration handler fired id=\(self?.taskId.rawValue ?? -1)")
            self?.end()
        }
        diag("beginBackgroundTask id=\(taskId.rawValue)")
    }

    func end() {
        guard taskId != .invalid else { return }
        diag("endBackgroundTask id=\(taskId.rawValue)")
        UIApplication.shared.endBackgroundTask(taskId)
        taskId = .invalid
    }
}
