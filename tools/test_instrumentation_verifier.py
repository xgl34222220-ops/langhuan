"""Check failure detection without claiming an emulator run."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
from tempfile import TemporaryDirectory
import subprocess
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO

spec = importlib.util.spec_from_file_location("epub_verifier", Path(__file__).with_name("verify_epub_process_death.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)

FIELDS = (f"INSTRUMENTATION_STATUS: class={verifier.TEST_CLASS}\n"
          f"INSTRUMENTATION_STATUS: test={verifier.TEST_METHOD}\n"
          "INSTRUMENTATION_STATUS: current=1\nINSTRUMENTATION_STATUS: numtests=1\n")
VALID = FIELDS + "INSTRUMENTATION_STATUS_CODE: 1\n" + FIELDS + "INSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_RESULT: stream=\nTime: 1.0\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n"

class VerifierTest(unittest.TestCase):
    def test_exact_single_success(self):
        verifier.verify_instrumentation(VALID)
    def test_failure_skip_or_error_status_never_passes(self):
        for code in [-1, -2, -3, -4]:
            with self.subTest(code=code), self.assertRaises(RuntimeError):
                verifier.verify_instrumentation(VALID.replace('INSTRUMENTATION_STATUS_CODE: 0', f'INSTRUMENTATION_STATUS_CODE: {code}'))
    def test_wrong_test_empty_run_and_aborted_run_never_pass(self):
        variants = [VALID.replace(verifier.TEST_METHOD, 'otherTest'),
                    VALID.replace('numtests=1', 'numtests=0'),
                    VALID.replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'),
                    VALID.replace('OK (1 test)', 'FAILURES!!!'),
                    VALID.replace(FIELDS + 'INSTRUMENTATION_STATUS_CODE: 0\n', ''),
                    VALID + 'INSTRUMENTATION_FAILED: crashed\n']
        for index, output in enumerate(variants):
            with self.subTest(index=index), self.assertRaises(RuntimeError):
                verifier.verify_instrumentation(output)

class PhaseCommandTest(unittest.TestCase):
    def run_case(self, outputs):
        with TemporaryDirectory(dir=Path(__file__).parent) as evidence:
            with patch('sys.argv', ['verify', '--evidence', evidence]), patch.object(verifier.subprocess, 'run', side_effect=[subprocess.CompletedProcess([], 0, output) for output in outputs]) as command:
                with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                    result = verifier.main()
                return result, command.call_count, (Path(evidence) / 'verified.txt').exists()
    def test_seed_failure_with_zero_adb_exit_stops_the_run(self):
        result, calls, marker = self.run_case([VALID.replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -2')])
        self.assertEqual((1, 1, False), (result, calls, marker))
    def test_restore_skip_with_zero_adb_exit_fails_the_run(self):
        result, calls, marker = self.run_case([VALID, '', VALID.replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -3')])
        self.assertEqual((1, 3, False), (result, calls, marker))
    def test_both_phases_must_pass_to_publish_evidence(self):
        self.assertEqual((0, 3, True), self.run_case([VALID, '', VALID]))

if __name__ == '__main__':
    unittest.main()
