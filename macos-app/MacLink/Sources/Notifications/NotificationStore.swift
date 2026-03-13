import Foundation
import UserNotifications
import Combine

/// In-memory store for notifications received from Android.
final class NotificationStore: ObservableObject {
    @Published private(set) var notifications: [PhoneNotification] = []

    func receive(notification n: Maclink_Notification) {
        let item = PhoneNotification(
            key: n.notificationKey,
            appName: n.appName,
            packageName: n.packageName,
            title: n.title,
            body: n.text,
            postedAt: Date(timeIntervalSince1970: Double(n.postedAt) / 1000),
            actions: n.actions.map { NotificationActionItem(key: $0.actionKey, label: $0.label) }
        )

        DispatchQueue.main.async {
            self.notifications.removeAll { $0.key == item.key }
            self.notifications.insert(item, at: 0)
            if self.notifications.count > 200 { self.notifications.removeLast() }
        }

        showSystemNotification(item)
    }

    func dismiss(key: String) {
        notifications.removeAll { $0.key == key }
    }

    // MARK: - System banner

    private func showSystemNotification(_ item: PhoneNotification) {
        let content = UNMutableNotificationContent()
        content.title = "[\(item.appName)] \(item.title)"
        content.body = item.body
        content.sound = .default

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
}

struct NotificationActionItem: Identifiable {
    let id = UUID()
    let key: String
    let label: String
}
