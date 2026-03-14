import AppKit
import SwiftUI
import UserNotifications

class AppDelegate: NSObject, NSApplicationDelegate {
    let connectionManager = ConnectionManager()
    let notificationStore = NotificationStore()
    let callStore = CallStore()
    private let bonjour = BonjourBroadcaster()
    private var callWindow: NSWindow?

    func applicationDidFinishLaunching(_ notification: Notification) {
        requestNotificationPermission()
        connectionManager.notificationStore = notificationStore
        connectionManager.callStore = callStore
        notificationStore.connectionManager = connectionManager
        callStore.connectionManager = connectionManager
        callStore.onCallChanged = { [weak self] call in
            DispatchQueue.main.async {
                if let call, call.state == .incoming {
                    self?.showCallWindow(call: call)
                } else {
                    self?.dismissCallWindow()
                }
            }
        }
        connectionManager.startServer()
        bonjour.start(port: ConnectionManager.port)
    }

    func applicationWillTerminate(_ notification: Notification) {
        bonjour.stop()
        connectionManager.stopServer()
    }

    // MARK: - Call window (wyskakuje automatycznie przy przychodzącym połączeniu)

    private func showCallWindow(call: CallStore.ActiveCall) {
        dismissCallWindow()

        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 320, height: 140),
            styleMask: [.titled, .fullSizeContentView, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.title = "Połączenie przychodzące"
        panel.level = .floating
        panel.isMovableByWindowBackground = true
        panel.titlebarAppearsTransparent = true
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.center()

        // Przesuń do prawego górnego rogu ekranu
        if let screen = NSScreen.main {
            let x = screen.visibleFrame.maxX - 340
            let y = screen.visibleFrame.maxY - 160
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        let view = NSHostingView(rootView:
            CallWindowView(call: call,
                           onAccept: { [weak self] in self?.callStore.accept(); self?.dismissCallWindow() },
                           onReject:  { [weak self] in self?.callStore.reject();  self?.dismissCallWindow() })
                .environmentObject(callStore)
        )
        panel.contentView = view
        panel.orderFrontRegardless()
        callWindow = panel

        // Dźwięk
        NSSound(named: .init("Glass"))?.play()
    }

    private func dismissCallWindow() {
        callWindow?.close()
        callWindow = nil
    }

    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { granted, error in
            if let error { print("[MacLink] Notification permission error: \(error)") }
        }
    }
}
