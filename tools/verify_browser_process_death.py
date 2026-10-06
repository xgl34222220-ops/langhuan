#!/usr/bin/env python3
"""Verify browser Cookie/DOM-storage restoration across a real CI process boundary."""
import argparse
from pathlib import Path
import subprocess
import sys
from verify_epub_process_death import verify_instrumentation

TEST_CLASS = "com.xiguli.langhuan.ui.SourceBrowserSessionV56DeviceTest"
TEST_METHOD = "browserProfileSurvivesProcessDeath"
COMPONENT = "com.xiguli.langhuan.test/androidx.test.runner.AndroidJUnitRunner"
PACKAGE = "com.xiguli.langhuan"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--evidence", type=Path, default=Path("browser-qa/process"))
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    (args.evidence / "verified.txt").unlink(missing_ok=True)
    try:
        for phase in ("seed", "restore"):
            if phase == "restore":
                stop = subprocess.run([args.adb, "shell", "am", "force-stop", PACKAGE], capture_output=True, text=True, timeout=30, check=True)
                (args.evidence / "force-stop.log").write_text(stop.stdout + stop.stderr)
            run = subprocess.run([args.adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", f"{TEST_CLASS}#{TEST_METHOD}", "-e", "browserPhase", phase, COMPONENT], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
            (args.evidence / f"{phase}.log").write_text(run.stdout)
            print(run.stdout, end="", flush=True)
            if run.returncode: raise RuntimeError(f"{phase} exited {run.returncode}")
            verify_instrumentation(run.stdout, TEST_CLASS, TEST_METHOD)
        (args.evidence / "verified.txt").write_text("Both phases passed without skips. Restore asserted a fresh app PID, the persistent Cookie and DOM storage.\n")
        return 0
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        print(f"Browser process restoration failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
