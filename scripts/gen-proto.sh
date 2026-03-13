#!/bin/bash
# Generuje pliki Protobuf dla macOS (Swift) i Android (Kotlin/Java)
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROTO_DIR="$SCRIPT_DIR/../shared/proto"
MACOS_OUT="$SCRIPT_DIR/../macos-app/MacLink/Sources/Model/Proto"
ANDROID_OUT="$SCRIPT_DIR/../android-app/app/src/main/proto"

echo "🔧 Generowanie plików Protobuf..."

# Swift (macOS)
mkdir -p "$MACOS_OUT"
protoc \
    --swift_out="$MACOS_OUT" \
    --swift_opt=Visibility=Public \
    --proto_path="$PROTO_DIR" \
    "$PROTO_DIR/maclink.proto"

echo "✅ Swift → $MACOS_OUT"

# Kotlin/Java (Android) - Android Studio generuje automatycznie z /proto
mkdir -p "$ANDROID_OUT"
cp "$PROTO_DIR/maclink.proto" "$ANDROID_OUT/"

echo "✅ Android proto → $ANDROID_OUT (Android Studio skompiluje automatycznie)"
echo ""
echo "🎉 Gotowe!"
