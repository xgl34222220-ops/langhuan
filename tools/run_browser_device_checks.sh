#!/usr/bin/env bash
# Synthetic CI emulator only; retain the real test exit status and diagnostic evidence.
set -euo pipefail
mkdir -p browser-qa
adb logcat -v threadtime > browser-qa/device-logcat.txt 2>&1 &
browser_logcat_pid=$!
browser_cleanup() {
  browser_exit=$?
  trap - EXIT
  kill "$browser_logcat_pid" 2>/dev/null || true
  wait "$browser_logcat_pid" 2>/dev/null || true
  timeout 20s adb pull /sdcard/Download/reader-qa browser-qa/screens || true
  timeout 10s adb shell dumpsys window windows > browser-qa/final-window.txt || true
  timeout 10s adb exec-out screencap -p > browser-qa/final-screen.png || true
  exit "$browser_exit"
}
trap browser_cleanup EXIT
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=com.xiguli.langhuan.ui.SourceBrowserSessionV56DeviceTest
python3 tools/verify_browser_process_death.py --evidence browser-qa/process
sha256sum app/build/outputs/apk/debug/app-debug.apk > browser-tested-apk.sha256
