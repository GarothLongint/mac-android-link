import AppKit
import UserNotifications

class AppDelegate: NSObject, NSApplicationDelegate {
    let connectionManager = ConnectionManager()
    let notificationStore = NotificationStore()
    let callStore = CallStore()
    private let bonjour = BonjourBroadcaster()

    func applicationDidFinishLaunching(_ notification: Notification) {
        requestNotificationPermission()
        connectionManager.notificationStore = notificationStore
        connectionManager.callStore = callStore
        notificationStore.connectionManager = connectionManager
        callStore.connectionManager = connectionManager
        connectionManager.startServer()
        bonjour.start(port: ConnectionManager.port)
    }

    func applicationWillTerminate(_ notification: Notification) {
        bonjour.stop()
        connectionManager.stopServer()
    }

    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { granted, error in
            if let error { print("[MacLink] Notification permission error: \(error)") }
        }
    }
}
