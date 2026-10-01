#!/usr/bin/env bash
# Preserve bounded diagnostic evidence from the synthetic CI emulator even if it disconnects.
# Every existing test and process-restore gate keeps its original failing exit status.
set -euo pipefail
reader_fixture_url=${1:?controlled source fixture URL required}
mkdir -p reader-qa/live
adb logcat -v threadtime 'CreativeWritingV135:I' 'CreativeStoryV135:I' 'AndroidRuntime:E' '*:W' > reader-qa/device-logcat.txt 2>&1 &
reader_logcat_pid=$!
(
  while timeout 5s adb get-state >/dev/null 2>&1; do
    timeout 10s adb pull /sdcard/Download/reader-qa reader-qa/live >/dev/null 2>&1 || true
    timeout 10s adb pull /sdcard/Android/data/com.xiguli.langhuan/files/reader-qa reader-qa/live >/dev/null 2>&1 || true
    sleep 15
  done
) &
reader_evidence_pid=$!
reader_cleanup() {
  reader_exit=$?
  trap - EXIT
  kill "$reader_logcat_pid" "$reader_evidence_pid" 2>/dev/null || true
  wait "$reader_logcat_pid" "$reader_evidence_pid" 2>/dev/null || true
  if [ "$reader_exit" -ne 0 ]; then
    free -h > reader-qa/host-memory.txt
    df -h > reader-qa/host-storage.txt
    sudo -n dmesg 2>/dev/null | grep -Ei 'out of memory|oom|killed process|segfault' > reader-qa/host-memory-errors.txt || true
    timeout 20s adb pull /sdcard/Download/reader-qa reader-qa || true
    timeout 20s adb pull /sdcard/Android/data/com.xiguli.langhuan/files/reader-qa reader-qa || true
    timeout 20s adb pull /sdcard/Android/data/com.xiguli.langhuan/files/epub-evidence reader-qa/epub-artwork || true
  fi
  exit "$reader_exit"
}
trap reader_cleanup EXIT
# Fail early on the full user-visible creation chain before spending time on the aggregate suite.
# A successful focused check never substitutes for the original full-suite/process gates below.
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.sourceFixtureBase="$reader_fixture_url" -Pandroid.testInstrumentationRunnerArguments.class=com.xiguli.langhuan.ui.CreativeWritingV135DeviceTest
mkdir -p reader-qa/creative-focused
cp -R app/build/reports/androidTests reader-qa/creative-focused/
gradle --no-daemon :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.sourceFixtureBase="$reader_fixture_url"
python3 tools/verify_epub_process_death.py --evidence reader-qa/epub-process
adb pull /sdcard/Android/data/com.xiguli.langhuan/files/epub-evidence reader-qa/epub-artwork
adb pull /sdcard/Download/reader-qa reader-qa
