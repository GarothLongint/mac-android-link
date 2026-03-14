import Foundation
import Combine
import AppKit
import UserNotifications

final class CallStore: ObservableObject {
    @Published var activeCall: ActiveCall? = nil
    weak var connectionManager: ConnectionManager?
    var onCallChanged: ((ActiveCall?) -> Void)?

    struct ActiveCall {
        let callId: String
        let callerName: String
        let callerNumber: String
        let callerPhoto: NSImage?
        let state: Maclink_CallEvent.State
        let startedAt: Date
    }

    func receive(callEvent: Maclink_CallEvent) {
        DispatchQueue.main.async {
            switch callEvent.state {
            case .incoming, .outgoing:
                let photo: NSImage? = callEvent.callerPhotoPng.isEmpty
                    ? nil
                    : NSImage(data: callEvent.callerPhotoPng)
                self.activeCall = ActiveCall(
                    callId: callEvent.callID,
                    callerName: callEvent.callerName,
                    callerNumber: callEvent.callerNumber,
                    callerPhoto: photo,
                    state: callEvent.state,
                    startedAt: Date()
                )
                self.onCallChanged?(self.activeCall)
                self.showCallNotification(callEvent: callEvent)
            case .accepted, .ended, .rejected:
                self.activeCall = nil
                self.onCallChanged?(nil)
            default:
                break
            }
        }
    }

    func accept() { send(.accepted) }
    func reject() { send(.rejected) }

    private func send(_ state: Maclink_CallEvent.State) {
        guard let call = activeCall else { return }
        var ev = Maclink_CallEvent()
        ev.callID = call.callId
        ev.state = state
        var env = Maclink_Envelope()
        env.id = UUID().uuidString
        env.timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        env.callEvent = ev
        connectionManager?.send(env)
        activeCall = nil
    }

    private func showCallNotification(callEvent: Maclink_CallEvent) {
        let content = UNMutableNotificationContent()
        let isIncoming = callEvent.state == .incoming
        let callerDisplay = callEvent.callerName.isEmpty ? callEvent.callerNumber : callEvent.callerName
        content.title = isIncoming ? "Połączenie przychodzące" : "Połączenie wychodzące"
        content.body = callerDisplay.isEmpty ? "Nieznany numer" : callerDisplay
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: "call-\(callEvent.callID)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error { print("[CallStore] Notification error: \(error)") }
        }
    }
}
