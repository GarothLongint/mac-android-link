#!/bin/bash
# MacLink — główny skrypt setup
# Uruchom go jako PIERWSZĄ rzecz po sklonowaniu projektu

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "╔══════════════════════════════════════╗"
echo "║        MacLink — Setup               ║"
echo "╚══════════════════════════════════════╝"
echo ""

# 1. Sprawdź Homebrew
if ! which brew &>/dev/null; then
    echo "📥 Instaluję Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
else
    echo "✅ Homebrew OK"
fi

# 2. protoc + swift-protobuf
if ! which protoc &>/dev/null; then
    echo "📥 Instaluję protoc + swift-protobuf..."
    brew install protobuf swift-protobuf
else
    echo "✅ protoc OK ($(protoc --version))"
fi

if ! which protoc-gen-swift &>/dev/null; then
    brew install swift-protobuf
else
    echo "✅ protoc-gen-swift OK"
fi

# 3. Generuj Protobuf
echo ""
echo "🔧 Generuję pliki Protobuf..."
bash "$SCRIPT_DIR/gen-proto.sh"

# 4. Xcode
echo ""
if ! xcodebuild -version &>/dev/null 2>&1; then
    echo "⚠️  WYMAGANE: Zainstaluj Xcode z App Store:"
    echo "   https://apps.apple.com/pl/app/xcode/id497799835"
    echo ""
    echo "   Po instalacji uruchom: bash scripts/setup-xcode.sh"
else
    echo "✅ Xcode OK ($(xcodebuild -version | head -1))"
fi

# 5. Android Studio
echo ""
if [ ! -d "/Applications/Android Studio.app" ]; then
    echo "⚠️  WYMAGANE: Zainstaluj Android Studio:"
    echo "   https://developer.android.com/studio"
    echo ""
    echo "   Po instalacji uruchom: bash scripts/setup-android.sh"
else
    echo "✅ Android Studio OK"
fi

echo ""
echo "╔══════════════════════════════════════╗"
echo "║  Setup zakończony!                   ║"
echo "║                                      ║"
echo "║  Następne kroki:                     ║"
echo "║  • bash scripts/setup-xcode.sh      ║"
echo "║  • bash scripts/setup-android.sh    ║"
echo "╚══════════════════════════════════════╝"
