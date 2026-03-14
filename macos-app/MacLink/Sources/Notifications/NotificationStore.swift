import Foundation
import UserNotifications
import Combine

/// In-memory store for notifications received from Android.
final class NotificationStore: ObservableObject {
    @Published private(set) var notifications: [PhoneNotification] = []
    weak var connectionManager: ConnectionManager?

    func receive(notification n: Maclink_Notification) {
        let item = PhoneNotification(
            key: n.notificationKey,
            appName: n.appName,
            packageName: n.packageName,
            title: n.title,
            body: n.text,
            postedAt: Date(timeIntervalSince1970: Double(n.postedAt) / 1000),
            actions: n.actions.map { NotificationActionItem(key: $0.actionKey, label: $0.label) },
            iconData: n.iconPng.isEmpty ? nil : Data(n.iconPng)
        )

        DispatchQueue.main.async {
            self.notifications.removeAll { $0.key == item.key }
            self.notifications.insert(item, at: 0)
            if self.notifications.count > 200 { self.notifications.removeLast() }
        }

        showSystemNotification(item)
    }

    func clearAll() {
        notifications.removeAll()
    }

    func dismiss(key: String) {        notifications.removeAll { $0.key == key }
        sendDismiss(key: key)
    }

    /// Called by macOS when user clicks a notification action (e.g. Reply)
    func performAction(notificationKey: String, actionKey: String, replyText: String = "") {
        connectionManager?.sendNotificationAction(
            notificationKey: notificationKey,
            actionKey: actionKey,
            replyText: replyText
        )
    }

    private func sendDismiss(key: String) {
        connectionManager?.sendDismiss(notificationKey: key)
    }

    // MARK: - System banner

    private func showSystemNotification(_ item: PhoneNotification) {
        let content = UNMutableNotificationContent()
        content.title = "[\(item.appName)] \(item.title)"
        content.body = item.body
        content.sound = .default

        // Dodaj ikonę apki jako miniaturę w bannerze
        if let iconData = item.iconData {
            let tmpURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("maclink_icon_\(item.packageName).png")
            try? iconData.write(to: tmpURL)
            if let attachment = try? UNNotificationAttachment(
                identifier: item.packageName,
                url: tmpURL,
                options: [UNNotificationAttachmentOptionsThumbnailClippingRectKey:
                            CGRect(x: 0, y: 0, width: 1, height: 1) as AnyObject]
            ) {
                content.attachments = [attachment]
            }
        }

        let request = UNNotificationRequest(
            identifier: "maclink.\(item.key)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }
}

// MARK: - Models

struct PhoneNotification: Identifiable {
    let id = UUID()
    let key: String
    let appName: String
    let packageName: String
    let title: String
    let body: String
    let postedAt: Date
    let actions: [NotificationActionItem]
    let iconData: Data?          // PNG ikony aplikacji (opcjonalne)
}

struct NotificationActionItem: Identifiable {
    let id = UUID()
    let key: String
    let label: String
}
