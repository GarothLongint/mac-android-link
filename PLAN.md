# MacLink — Plan pracy

> Integracja macOS ↔ Android (Phone Link dla Apple Silicon)

---

## Status faz

| Faza | Zakres | Status |
|------|--------|--------|
| **0** | Infrastruktura (proto, scaffold, skrypty) | ✅ Ukończona |
| **1** | WiFi (LAN) — powiadomienia | 🔵 W toku (8/16) |
| **2** | Audio i rozmowy telefoniczne | ⬜ Oczekuje |
| **3** | Bluetooth | ⬜ Oczekuje |
| **4** | Internet (relay przez sieć) | ⬜ Oczekuje |
| **5** | Dodatkowe funkcje | ⬜ Oczekuje |

---

## Faza 0 — Infrastruktura ✅

Podstawy projektu, które pozwalają na dalszą pracę.

- [x] `shared/proto/maclink.proto` — protokół komunikacji (Protobuf)
- [x] `scripts/gen-proto.sh` — generowanie kodu Swift i Android z .proto
- [x] `scripts/setup.sh` — instalacja narzędzi (brew, protoc, xcode, android studio)
- [x] Struktura katalogów `macos-app/` i `android-app/`

---

## Faza 1 — WiFi (LAN) 🔵

Komunikacja w lokalnej sieci WiFi — **fundament całej aplikacji**.

### macOS (Swift)

- [x] **WebSocket serwer** (`ConnectionManager.swift`)  
  `NWListener` na porcie `9876`, przyjmuje binarne wiadomości Protobuf
- [x] **Bonjour mDNS broadcast** (`BonjourBroadcaster.swift`)  
  Ogłasza usługę `_maclink._tcp` — Android ją automatycznie wykrywa
- [x] **Store powiadomień** (`NotificationStore.swift`)  
  Odbiera powiadomienia z telefonu, wyświetla bannery systemowe
- [x] **Menu Bar UI** (`MenuBarView.swift`)  
  Aplikacja w pasku menu: status połączenia + lista powiadomień
- [ ] **Parowanie urządzeń** — dialog "Zaufaj temu urządzeniu?" przy pierwszym połączeniu
- [ ] **Akcje powiadomień** — kliknięcie "Odpowiedz" w menu bar wysyła odpowiedź na telefon
- [ ] **Odrzucanie powiadomień** — dismiss z Mac usuwa powiadomienie też na telefonie

### Android (Kotlin)

- [x] **NSD Discovery** (`NsdDiscovery.kt`)  
  Automatycznie szuka MacLink w sieci, emituje listę przez `StateFlow`
- [x] **WebSocket klient** (`MacLinkClient.kt`)  
  `OkHttp` WS, Handshake, Heartbeat co 30s, Protobuf binary frames
- [x] **NotificationListenerService** (`PhoneNotificationListenerService.kt`)  
  Przechwytuje wszystkie powiadomienia i wysyła je do Mac
- [x] **UI** (`MainScreen.kt`)  
  Ekran discovery urządzeń, status połączenia, connect/disconnect
- [ ] **Odpowiedzi na SMS/Messenger** — obsługa `RemoteInput` z akcji powiadomień
- [ ] **Auto-reconnect** — automatyczne ponowne połączenie z exponential backoff
- [ ] **Ikony aplikacji** — wysyłanie ikony PNG razem z powiadomieniem

### Wspólne

- [ ] **Test end-to-end** — pełny test: discovery → połączenie → powiadomienia → heartbeat
- [ ] **TLS** — szyfrowanie WebSocket (self-signed cert, TOFU przy parowaniu)

---

## Faza 2 — Audio i rozmowy ⬜

Odbieranie i prowadzenie rozmów z poziomu Mac.

- [ ] **Android: WebRTC** — integracja `google/webrtc` AAR, `PeerConnectionFactory`, audio track
- [ ] **macOS: WebRTC** — integracja `libwebrtc`, SDP, ICE, `CoreAudio` output
- [ ] **Android: wykrywanie połączeń** — `TelecomManager` / `PhoneStateListener`  
  Zdarzenia: INCOMING / OUTGOING / ANSWERED / ENDED
- [ ] **macOS: UI połączeń** — panel "Połączenie od X" z przyciskami Odbierz/Odrzuć
- [ ] **Pełne audio** — mikrofon Mac → WebRTC → Android → sieć GSM (i odwrotnie)
- [ ] **DTMF** — klawiatura tonowa z Mac (przydatne dla IVR / automatów)

---

## Faza 3 — Bluetooth ⬜

Alternatywny kanał gdy brak wspólnego WiFi.

- [ ] **BLE discovery** — `CoreBluetooth` (macOS) + `BLE Scan` (Android) jako discovery poza LAN
- [ ] **BLE fallback** — ograniczony kanał BLE do powiadomień gdy WiFi niedostępne

---

## Faza 4 — Internet ⬜

Działa przez internet, poza siecią lokalną.

- [ ] **Signaling server** — Node.js WebSocket relay gdy Mac i Android w różnych sieciach
- [ ] **TURN/STUN server** — `Coturn` na VPS dla WebRTC przez internet
- [ ] **Autentykacja** — powiązanie urządzeń przez konto (bez potrzeby LAN)
- [ ] **E2E encryption** — DTLS (WebRTC) + TLS WebSocket, klucz wymieniany przy parowaniu LAN

---

## Faza 5 — Dodatkowe funkcje ⬜

Funkcje poza połączeniami i powiadomieniami.

- [ ] **SMS na Mac** — historia wiadomości, wysyłanie nowych SMS z klawiatury Mac
- [ ] **Schowek (clipboard)** — kopiuj na Android → wklej na Mac i odwrotnie (limit 1MB)
- [ ] **Wysyłanie plików** — drag & drop plik z Mac → Android (przez WiFi)
- [ ] **Kontakty** — synchronizacja kontaktów Android → macOS `Contacts.framework`
- [ ] **Status baterii** — poziom baterii i siła sygnału Android widoczne w menu bar

---

## Architektura techniczna

```
macOS (Swift/SwiftUI)              Android (Kotlin/Compose)
─────────────────────              ────────────────────────
NWListener :9876  ◄─── WebSocket ───► OkHttp WebSocket
BonjourBroadcaster ◄── mDNS ───────► NsdDiscovery
NotificationStore  ◄── Protobuf ────► NotificationListenerService
WebRTC (faza 2)   ◄─── DTLS ────────► WebRTC AAR (faza 2)
```

**Protokół:** Binary Protobuf w `Envelope` wrapperze  
**Discovery:** mDNS `_maclink._tcp` (automatyczne, zero-config)  
**Port:** `9876` (WebSocket)  
**Szyfrowanie:** TLS (faza 1) + DTLS wbudowane w WebRTC (faza 2)

---

## Wymagania systemowe

| Platforma | Minimalna wersja |
|-----------|-----------------|
| macOS | 13.0 Ventura |
| Android | 10 (API 29) |
| Xcode | 15+ |
| Android Studio | Hedgehog+ |
