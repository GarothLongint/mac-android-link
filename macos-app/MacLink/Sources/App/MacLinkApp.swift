import SwiftUI

@main
struct MacLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        // Menu bar only — no Dock icon (set LSUIElement=YES in Info.plist)
        MenuBarExtra("MacLink", systemImage: "iphone.and.arrow.forward") {
            MenuBarView()
                .environmentObject(appDelegate.connectionManager)
                .environmentObject(appDelegate.notificationStore)
        }
        .menuBarExtraStyle(.window)
    }
}
