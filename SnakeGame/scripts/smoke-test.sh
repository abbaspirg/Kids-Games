#!/usr/bin/env bash
# Installs the debug APK on a running emulator, launches it and reports whether it stays open.
set -u
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.kidsgames.snake

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 10
# Touch positions scale with the emulator's screen size.
SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
echo "Screen ${W}x${H}"
adb shell input tap $((W / 2)) $((H / 2))
sleep 2
adb shell input swipe $((W / 2)) $((H / 2)) $((W * 9 / 10)) $((H / 2)) 120
sleep 2
adb shell input swipe $((W / 2)) $((H / 2)) $((W / 2)) $((H * 9 / 10)) 120
sleep 3

mkdir -p smoke
adb exec-out screencap -p > smoke/screen.png
echo "===== crash buffer ====="
adb logcat -d -b crash | tee smoke/crash.txt
echo "===== app log ====="
adb logcat -d | grep -E "AndroidRuntime|$PKG|FATAL|SoundPool|AudioTrack" | tail -80 | tee smoke/app.txt

if adb shell pidof "$PKG" > /dev/null; then
  echo "SMOKE TEST PASSED: $PKG is running"
else
  echo "SMOKE TEST FAILED: $PKG is not running"
  exit 1
fi
