import Foundation
import Combine
import AppKit
import UserNotifications

final class CallStore: ObservableObject {
    @Published var activeCall: ActiveCall? = nil
    weak var connectionManager: ConnectionManager?
    var onCallChanged: ((ActiveCall?) -> Void)?
    var showWindowAction: (() -> Void)?   // wywołane z menu bar → pokaż okno rozmowy

    enum Phase {
        case incoming
        case active
        case outgoing
    }

    struct ActiveCall {
        let callId: String
        let callerName: String
        let callerNumber: String
        let callerPhoto: NSImage?
        let startedAt: Date
        var phase: Phase
    }

    func receive(callEvent: Maclink_CallEvent) {
        DispatchQueue.main.async {
            switch callEvent.state {
            case .incoming:
                let photo: NSImage? = callEvent.callerPhotoPng.isEmpty
                    ? nil
                    : NSImage(data: callEvent.callerPhotoPng)
                self.activeCall = ActiveCall(
                    callId: callEvent.callID,
                    callerName: callEvent.callerName,
                    callerNumber: callEvent.callerNumber,
                    callerPhoto: photo,
                    startedAt: Date(),
                    phase: .incoming
                )
                self.onCallChanged?(self.activeCall)

            case .outgoing:
                let photo: NSImage? = callEvent.callerPhotoPng.isEmpty
                    ? nil
                    : NSImage(data: callEvent.callerPhotoPng)
                self.activeCall = ActiveCall(
                    callId: callEvent.callID,
                    callerName: callEvent.callerName,
                    callerNumber: callEvent.callerNumber,
                    callerPhoto: photo,
                    startedAt: Date(),
                    phase: .outgoing
                )
                self.onCallChanged?(self.activeCall)

            case .accepted:
                // Android potwierdza odebranie → przejdź w tryb aktywnej rozmowy
                if var call = self.activeCall {
                    call.phase = .active
                    self.activeCall = call
                    self.onCallChanged?(self.activeCall)
                    // Audio engine send path configured — audio started manually via button in ActiveCallView
                    AudioEngine.shared.sendData = { [weak self] data in
                        guard let env = try? Maclink_Envelope(serializedBytes: data) else { return }
                        self?.connectionManager?.send(env)
                    }
                }

            case .ended, .rejected:
                AudioEngine.shared.stopStreaming()
                self.activeCall = nil
                self.onCallChanged?(nil)

            default:
                break
            }
        }
    }

    func accept() {
        guard var call = activeCall else { return }
        call.phase = .active
        activeCall = call
        onCallChanged?(activeCall)
        // Configure AudioEngine send path — audio started manually by user in ActiveCallView
        AudioEngine.shared.sendData = { [weak self] data in
            guard let env = try? Maclink_Envelope(serializedBytes: data) else { return }
            self?.connectionManager?.send(env)
        }
        sendEvent(.accepted, callId: call.callId)
    }

    func reject() {
        guard let call = activeCall else { return }
        AudioEngine.shared.stopStreaming()
        activeCall = nil
        onCallChanged?(nil)
        sendEvent(.rejected, callId: call.callId)
    }

    func hangUp() {
        guard let call = activeCall else { return }
        AudioEngine.shared.stopStreaming()
        activeCall = nil
        onCallChanged?(nil)
        sendEvent(.ended, callId: call.callId)
    }

    // MARK: - Private

    private func sendEvent(_ state: Maclink_CallEvent.State, callId: String) {
        var ev = Maclink_CallEvent()
        ev.callID = callId
        ev.state = state
        var env = Maclink_Envelope()
        env.id = UUID().uuidString
        env.timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        env.callEvent = ev
        connectionManager?.send(env)
    }
}

