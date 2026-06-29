import Network
import Shared

/// Native NWPathMonitor wired into Kotlin [OutgoingMessageOutboxService] via [IosNetworkBridge].
final class YapNetworkMonitor {
    static let shared = YapNetworkMonitor()

    private var monitor: NWPathMonitor?
    private let queue = DispatchQueue(label: "com.yapyap.network-monitor")

    private init() {}

    func installBridgeHandlers() {
        IosNetworkBridge.shared.registerStartHandler {
            self.start()
        }
        IosNetworkBridge.shared.registerStopHandler {
            self.stop()
        }
    }

    private func start() {
        stop()
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { path in
            let online = path.status == .satisfied
            IosNetworkBridge.shared.deliverOnlineStatus(online: online)
        }
        monitor.start(queue: queue)
        self.monitor = monitor
        IosNetworkBridge.shared.deliverOnlineStatus(online: monitor.currentPath.status == .satisfied)
    }

    private func stop() {
        monitor?.cancel()
        monitor = nil
    }
}

enum YapNetworkBridgeInstaller {
    static func install() {
        YapNetworkMonitor.shared.installBridgeHandlers()
    }
}
