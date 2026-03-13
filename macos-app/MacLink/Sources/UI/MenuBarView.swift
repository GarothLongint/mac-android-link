import SwiftUI

struct MenuBarView: View {
    @EnvironmentObject var connectionManager: ConnectionManager
    @EnvironmentObject var notificationStore: NotificationStore

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            if notificationStore.notifications.isEmpty {
                emptyState
            } else {
                notificationList
            }
            Divider()
            footer
        }
        .frame(width: 360)
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Image(systemName: connectionManager.connectedDevice != nil ? "iphone.and.arrow.forward" : "iphone.slash")
                .foregroundStyle(connectionManager.connectedDevice != nil ? .green : .secondary)

            if let device = connectionManager.connectedDevice {
                Text(device.name)
                    .font(.headline)
            } else {
                Text("Brak połączenia")
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Circle()
                .fill(statusColor)
                .frame(width: 8, height: 8)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var statusColor: Color {
        switch connectionManager.serverState {
        case .ready:   return connectionManager.connectedDevice != nil ? .green : .orange
        case .failed:  return .red
        default:       return .gray
        }
    }

    // MARK: - Notification list

    private var notificationList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(notificationStore.notifications) { notif in
                    NotificationRow(notification: notif)
                    Divider().padding(.leading, 52)
                }
            }
        }
        .frame(maxHeight: 420)
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "bell.slash")
                .font(.system(size: 32))
                .foregroundStyle(.secondary)
            Text("Brak powiadomień")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }

    // MARK: - Footer

    private var footer: some View {
        HStack {
            Text("Port: \(ConnectionManager.port)")
                .font(.caption)
                .foregroundStyle(.tertiary)
            Spacer()
            Button("Wyczyść") {
                notificationStore.notifications.removeAll()
            }
            .buttonStyle(.plain)
            .font(.caption)
            .foregroundStyle(.secondary)

            Button("Zakończ") {
                NSApp.terminate(nil)
            }
            .buttonStyle(.plain)
            .font(.caption)
            .foregroundStyle(.red)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

// MARK: - Notification Row

struct NotificationRow: View {
    let notification: PhoneNotification

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            // App icon placeholder
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.accentColor.opacity(0.15))
                .frame(width: 32, height: 32)
                .overlay(
                    Text(notification.appName.prefix(1))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.accent)
                )

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(notification.appName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(notification.postedAt, style: .relative)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                Text(notification.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                if !notification.body.isEmpty {
                    Text(notification.body)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}
