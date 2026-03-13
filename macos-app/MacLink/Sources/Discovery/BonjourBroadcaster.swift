import Foundation
import Network

/// Broadcasts the MacLink service over mDNS (Bonjour) so Android devices can discover it automatically.
final class BonjourBroadcaster {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "com.maclink.bonjour")

    static let serviceType = "_maclink._tcp"
    static let serviceName = "MacLink-\(Host.current().localizedName ?? "Mac")"

    func start(port: UInt16) {
        let params = NWParameters.tcp
        guard let listener = try? NWListener(using: params, on: NWEndpoint.Port(rawValue: port)!) else {
            print("[Bonjour] Failed to create listener for mDNS")
            return
        }

        listener.service = NWListener.Service(
            name: Self.serviceName,
            type: Self.serviceType,
            txtRecord: makeTXTRecord()
        )

        listener.serviceRegistrationUpdateHandler = { change in
            switch change {
            case .add(let endpoint):
                print("[Bonjour] Registered: \(endpoint)")
            case .remove(let endpoint):
                print("[Bonjour] Removed: \(endpoint)")
            @unknown default: break
            }
        }

        listener.stateUpdateHandler = { state in
            print("[Bonjour] State: \(state)")
        }

        // We only need Bonjour registration, actual connections handled by ConnectionManager
        listener.newConnectionHandler = { $0.cancel() }
        listener.start(queue: queue)
        self.listener = listener
    }

    func stop() {
        listener?.cancel()
    }

    private func makeTXTRecord() -> NWTXTRecord {
        var record = NWTXTRecord()
        record["version"] = "1"
        record["id"] = DeviceIdentity.id
        return record
    }
}
