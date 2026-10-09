#!/usr/bin/env bash
# Preserve bounded diagnostic evidence from the synthetic CI emulator even if it disconnects.
# Every existing test and process-restore gate keeps its original failing exit status.
set -euo pipefail
reader_fixture_url=${1:?controlled source fixture URL required}
mkdir -p reader-qa/live reader-qa/retries
reader_start_evidence() {
  adb logcat -v threadtime 'CreativeWritingV135:I' 'CreativeStoryV135:I' 'EditorAtomicityV136:I' 'EditorProfileV137:I' 'AndroidRuntime:E' '*:W' >> reader-qa/device-logcat.txt 2>&1 &
  reader_logcat_pid=$!
  (
    while timeout 5s adb get-state >/dev/null 2>&1; do
      timeout 10s adb pull /sdcard/Download/reader-qa reader-qa/live >/dev/null 2>&1 || true
      timeout 10s adb pull /sdcard/Android/data/com.xiguli.langhuan/files/reader-qa reader-qa/live >/dev/null 2>&1 || true
      sleep 15
    done
  ) &
  reader_evidence_pid=$!
}
reader_stop_evidence() {
  kill "$reader_logcat_pid" "$reader_evidence_pid" 2>/dev/null || true
  wait "$reader_logcat_pid" "$reader_evidence_pid" 2>/dev/null || true
}
# Wait until the emulator is online, booted and its package manager answers, then settle.
reader_wait_for_settled_device() {
  timeout 120s adb wait-for-device || return 1
  for _ in $(seq 1 90); do
    if [ "$(timeout 10s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
      && timeout 20s adb shell pm path android >/dev/null 2>&1; then
      timeout 10s adb shell input keyevent 82 >/dev/null 2>&1 || true
      sleep 10
      return 0
    fi
    sleep 2
  done
  return 1
}
# Recover from a transient `device offline` before a retry: reconnect, else restart the adb server.
reader_recover_device() {
  echo "::group::Recover emulator connection"
  reader_stop_evidence
  adb devices -l || true
  timeout 20s adb reconnect offline || true
  if ! reader_wait_for_settled_device; then
    adb kill-server || true
    sleep 3
    adb start-server
    reader_wait_for_settled_device
  fi
  adb devices -l
  reader_start_evidence
  echo "::endgroup::"
}
reader_results_dir=app/build/outputs/androidTest-results
# Run connected tests; on failure retry (at most twice, after device recovery) only the tests
# that have not passed yet. Every requested test must still pass on the device; failing
# assertions are never skipped and the job fails if a test is still failing after the retries.
reader_connected_tests() {
  local label=$1
  shift
  local attempt not_class status
  local previous=()
  for attempt in 1 2 3; do
    rm -rf "$reader_results_dir" app/build/reports/androidTests
    not_class=""
    if [ "$attempt" -gt 1 ]; then
      not_class=$(python3 tools/reader_device_retry_plan.py "${previous[@]}" --report "reader-qa/retries/$label-before-attempt-$attempt.txt")
      echo "Retrying $label (attempt $attempt/3); tests that already passed are excluded."
    fi
    status=0
    if [ -n "$not_class" ]; then
      gradle --no-daemon :app:connectedDebugAndroidTest "$@" -Pandroid.testInstrumentationRunnerArguments.notClass="$not_class" || status=$?
    else
      gradle --no-daemon :app:connectedDebugAndroidTest "$@" || status=$?
    fi
    mkdir -p "reader-qa/retries/$label-attempt-$attempt"
    cp -R "$reader_results_dir" "reader-qa/retries/$label-attempt-$attempt/" 2>/dev/null || true
    cp -R app/build/reports/androidTests "reader-qa/retries/$label-attempt-$attempt/" 2>/dev/null || true
    previous+=("reader-qa/retries/$label-attempt-$attempt")
    if [ "$status" -eq 0 ]; then
      return 0
    fi
    if [ "$attempt" -lt 3 ]; then
      reader_recover_device
    fi
  done
  return "$status"
}
reader_gradle_args=(-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.sourceFixtureBase="$reader_fixture_url")
reader_start_evidence
reader_cleanup() {
  reader_exit=$?
  trap - EXIT
  reader_stop_evidence
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
# Recheck original failures and browser/search/catalogue recovery before the full gates.
# These focused results never replace the complete suite or process-restoration gates.
reader_regression_cases="com.xiguli.langhuan.ui.ReaderBookmarkV49DeviceTest#legacyRecoveryRequiresAnExplicitChoiceAndCancelKeepsAllData,com.xiguli.langhuan.ui.ReaderProgressV42DeviceTest#insertingMissingChaptersKeepsTheSameChapterAndSentence,com.xiguli.langhuan.ui.ReaderRecreationV42DeviceTest#longChapterAndChangedFontStayInReaderAfterActivityRecreation,com.xiguli.langhuan.ui.ReaderWritingCopyV53DeviceTest#readerAiActionCreatesIndependentDraftAndCancelLeavesNoProject,com.xiguli.langhuan.ui.SourceEditingV41DeviceTest#sourceDraftSurvivesActivityRecreationAndCancelDoesNotWrite,com.xiguli.langhuan.ui.CreativeStoryV135DeviceTest#clearingChatWhileHttpReplyIsDelayedCannotResurrectIt,com.xiguli.langhuan.ui.SourceBrowserSessionV56DeviceTest#verificationRecreationWaitsForTheUserAndKeepsTheResultingCookie,com.xiguli.langhuan.ui.SourceBrowserSessionV56DeviceTest#rendererExitWhileVerifyingReleasesTheWaitAndRetryKeepsTheProfile,com.xiguli.langhuan.ui.SourceSearchRecoveryV57DeviceTest,com.xiguli.langhuan.ui.SourceCatalogueRecoveryV58DeviceTest,com.xiguli.langhuan.ui.EpubOriginalReaderDeviceTest#reflowRendererExitKeepsConfirmedLocatorAndManualRetryRestoresSecureArtwork,com.xiguli.langhuan.ui.EpubOriginalReaderDeviceTest#fixedRendererExitKeepsConfirmedPageAndRecreationWaitsForManualRetry"
reader_connected_tests reader-regression-focused "${reader_gradle_args[@]}" -Pandroid.testInstrumentationRunnerArguments.class="$reader_regression_cases"
mkdir -p reader-qa/reader-regression-focused
cp -R app/build/reports/androidTests reader-qa/reader-regression-focused/
cp -R app/build/outputs/androidTest-results reader-qa/reader-regression-focused/
# Exercise the editor profile/save transaction before the existing complete gates.
reader_connected_tests editor-profile-focused "${reader_gradle_args[@]}" -Pandroid.testInstrumentationRunnerArguments.class=com.xiguli.langhuan.ui.CreativeEditorProfileSaveV137DeviceTest
mkdir -p reader-qa/editor-profile-focused
cp -R app/build/reports/androidTests reader-qa/editor-profile-focused/
cp -R app/build/outputs/androidTest-results reader-qa/editor-profile-focused/
# Fail early on the full creation chain, editor reentry and Room fault/cancellation checks.
# A successful focused check never substitutes for the original full-suite/process gates below.
reader_connected_tests creative-focused "${reader_gradle_args[@]}" -Pandroid.testInstrumentationRunnerArguments.class=com.xiguli.langhuan.ui.CreativeWritingV135DeviceTest,com.xiguli.langhuan.ui.CreativeEditorEntryV136DeviceTest,com.xiguli.langhuan.data.CreativeEditorAtomicityV136DeviceTest
mkdir -p reader-qa/creative-focused
cp -R app/build/reports/androidTests reader-qa/creative-focused/
cp -R app/build/outputs/androidTest-results reader-qa/creative-focused/
reader_connected_tests full-suite "${reader_gradle_args[@]}"
reader_wait_for_settled_device
# The two-phase process-death gate is re-run whole (never partially) after a device recovery.
for reader_process_attempt in 1 2 3; do
  if python3 tools/verify_epub_process_death.py --evidence reader-qa/epub-process; then
    break
  fi
  [ "$reader_process_attempt" -lt 3 ] || exit 1
  cp -R reader-qa/epub-process "reader-qa/retries/epub-process-attempt-$reader_process_attempt" || true
  reader_recover_device
done
adb pull /sdcard/Android/data/com.xiguli.langhuan/files/epub-evidence reader-qa/epub-artwork
adb pull /sdcard/Download/reader-qa reader-qa
