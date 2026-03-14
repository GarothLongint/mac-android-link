import Foundation

/// Persists trusted Android device IDs across app launches.
/// On first connection from an unknown device, the user must approve it.
final class PairingManager: ObservableObject {
    private let trustedKey = "com.maclink.trustedDevices"

    @Published var pendingDevice: PendingDevice?

    struct PendingDevice {
        let id: String
        let name: String
        var onDecision: (Bool) -> Void
    }

    // MARK: - Trust checks

    func isTrusted(deviceId: String) -> Bool {
        trustedDevices.contains(deviceId)
    }

    func trust(deviceId: String) {
        var devices = trustedDevices
        devices.insert(deviceId)
        UserDefaults.standard.set(Array(devices), forKey: trustedKey)
    }

    func revoke(deviceId: String) {
        var devices = trustedDevices
        devices.remove(deviceId)
        UserDefaults.standard.set(Array(devices), forKey: trustedKey)
        ObjectWillChangePublisher().send()
    }

    var trustedDevices: Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: trustedKey) ?? [])
    }

    // MARK: - Pairing request

    /// Call when a new (unknown) device connects. Shows UI prompt.
    /// `decision` callback receives `true` if user accepted.
    func requestPairing(id: String, name: String, decision: @escaping (Bool) -> Void) {
        DispatchQueue.main.async {
            self.pendingDevice = PendingDevice(id: id, name: name, onDecision: { accepted in
                if accepted { self.trust(deviceId: id) }
                decision(accepted)
                self.pendingDevice = nil
            })
        }
    }
}
