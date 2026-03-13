#!/bin/bash
# Tworzy projekt Xcode dla MacLink (macOS Menu Bar App)
# Wymaga zainstalowanego Xcode

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/../macos-app"

echo "🔍 Sprawdzam Xcode..."

if ! xcodebuild -version &>/dev/null; then
    echo ""
    echo "❌ Xcode nie jest zainstalowany lub nie jest aktywny."
    echo ""
    echo "📥 KROK 1: Zainstaluj Xcode z Mac App Store:"
    echo "   https://apps.apple.com/pl/app/xcode/id497799835"
    echo ""
    echo "📥 KROK 2: Po instalacji uruchom Xcode raz (zaakceptuje licencję)"
    echo ""
    echo "📥 KROK 3: Uruchom ponownie ten skrypt:"
    echo "   bash scripts/setup-xcode.sh"
    exit 1
fi

XCODE_VER=$(xcodebuild -version | head -1)
echo "✅ $XCODE_VER"

echo ""
echo "📦 Otwieranie projektu Swift Package w Xcode..."
echo "   Xcode automatycznie pobierze zależności (swift-protobuf)"
echo ""

open "$PROJECT_DIR/Package.swift"

echo "✅ Xcode powinien się otworzyć z projektem MacLink"
echo ""
echo "▶️  Aby uruchomić:"
echo "   1. W Xcode wybierz schemat 'MacLink' (lewy górny róg)"
echo "   2. Kliknij ▶ (Run) lub Cmd+R"
echo "   3. Aplikacja pojawi się w pasku menu (górny prawy róg ekranu)"
