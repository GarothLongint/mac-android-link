#!/bin/bash
# Sprawdza i konfiguruje środowisko Android Studio + projekt
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/../android-app"

echo "🔍 Sprawdzam środowisko Android..."

# Android Studio
ANDROID_STUDIO_PATH=""
if [ -d "/Applications/Android Studio.app" ]; then
    ANDROID_STUDIO_PATH="/Applications/Android Studio.app"
elif [ -d "$HOME/Applications/Android Studio.app" ]; then
    ANDROID_STUDIO_PATH="$HOME/Applications/Android Studio.app"
fi

if [ -z "$ANDROID_STUDIO_PATH" ]; then
    echo ""
    echo "❌ Android Studio nie jest zainstalowane."
    echo ""
    echo "📥 KROK 1: Pobierz Android Studio:"
    echo "   https://developer.android.com/studio"
    echo "   (plik .dmg, ~1.2GB)"
    echo ""
    echo "📥 KROK 2: Zainstaluj (przeciągnij do /Applications)"
    echo ""
    echo "📥 KROK 3: Uruchom Android Studio, przejdź przez kreator:"
    echo "   - Standard setup"
    echo "   - Pobierze Android SDK automatycznie"
    echo ""
    echo "📥 KROK 4: Uruchom ponownie ten skrypt"
    exit 1
fi

echo "✅ Android Studio: $ANDROID_STUDIO_PATH"

# Sprawdź SDK
ANDROID_SDK="$HOME/Library/Android/sdk"
if [ ! -d "$ANDROID_SDK" ]; then
    echo "⚠️  Android SDK nie znaleziono w $ANDROID_SDK"
    echo "   Uruchom Android Studio i przejdź przez kreator konfiguracji"
    exit 1
fi

echo "✅ Android SDK: $ANDROID_SDK"

# Otwórz projekt
echo ""
echo "📂 Otwieranie projektu Android w Android Studio..."
open -a "$ANDROID_STUDIO_PATH" "$ANDROID_DIR"

echo ""
echo "✅ Android Studio powinno się otworzyć z projektem"
echo ""
echo "▶️  Aby uruchomić na telefonie:"
echo "   1. Na telefonie: Ustawienia → O telefonie → Kliknij 7x na 'Numer kompilacji'"
echo "      (włącza Opcje Programisty)"
echo "   2. Ustawienia → Opcje Programisty → USB Debugging → Włącz"
echo "   3. Podłącz telefon kablem USB"
echo "   4. W Android Studio kliknij ▶ (Run) — wybierz swój telefon"
echo ""
echo "📱 Po pierwszym uruchomieniu na WiFi możesz odłączyć USB:"
echo "   Android Studio → Devices → telefon → WiFi icon"
