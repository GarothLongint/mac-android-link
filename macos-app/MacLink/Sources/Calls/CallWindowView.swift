import SwiftUI
import AppKit

/// Okno pływające które wyskakuje automatycznie przy przychodzącym połączeniu.
/// Nie wymaga otwierania menu bara.
struct CallWindowView: View {
    let call: CallStore.ActiveCall
    let onAccept: () -> Void
    let onReject: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                // Zdjęcie lub ikona
                Group {
                    if let photo = call.callerPhoto {
                        Image(nsImage: photo)
                            .resizable()
                            .scaledToFill()
                    } else {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 52, height: 52)
                .background(Color.green.opacity(0.8))
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 3) {
                    Text("Połączenie przychodzące")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(call.callerName.isEmpty ? "Nieznany" : call.callerName)
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
