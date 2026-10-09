#!/usr/bin/env python3
"""Plan a bounded retry of a connected instrumentation run from its JUnit XML results.

Prints a comma-separated `notClass` value listing every test that already PASSED in the
previous attempt, so the next attempt re-executes only failed tests and tests that never ran
(for example after `device offline` aborted the run). Prints nothing when no passing test was
recorded, which makes the caller repeat the whole requested set. Nothing is ever skipped: a
test leaves the plan only after it has passed on the device.
"""
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def passed_tests(results_dirs):
    passed, failed = set(), set()
    for root_dir in results_dirs:
        for xml_file in sorted(Path(root_dir).rglob("TEST-*.xml")):
            try:
                root = ET.parse(xml_file).getroot()
            except ET.ParseError:
                continue
            for case in root.iter("testcase"):
                cls, name = case.get("classname"), case.get("name")
                if not cls or not name:
                    continue
                test_id = f"{cls}#{name}"
                if any(child.tag in ("failure", "error", "skipped") for child in case):
                    failed.add(test_id)
                else:
                    passed.add(test_id)
    # A test that passed in any attempt is done; only tests that never passed stay in the plan.
    return passed, failed - passed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", nargs="+", help="androidTest-results directories of the previous attempts")
    parser.add_argument("--report", type=Path, help="write a human-readable summary here")
    args = parser.parse_args()
    passed, failed = passed_tests(args.results)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            "passed:\n" + "".join(f"  {t}\n" for t in sorted(passed))
            + "failed:\n" + "".join(f"  {t}\n" for t in sorted(failed))
        )
    sys.stdout.write(",".join(sorted(passed)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
