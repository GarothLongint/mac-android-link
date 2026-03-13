# MacLink 🔗

> Integracja macOS ↔ Android — powiadomienia, rozmowy i audio przez WiFi (później Bluetooth i Internet).

Inspirowane Microsoft Phone Link, w pełni open-source, natywne aplikacje.

## Architektura

```
┌─────────────────────────────┐        WiFi (LAN)        ┌──────────────────────────────┐
│       macOS App (Swift)     │ ◄──── WebSocket + TLS ──► │    Android App (Kotlin)      │
│                             │                           │                              │
│  • Menu Bar UI (SwiftUI)    │ ◄──── WebRTC (DTLS) ───► │  • NotificationListenerService│
│  • NWListener (WS Server)   │                           │  • TelecomManager (Calls)    │
│  • Bonjour (mDNS broadcast) │ ◄──── mDNS discovery ──► │  • NSD (mDNS client)         │
│  • CoreAudio                │                           │  • AudioManager              │
└─────────────────────────────┘                           └──────────────────────────────┘
```

## Protokół komunikacji

Wszystkie wiadomości są serializowane przez **Protocol Buffers** i przesyłane przez WebSocket jako dane binarne.

Schemat: [`shared/proto/maclink.proto`](shared/proto/maclink.proto)

Główne typy wiadomości:
| Typ | Kierunek | Opis |
|-----|----------|------|
| `Handshake` / `HandshakeAck` | Android → Mac / Mac → Android | Parowanie urządzeń |
| `Notification` | Android → Mac | Powiadomienie z telefonu |
| `NotificationAction` | Mac → Android | Odpowiedź / akcja |
| `CallEvent` | Dwukierunkowy | Zdarzenia połączeń |
| `AudioOffer/Answer/IceCandidate` | Dwukierunkowy | WebRTC signaling |
| `Heartbeat` | Dwukierunkowy | Keep-alive co 30s |

## Struktura projektu

```
mac-android-link/
├── shared/
│   └── proto/           # Protobuf schema (wspólny protokół)
├── macos-app/           # Swift/SwiftUI aplikacja macOS
│   └── MacLink/
│       └── Sources/
│           ├── App/     # AppDelegate, entry point
│           ├── Server/  # WebSocket NWListener
│           ├── Discovery/ # Bonjour mDNS
│           ├── Notifications/ # UNUserNotificationCenter
│           ├── Audio/   # CoreAudio, WebRTC
│           ├── Model/   # Shared data models
│           └── UI/      # SwiftUI views
├── android-app/         # Kotlin/Compose Android app
│   └── app/src/main/kotlin/com/maclink/android/
│       ├── service/     # NotificationListenerService, TelecomManager
│       ├── network/     # WebSocket client, mDNS NSD
│       ├── ui/          # Jetpack Compose screens
│       └── model/       # Data models
└── docs/                # Dokumentacja, diagramy
```

## Fazy rozwoju

### Faza 1: WiFi (LAN) ✅ W toku
- [ ] Protobuf schema
- [ ] macOS: WebSocket server (NWListener)
- [ ] macOS: mDNS broadcast (Bonjour)
- [ ] macOS: Menu Bar UI (lista powiadomień)
- [ ] Android: mDNS discovery (NSD)
- [ ] Android: WebSocket client (OkHttp)
- [ ] Android: NotificationListenerService
- [ ] Android: UI połączenia

### Faza 2: Audio (WebRTC)
- [ ] WebRTC integracja Android (Google WebRTC AAR)
- [ ] WebRTC integracja macOS (libwebrtc)
- [ ] Obsługa połączeń telefonicznych
- [ ] CallKit integracja macOS

### Faza 3: Bluetooth
- [ ] BLE jako kanał fallback / discovery poza LAN

### Faza 4: Internet
- [ ] TURN/STUN relay server
- [ ] Signaling server (WebSocket relay)
- [ ] E2E encryption (DTLS wbudowane w WebRTC + TLS WebSocket)

## Wymagania

### macOS
- macOS 13.0+ (Ventura)
- Xcode 15+

### Android
- Android 10+ (API 29)
- Android Studio Hedgehog+

## Port domyślny

WebSocket serwer nasłuchuje na `0.0.0.0:9876`  
mDNS service type: `_maclink._tcp.`
