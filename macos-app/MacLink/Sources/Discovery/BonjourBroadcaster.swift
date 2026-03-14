import Foundation

/// Announces the MacLink service over mDNS (Bonjour) using NetService.
/// Does NOT open its own port — just broadcasts that port 9876 is available.
final class BonjourBroadcaster: NSObject {
    private var netService: NetService?

    static let serviceType = "_maclink._tcp."
    static let serviceName = "MacLink-\(Host.current().localizedName ?? "Mac")"

    func start(port: UInt16) {
        let service = NetService(
            domain: "local.",
            type: Self.serviceType,
            name: Self.serviceName,
            port: Int32(port)
        )
        service.delegate = self
        service.publish()
        self.netService = service
        print("[Bonjour] Publishing \(Self.serviceName) on port \(port)...")
    }

    func stop() {
        netService?.stop()
        netService = nil
    }
}

extension BonjourBroadcaster: NetServiceDelegate {
    func netServiceDidPublish(_ sender: NetService) {
        print("[Bonjour] Registered: \(sender.name).\(sender.type)\(sender.domain)")
    }

    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        print("[Bonjour] Failed to publish: \(errorDict)")
    }
}
