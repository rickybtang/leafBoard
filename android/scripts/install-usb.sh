#!/bin/zsh
set -euo pipefail

SCRIPT_DIR=${0:A:h}
ANDROID_DIR=${SCRIPT_DIR:h}
PROJECT_DIR=${ANDROID_DIR:h}
JAVA_RUNTIME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

cd "$ANDROID_DIR"
JAVA_HOME="$JAVA_RUNTIME" ./gradlew :app:assembleDebug
adb install -r "$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
adb shell pm enable io.github.rickybtang.leafboard >/dev/null
adb shell am start -n io.github.rickybtang.leafboard/.MainActivity
adb shell mkdir -p /sdcard/Android/data/io.github.rickybtang.leafboard/files/inbox

echo "USB import directory: /sdcard/Android/data/io.github.rickybtang.leafboard/files/inbox"
echo "Example: adb push $PROJECT_DIR/protocol/examples/quota-card.json /sdcard/Android/data/io.github.rickybtang.leafboard/files/inbox/"
