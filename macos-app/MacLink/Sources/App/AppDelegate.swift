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
                if let call {
                    if call.phase == .incoming {
                        self?.showCallWindow()
                    } else if call.phase == .active || call.phase == .outgoing {
                        if self?.callWindow == nil {
                            self?.showCallWindow()
                        }
                    }
                } else {
                    self?.dismissCallWindow()
                }
            }
        }
        callStore.showWindowAction = { [weak self] in
            DispatchQueue.main.async { self?.showCallWindow() }
        }
        connectionManager.startServer()
        bonjour.start(port: ConnectionManager.port)
    }

    func applicationWillTerminate(_ notification: Notification) {
        bonjour.stop()
        connectionManager.stopServer()
    }

    // MARK: - Call window (wyskakuje automatycznie przy przychodzącym połączeniu)

    private func showCallWindow() {
        guard callWindow == nil else { return } // już otwarte

        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 320, height: 140),
            styleMask: [.titled, .fullSizeContentView, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.title = "Połączenie"
        panel.level = .floating
        panel.isMovableByWindowBackground = true
        panel.titlebarAppearsTransparent = true
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.center()

        // Prawy górny róg ekranu
        if let screen = NSScreen.main {
            let x = screen.visibleFrame.maxX - 340
            let y = screen.visibleFrame.maxY - 160
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        let view = NSHostingView(rootView:
            CallWindowView(
                callStore: callStore,
                onAccept: { [weak self] in self?.callStore.accept() },
                onReject:  { [weak self] in self?.callStore.reject() },
                onHangUp:  { [weak self] in self?.callStore.hangUp() },
                onClose:   { [weak self] in self?.closeCallWindowOnly() }
            )
        )
        panel.contentView = view
        panel.orderFrontRegardless()
        callWindow = panel

        NSSound(named: .init("Glass"))?.play()
    }

    private func dismissCallWindow() {
        callWindow?.close()
        callWindow = nil
    }

    /// Zamknij okno ale NIE kończ rozmowy — można przywrócić z menu bar
    private func closeCallWindowOnly() {
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
