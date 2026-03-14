import SwiftUI

struct MenuBarView: View {
    @EnvironmentObject var connectionManager: ConnectionManager
    @EnvironmentObject var notificationStore: NotificationStore
    @EnvironmentObject var callStore: CallStore
    @State private var replyState: ReplyState? = nil

    struct ReplyState: Identifiable {
        let id = UUID()
        let notificationKey: String
        let actionKey: String
        let appName: String
        var text: String = ""
    }

    var body: some View {
        VStack(spacing: 0) {
            // Active call banner (highest priority)
            if let call = callStore.activeCall {
                CallBanner(
                    call: call,
                    onAccept: { callStore.accept() },
                    onReject: { callStore.reject() },
                    onHangUp: { callStore.hangUp() },
                    onShowWindow: { callStore.showWindowAction?() }
                )
                Divider()
            }

            // Pairing request banner (highest priority)
            if let pending = connectionManager.pairing.pendingDevice {
                PairingBanner(pending: pending)
                Divider()
            }

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
        // Reply sheet
        .sheet(item: $replyState) { state in
            ReplySheet(state: state) { text in
                notificationStore.performAction(
                    notificationKey: state.notificationKey,
                    actionKey: state.actionKey,
                    replyText: text
                )
                replyState = nil
            } onCancel: {
                replyState = nil
            }
        }
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
                    NotificationRow(
                        notification: notif,
                        onAction: { key, actionKey in
                            handleAction(notifKey: key, actionKey: actionKey, notif: notif)
                        },
                        onDismiss: {
                            notificationStore.dismiss(key: notif.key)
                        }
                    )
                    Divider().padding(.leading, 52)
                }
            }
        }
        .frame(maxHeight: 420)
    }

    private func handleAction(notifKey: String, actionKey: String, notif: PhoneNotification) {
        // If action looks like a reply (label contains "Odpow" or "Reply"), show reply sheet
        let action = notif.actions.first { $0.key == actionKey }
        let label = action?.label.lowercased() ?? ""
        if label.contains("odpow") || label.contains("reply") || label.contains("odpisz") {
            replyState = ReplyState(
                notificationKey: notifKey,
                actionKey: actionKey,
                appName: notif.appName
            )
        } else {
            notificationStore.performAction(notificationKey: notifKey, actionKey: actionKey)
        }
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
                notificationStore.clearAll()
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
    var onAction: ((String, String) -> Void)? = nil // (notifKey, actionKey)
    var onDismiss: (() -> Void)? = nil

    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10) {
                // App icon (letter placeholder or actual icon)
                if let iconData = notification.iconData, let nsImage = NSImage(data: iconData) {
                    Image(nsImage: nsImage)
                        .resizable()
                        .frame(width: 32, height: 32)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.accentColor.opacity(0.15))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Text(notification.appName.prefix(1))
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color.accentColor)
                        )
                }

                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(notification.appName)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(notification.postedAt, style: .relative)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)

                        // Dismiss button
                        Button {
                            onDismiss?()
                        } label: {
                            Image(systemName: "xmark")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .buttonStyle(.plain)
                    }
                    Text(notification.title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)
                    if !notification.body.isEmpty {
                        Text(notification.body)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(isExpanded ? 8 : 2)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
            .onTapGesture { isExpanded.toggle() }

            // Action buttons
            if !notification.actions.isEmpty {
                HStack(spacing: 8) {
                    ForEach(notification.actions) { action in
                        Button(action.label) {
                            onAction?(notification.key, action.key)
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                }
                .padding(.leading, 58)
                .padding(.bottom, 8)
            }
        }
    }
}

// MARK: - Pairing Banner

struct PairingBanner: View {
    let pending: PairingManager.PendingDevice

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "iphone.badge.play")
                    .font(.title2)
                    .foregroundStyle(.orange)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Nowe urządzenie")
                        .font(.subheadline).fontWeight(.semibold)
                    Text("\(pending.name) chce się połączyć")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
            }
            HStack {
                Button("Odrzuć") {
                    pending.onDecision(false)
                }
                .buttonStyle(.bordered)
                .tint(.red)

                Button("Zaufaj i połącz") {
                    pending.onDecision(true)
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(12)
        .background(Color.orange.opacity(0.08))
    }
}

// MARK: - Call Banner

struct CallBanner: View {
    let call: CallStore.ActiveCall
    let onAccept: () -> Void
    let onReject: () -> Void
    let onHangUp: () -> Void
    let onShowWindow: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                // Caller photo or fallback icon
                if let photo = call.callerPhoto {
                    Image(nsImage: photo)
                        .resizable()
                        .frame(width: 40, height: 40)
                        .clipShape(Circle())
                } else {
                    Image(systemName: "phone.fill")
                        .font(.title2)
                        .foregroundStyle(.green)
                        .frame(width: 40, height: 40)
                        .background(Color.green.opacity(0.12))
                        .clipShape(Circle())
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(call.phase == .incoming ? "Połączenie przychodzące" : call.phase == .active ? "W trakcie rozmowy" : "Połączenie wychodzące")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(call.callerName.isEmpty ? call.callerNumber : call.callerName)
                        .font(.subheadline).fontWeight(.semibold)
                    if !call.callerName.isEmpty && !call.callerNumber.isEmpty {
                        Text(call.callerNumber)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()

                // Przywróć okno rozmowy (gdy aktywna)
                if call.phase == .active || call.phase == .outgoing {
                    Button(action: onShowWindow) {
                        Image(systemName: "rectangle.and.arrow.up.right.and.arrow.down.left")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .help("Pokaż okno rozmowy")
                }
            }

            HStack {
                if call.phase == .incoming {
                    Button("Odrzuć") { onReject() }
                        .buttonStyle(.bordered)
                        .tint(.red)
                    Button("Odbierz") { onAccept() }
                        .buttonStyle(.borderedProminent)
                        .tint(.green)
                } else {
                    Button("Pokaż okno") { onShowWindow() }
                        .buttonStyle(.bordered)
                    Button("Zakończ") { onHangUp() }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(12)
        .background(Color.green.opacity(0.08))
    }
}

// MARK: - Reply Sheet

struct ReplySheet: View {
    let state: MenuBarView.ReplyState
    let onSend: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Odpowiedz przez \(state.appName)")
                .font(.headline)

            TextEditor(text: .constant(state.text))
                .frame(height: 80)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.secondary.opacity(0.3)))

            HStack {
                Button("Anuluj", action: onCancel).keyboardShortcut(.escape)
                Spacer()
                Button("Wyślij") { onSend(state.text) }
                    .buttonStyle(.borderedProminent)
                    .keyboardShortcut(.return)
            }
        }
        .padding(20)
        .frame(width: 340)
    }
}
