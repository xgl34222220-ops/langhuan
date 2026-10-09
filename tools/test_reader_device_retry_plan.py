import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("retry_plan", Path(__file__).with_name("reader_device_retry_plan.py"))
retry_plan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retry_plan)
passed_tests = retry_plan.passed_tests

XML = """<?xml version='1.0' encoding='UTF-8' ?>
<testsuite name="s" tests="{n}">{cases}</testsuite>
"""


def write(directory, name, cases):
    path = Path(directory) / "connected" / "debug" / f"TEST-{name}.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(XML.format(n=len(cases), cases="".join(cases)))


class RetryPlanTest(unittest.TestCase):
    def test_failed_and_errored_tests_stay_in_the_plan_until_they_pass(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            write(first, "a", [
                '<testcase classname="p.A" name="ok"/>',
                '<testcase classname="p.A" name="bad"><failure>x</failure></testcase>',
                '<testcase classname="p.B" name="offline"><error>device offline</error></testcase>',
                '<testcase classname="p.B" name="ignored"><skipped/></testcase>',
            ])
            passed, failed = passed_tests([first])
            self.assertEqual(passed, {"p.A#ok"})
            self.assertEqual(failed, {"p.A#bad", "p.B#offline", "p.B#ignored"})
            write(second, "a", ['<testcase classname="p.A" name="bad"/>'])
            passed, failed = passed_tests([first, second])
            self.assertEqual(passed, {"p.A#ok", "p.A#bad"})
            self.assertEqual(failed, {"p.B#offline", "p.B#ignored"})

    def test_missing_results_exclude_nothing(self):
        with tempfile.TemporaryDirectory() as empty:
            self.assertEqual(passed_tests([empty]), (set(), set()))


if __name__ == "__main__":
    unittest.main()
