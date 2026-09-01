#!/bin/zsh
set -euo pipefail

SCRIPT_DIR=${0:A:h}
PROJECT_DIR=${SCRIPT_DIR:h}
APP_DIR="$PROJECT_DIR/dist/LeafBoard Hub.app"
HELPER_DIR="$APP_DIR/Contents/Helpers"
SIGNING_IDENTITY="LeafBoard Local Signing"

if ! security find-identity -v -p codesigning | grep -Fq "\"$SIGNING_IDENTITY\""; then
    echo "缺少代码签名身份：$SIGNING_IDENTITY" >&2
    echo "首次源码构建请先运行：macos/scripts/setup-local-signing.sh" >&2
    exit 1
fi

cd "$PROJECT_DIR"
swift build -c release
mkdir -p "$APP_DIR/Contents/MacOS"
mkdir -p "$HELPER_DIR"
cp "$PROJECT_DIR/.build/release/LeafBoardHub" "$APP_DIR/Contents/MacOS/LeafBoardHub"
cp "$PROJECT_DIR/.build/release/LeafBoardCredentialHelper" "$HELPER_DIR/LeafBoardCredentialHelper"
cp "$SCRIPT_DIR/Info.plist" "$APP_DIR/Contents/Info.plist"
codesign --force --sign "$SIGNING_IDENTITY" \
    --identifier io.github.rickybtang.leafboard.credential-helper \
    "$HELPER_DIR/LeafBoardCredentialHelper"
codesign --force --sign "$SIGNING_IDENTITY" "$APP_DIR"
codesign --verify --deep --strict "$APP_DIR"

echo "$APP_DIR"
