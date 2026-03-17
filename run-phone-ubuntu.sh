#!/usr/bin/env bash
set -euo pipefail

PKG="com.privacy.faraday"
cd "$(dirname "$0")"

# Use Android Studio's bundled JDK
export JAVA_HOME="$HOME/Downloads/android-studio-panda2-linux/android-studio/jbr"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Build and install
echo "Building and installing..."
./gradlew :app:installDebug

# Find connected device (first one if multiple)
DEVICE=$(adb devices | awk 'NR==2 && $2=="device" {print $1}')
if [ -z "$DEVICE" ]; then
    echo "No device found. Is your phone connected with USB debugging enabled?"
    exit 1
fi

# Launch the app
echo "Launching on $DEVICE..."
adb -s "$DEVICE" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
echo "Installed + launched on $DEVICE"
