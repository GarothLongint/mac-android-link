import Foundation
import Network
import Combine

/// Manages connected Android devices and the NWListener WebSocket server.
final class ConnectionManager: ObservableObject {
    static let port: UInt16 = 9876

    @Published var connectedDevice: AndroidDevice?
    @Published var serverState: NWListener.State = .setup

    weak var notificationStore: NotificationStore?
    let pairing = PairingManager()

    private var listener: NWListener?
    private var activeConnection: NWConnection?
    private let queue = DispatchQueue(label: "com.maclink.server", qos: .userInitiated)

    // MARK: - Server lifecycle

    func startServer() {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true

        let wsOptions = NWProtocolWebSocket.Options()
        wsOptions.autoReplyPing = true
        params.defaultProtocolStack.applicationProtocols.insert(wsOptions, at: 0)

        guard let listener = try? NWListener(using: params, on: NWEndpoint.Port(rawValue: Self.port)!) else {
            print("[Server] Failed to create listener")
            return
        }

        listener.stateUpdateHandler = { [weak self] state in
            DispatchQueue.main.async { self?.serverState = state }
            print("[Server] State: \(state)")
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleNewConnection(connection)
        }

        listener.start(queue: queue)
        self.listener = listener
        print("[Server] Listening on port \(Self.port)")
    }

    func stopServer() {
        listener?.cancel()
        activeConnection?.cancel()
    }

    // MARK: - Connection handling

    private func handleNewConnection(_ connection: NWConnection) {
        // Only one device at a time for now
        activeConnection?.cancel()
        activeConnection = connection

        connection.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                print("[Server] Client connected")
                self?.receiveNextMessage(on: connection)
            case .failed(let error):
                print("[Server] Connection failed: \(error)")
                DispatchQueue.main.async { self?.connectedDevice = nil }
            case .cancelled:
                DispatchQueue.main.async { self?.connectedDevice = nil }
            default: break
            }
        }

        connection.start(queue: queue)
    }

    // MARK: - Message receiving (length-prefixed binary Protobuf)

    private func receiveNextMessage(on connection: NWConnection) {
        connection.receiveMessage { [weak self] data, context, isComplete, error in
            if let error {
                print("[Server] Receive error: \(error)")
                return
            }
            if let data, !data.isEmpty {
                self?.handleData(data, on: connection)
            }
            self?.receiveNextMessage(on: connection)
        }
    }

    private func handleData(_ data: Data, on connection: NWConnection) {
        do {
            let envelope = try Maclink_Envelope(serializedBytes: data)
            processEnvelope(envelope, on: connection)
        } catch {
            print("[Server] Proto decode error: \(error)")
        }
    }

    private func processEnvelope(_ envelope: Maclink_Envelope, on connection: NWConnection) {
        switch envelope.payload {
        case .handshake(let hs):
            handleHandshake(hs, on: connection)
        case .notification(let n):
            DispatchQueue.main.async {
                self.notificationStore?.receive(notification: n)
            }
        case .callEvent(let call):
            print("[Server] Call event: \(call.state) from \(call.callerName)")
        case .heartbeat:
            sendHeartbeatAck(on: connection)
        default:
            print("[Server] Unhandled envelope payload")
        }
    }

    // MARK: - Handshake

    private func handleHandshake(_ hs: Maclink_Handshake, on connection: NWConnection) {
        print("[Server] Handshake from \(hs.deviceName) (v\(hs.version))")

        if pairing.isTrusted(deviceId: hs.deviceID) {
            acceptDevice(id: hs.deviceID, name: hs.deviceName, on: connection)
        } else {
            pairing.requestPairing(id: hs.deviceID, name: hs.deviceName) { [weak self] accepted in
                if accepted {
                    self?.acceptDevice(id: hs.deviceID, name: hs.deviceName, on: connection)
                } else {
                    self?.rejectDevice(reason: "Odrzucono przez użytkownika", on: connection)
                }
            }
        }
    }

    private func acceptDevice(id: String, name: String, on connection: NWConnection) {
        let device = AndroidDevice(id: id, name: name)
        DispatchQueue.main.async { self.connectedDevice = device }

        var ack = Maclink_HandshakeAck()
        ack.accepted = true
        ack.macName = Host.current().localizedName ?? "Mac"
        ack.macID = DeviceIdentity.id
        sendEnvelope(ack.asEnvelope(), on: connection)
    }

    private func rejectDevice(reason: String, on connection: NWConnection) {
        var ack = Maclink_HandshakeAck()
        ack.accepted = false
        ack.rejectReason = reason
        sendEnvelope(ack.asEnvelope(), on: connection)
        connection.cancel()
    }

    // MARK: - Sending

    func send(_ envelope: Maclink_Envelope) {
        guard let connection = activeConnection else { return }
        sendEnvelope(envelope, on: connection)
    }

    func sendNotificationAction(notificationKey: String, actionKey: String, replyText: String) {
        var action = Maclink_NotificationAction()
        action.notificationKey = notificationKey
        action.actionKey = actionKey
        action.label = actionKey
        action.replyText = replyText

        var env = Maclink_Envelope()
        env.id = UUID().uuidString
        env.timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        env.notificationAction = action
        send(env)
    }

    func sendDismiss(notificationKey: String) {
        // Reuse NotificationAction with special actionKey "dismiss"
        sendNotificationAction(notificationKey: notificationKey, actionKey: "dismiss", replyText: "")
    }

    private func sendEnvelope(_ envelope: Maclink_Envelope, on connection: NWConnection) {
        guard let data = try? envelope.serializedData() else { return }
        let metadata = NWProtocolWebSocket.Metadata(opcode: .binary)
        let context = NWConnection.ContentContext(identifier: "ws", metadata: [metadata])
        connection.send(content: data, contentContext: context, isComplete: true, completion: .idempotent)
    }

    private func sendHeartbeatAck(on connection: NWConnection) {
        var hb = Maclink_Heartbeat()
        hb.sentAt = Int64(Date().timeIntervalSince1970 * 1000)
        var env = Maclink_Envelope()
        env.heartbeat = hb
        sendEnvelope(env, on: connection)
    }
}

// MARK: - Helpers

struct AndroidDevice: Identifiable {
    let id: String
    let name: String
}

enum DeviceIdentity {
    static let id: String = {
        let key = "com.maclink.device.id"
        if let stored = UserDefaults.standard.string(forKey: key) { return stored }
        let new = UUID().uuidString
        UserDefaults.standard.set(new, forKey: key)
        return new
    }()
}

extension Maclink_HandshakeAck {
    func asEnvelope() -> Maclink_Envelope {
        var env = Maclink_Envelope()
        env.id = UUID().uuidString
        env.timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        env.handshakeAck = self
        return env
    }
}
