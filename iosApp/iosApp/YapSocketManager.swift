import Foundation
import SocketIO
import Shared

enum YapSocketJson {
    static func stringFromSocketData(_ data: [Any]) -> String? {
        guard let first = data.first else { return nil }
        if let string = first as? String { return string }
        return stringFromJsonObject(first)
    }

    static func stringFromAckData(_ data: [Any]) -> String? {
        if data.isEmpty { return nil }
        if data.count == 1, let first = data.first {
            return stringFromJsonObject(first)
        }
        return stringFromJsonObject(data)
    }

    private static func stringFromJsonObject(_ object: Any) -> String? {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object),
              let json = String(data: data, encoding: .utf8) else {
            return nil
        }
        return json
    }

    static func dictionary(from jsonPayload: String) -> [String: Any]? {
        guard let data = jsonPayload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let dictionary = object as? [String: Any] else {
            return nil
        }
        return dictionary
    }
}

final class YapSocketManager {
    static let shared = YapSocketManager()

    private var manager: SocketManager?
    private var socket: SocketIOClient?

    private init() {}

    func connect(baseUrl: String, token: String) {
        disconnectNative()

        guard let socketUrl = URL(string: baseUrl) else { return }

        let socketManager = SocketManager(
            socketURL: socketUrl,
            config: [
                .log(true),
                .compress,
                .forceNew(true),
                .reconnects(false),
                .version(.three),
            ]
        )
        let client = socketManager.defaultSocket
        registerListeners(on: client)

        manager = socketManager
        socket = client
        print("[SocketIO] connecting to \(baseUrl)")
        client.connect(withPayload: ["token": token])
    }

    func disconnectNative() {
        socket?.removeAllHandlers()
        socket?.disconnect()
        socket = nil
        manager = nil
    }

    func emit(event: String, payloadJson: String, ackId: Int32) {
        guard let socket, let payload = YapSocketJson.dictionary(from: payloadJson) else { return }

        if ackId >= 0 {
            socket.emitWithAck(event, payload).timingOut(after: 15) { data in
                let response = YapSocketJson.stringFromAckData(data)
                IosSocketBridge.shared.deliverAck(ackId: ackId, payload: response)
            }
        } else {
            socket.emit(event, payload)
        }
    }

    private func registerListeners(on client: SocketIOClient) {
        client.on(clientEvent: .connect) { [weak self] _, _ in
            print("[SocketIO] connected")
            self?.deliverNative(event: SocketConstants.shared.NATIVE_CONNECT, payload: nil)
        }

        client.on(clientEvent: .disconnect) { [weak self] data, _ in
            print("[SocketIO] disconnected: \(data)")
            self?.deliverNative(event: SocketConstants.shared.NATIVE_DISCONNECT, payload: nil)
        }

        client.on(clientEvent: .error) { [weak self] data, _ in
            let message = YapSocketJson.stringFromSocketData(data) ?? String(describing: data)
            print("[SocketIO] connect_error: \(message)")
            self?.deliverNative(event: SocketConstants.shared.NATIVE_CONNECT_ERROR, payload: message)
        }

        listenServerEvent(client, event: SocketConstants.shared.SOCKET_READY)
        listenServerEvent(client, event: SocketConstants.shared.MESSAGE_NEW)
        listenServerEvent(client, event: SocketConstants.shared.MESSAGE_STATUS)
        listenServerEvent(client, event: SocketConstants.shared.TYPING)
        listenServerEvent(client, event: SocketConstants.shared.PRESENCE_UPDATE)
    }

    private func listenServerEvent(_ client: SocketIOClient, event: String) {
        client.on(event) { [weak self] data, _ in
            if event == SocketConstants.shared.SOCKET_READY {
                print("[SocketIO] socket:ready")
            }
            let payload = YapSocketJson.stringFromSocketData(data)
            self?.deliverNative(event: event, payload: payload)
        }
    }

    private func deliverNative(event: String, payload: String?) {
        IosSocketBridge.shared.deliverNativeEvent(event: event, payload: payload)
    }
}

enum YapSocketBridgeInstaller {
    static func install() {
        IosSocketBridge.shared.registerConnectHandler { baseUrl, token in
            YapSocketManager.shared.connect(baseUrl: baseUrl, token: token)
        }
        IosSocketBridge.shared.registerDisconnectHandler {
            YapSocketManager.shared.disconnectNative()
        }
        IosSocketBridge.shared.registerEmitHandler { event, payloadJson, ackId in
            YapSocketManager.shared.emit(event: event, payloadJson: payloadJson, ackId: Int32(ackId))
        }
    }
}
