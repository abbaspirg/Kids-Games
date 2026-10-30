#!/usr/bin/env bash
# Installs the debug APK on a running emulator, launches it and reports whether it stays open.
set -u
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.kidsgames.snake

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 10
adb shell input tap 500 1200
sleep 3
adb shell input swipe 500 1200 900 1200 150
sleep 3

mkdir -p smoke
adb exec-out screencap -p > smoke/screen.png
echo "===== crash buffer ====="
adb logcat -d -b crash | tee smoke/crash.txt
echo "===== app log ====="
adb logcat -d | grep -E "AndroidRuntime|$PKG|FATAL" | tail -80 | tee smoke/app.txt

if adb shell pidof "$PKG" > /dev/null; then
  echo "SMOKE TEST PASSED: $PKG is running"
else
  echo "SMOKE TEST FAILED: $PKG is not running"
  exit 1
fi
