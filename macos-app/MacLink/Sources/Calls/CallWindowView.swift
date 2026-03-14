import SwiftUI
import AppKit

/// Pływające okno połączenia — wyświetla zarówno przychodzące jak i aktywne połączenie.
struct CallWindowView: View {
    @ObservedObject var callStore: CallStore
    let onAccept: () -> Void
    let onReject: () -> Void
    let onHangUp: () -> Void
    let onClose: () -> Void   // Zamknij okno bez kończenia rozmowy

    var body: some View {
        if let call = callStore.activeCall {
            switch call.phase {
            case .incoming:
                IncomingCallView(call: call, onAccept: onAccept, onReject: onReject)
            case .active, .outgoing:
                ActiveCallView(call: call, onHangUp: onHangUp, onClose: onClose)
            }
        }
    }
}

// MARK: - Incoming call (Odbierz / Odrzuć)

private struct IncomingCallView: View {
    let call: CallStore.ActiveCall
    let onAccept: () -> Void
    let onReject: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                CallerAvatar(photo: call.callerPhoto, size: 52)

                VStack(alignment: .leading, spacing: 3) {
                    Text("Połączenie przychodzące")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(call.callerName.isEmpty ? "Nieznany numer" : call.callerName)
                        .font(.headline)
                        .lineLimit(1)
                    if !call.callerNumber.isEmpty {
                        Text(call.callerNumber)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
            }

            HStack(spacing: 12) {
                Button(action: onReject) {
                    Label("Odrzuć", systemImage: "phone.down.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .keyboardShortcut(.escape)

                Button(action: onAccept) {
                    Label("Odbierz", systemImage: "phone.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
                .keyboardShortcut(.return)
            }
        }
        .padding(18)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Active call (timer + Zakończ + audio toggle)

private struct ActiveCallView: View {
    let call: CallStore.ActiveCall
    let onHangUp: () -> Void
    let onClose: () -> Void    // tylko zamknij okno, rozmowa trwa

    @State private var elapsed: TimeInterval = 0
    @State private var audioStreaming = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                CallerAvatar(photo: call.callerPhoto, size: 52, tint: .blue)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(.green)
                            .frame(width: 7, height: 7)
                        Text("Połączono")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(call.callerName.isEmpty ? "Nieznany numer" : call.callerName)
                        .font(.headline)
                        .lineLimit(1)
                    Text(formatDuration(elapsed))
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                Spacer()

                // Zamknij okno bez kończenia rozmowy
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("Zamknij okno (rozmowa trwa nadal)")
            }

            // Przycisk audio — włącz dopiero gdy rozmowa stabilna
            Button(action: toggleAudio) {
                Label(
                    audioStreaming ? "Wyłącz audio przez Mac" : "🎤 Mów przez Maca",
                    systemImage: audioStreaming ? "mic.slash.fill" : "mic.fill"
                )
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(audioStreaming ? .orange : .blue)

            Button(action: onHangUp) {
                Label("Zakończ rozmowę", systemImage: "phone.down.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .keyboardShortcut(.escape)
        }
        .padding(18)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .onReceive(timer) { _ in
            elapsed = Date().timeIntervalSince(call.startedAt)
        }
        .onDisappear {
            if audioStreaming { AudioEngine.shared.stopStreaming() }
        }
    }

    private func toggleAudio() {
        if audioStreaming {
            AudioEngine.shared.stopStreaming()
            audioStreaming = false
        } else {
            AudioEngine.shared.startStreaming(callId: call.callId)
            audioStreaming = true
        }
    }

    private func formatDuration(_ t: TimeInterval) -> String {
        let total = Int(t)
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        } else {
            return String(format: "%02d:%02d", m, s)
        }
    }
}

// MARK: - Shared avatar

private struct CallerAvatar: View {
    let photo: NSImage?
    let size: CGFloat
    var tint: Color = .green

    var body: some View {
        Group {
            if let photo {
                Image(nsImage: photo)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .background(tint.opacity(0.8))
        .clipShape(Circle())
    }
}

