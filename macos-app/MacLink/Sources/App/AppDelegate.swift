import AppKit
import UserNotifications

class AppDelegate: NSObject, NSApplicationDelegate {
    let connectionManager = ConnectionManager()
    let notificationStore = NotificationStore()

    func applicationDidFinishLaunching(_ notification: Notification) {
        requestNotificationPermission()
        connectionManager.notificationStore = notificationStore
        connectionManager.startServer()
    }

    func applicationWillTerminate(_ notification: Notification) {
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
