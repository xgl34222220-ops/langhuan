#!/usr/bin/env python3
"""Run both EPUB instrumentation phases and fail closed on Android/JUnit failures.

Run after installing the debug app and androidTest APK on a running emulator.
Do not use `adb shell am instrument`'s exit code as the only JUnit success check.
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys

TEST_CLASS = "com.xiguli.langhuan.ui.EpubOriginalReaderDeviceTest"
TEST_METHOD = "processDeathRoundTrip"
COMPONENT = "com.xiguli.langhuan.test/androidx.test.runner.AndroidJUnitRunner"
PACKAGE = "com.xiguli.langhuan"


def verify_instrumentation(output: str, test_class: str = TEST_CLASS, test_method: str = TEST_METHOD) -> None:
    terminal = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", output, re.M)
    if terminal != ["-1"]:
        raise RuntimeError("Instrumentation did not report one normal terminal result")
    blocks = []
    current = {}
    for line in output.splitlines():
        match = re.match(r"INSTRUMENTATION_STATUS: ([^=]+)=(.*)", line)
        if match:
            current[match[1]] = match[2]
        match = re.match(r"INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", line)
        if match:
            code = int(match[1])
            if code < 0:
                raise RuntimeError(f"Test failure, ignored test, or assumption skip (status {code})")
            blocks.append((code, current))
            current = {}
    successes = [block for code, block in blocks if code == 0]
    if len(successes) != 1:
        raise RuntimeError("Expected exactly one completed successful test")
    success = successes[0]
    expected = {"class": test_class, "test": test_method, "numtests": "1", "current": "1"}
    if any(success.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Instrumentation did not execute the exact requested single test")
    if not re.search(r"^OK \(1 test\)\s*$", output, re.M):
        raise RuntimeError("Missing JUnit one-test success summary")
    if re.search(r"^(?:FAILURES!!!|INSTRUMENTATION_FAILED:|INSTRUMENTATION_ABORTED:)", output, re.M):
        raise RuntimeError("Instrumentation failure marker present")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--evidence", type=Path, default=Path("reader-qa/epub-process"))
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    (args.evidence / "verified.txt").unlink(missing_ok=True)

    def run(command, label):
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, timeout=180, check=False)
        (args.evidence / f"{label}.log").write_text(result.stdout)
        print(result.stdout, end="", flush=True)
        if result.returncode:
            raise RuntimeError(f"{label} command exited {result.returncode}")
        return result.stdout

    try:
        for phase in ("seed", "restore"):
            if phase == "restore":
                run([args.adb, "shell", "am", "force-stop", PACKAGE], "force-stop")
            output = run([args.adb, "shell", "am", "instrument", "-w", "-r",
                          "-e", "class", f"{TEST_CLASS}#{TEST_METHOD}",
                          "-e", "phase", phase, COMPONENT], phase)
            verify_instrumentation(output)
        (args.evidence / "verified.txt").write_text(
            "Both EPUB phases passed without skips. Restore asserted a different process PID and the saved Locator.\n")
        return 0
    except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
        print(f"EPUB process-death verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
