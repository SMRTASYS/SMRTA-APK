#!/usr/bin/env bash
set -euo pipefail

echo "=== SMRTA.APK | SMRTASYS PORTAL (Linux/macOS Build) ==="

if [ -z "${JAVA_HOME:-}" ]; then
    if command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(command -v java)"
        JAVA_RESOLVED="$(readlink -f "$JAVA_BIN" 2>/dev/null || realpath "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")"
        export JAVA_HOME="$(dirname "$(dirname "$JAVA_RESOLVED")")"
    fi
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: Java JDK 17 not found. Set JAVA_HOME or install JDK." >&2
    exit 1
fi
echo "[OK] Java JDK detected: $JAVA_HOME"
"$JAVA_HOME/bin/java" -version

if [ -z "${ANDROID_HOME:-}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk"; do
        if [ -d "$candidate" ]; then
            export ANDROID_HOME="$candidate"
            break
        fi
    done
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
echo "[OK] Android SDK detected: $ANDROID_HOME"

chmod +x ./gradlew 2>/dev/null || true
printf "sdk.dir=%s
" "$ANDROID_HOME" > local.properties
echo "[OK] local.properties configured."

echo "[..] Running assembleDebug..."
if [ -x "./gradlew" ]; then
    ./gradlew assembleDebug --stacktrace
elif command -v gradle >/dev/null 2>&1; then
    gradle assembleDebug --stacktrace
else
    echo "ERROR: Neither ./gradlew nor gradle is available." >&2
    exit 1
fi

APK_PATH="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: Build completed without producing APK at $APK_PATH" >&2
    exit 1
fi

echo "=== BUILD SUCCEEDED ==="
echo "Debug APK Location: $APK_PATH"
