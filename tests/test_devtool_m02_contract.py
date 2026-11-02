"""M02 topology and negative-mutation tests (stdlib, no Devtool runtime/Gradle)."""
from __future__ import annotations
import copy
import pathlib
import sys
import tomllib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from devtool_actions_contract import check_document, ContractError


class M02Contract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = tomllib.loads((ROOT / '.devtool.toml').read_text(encoding='utf-8'))

    def test_expected_layout(self):
        self.assertEqual(check_document(self.raw, ROOT)['status'], 'PASS')

    def test_no_history_targets_or_commands(self):
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_gate_a_history'] = {'type': 'command'}
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)
        raw = copy.deepcopy(self.raw)
        raw['wrapper']['commands']['gate-e-release'] = {'target':'dustui_go', 'workflow':'go-test'}
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

    def test_no_local_gradle_or_android_wrapper(self):
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_go']['jobs']['test']['command'] = ['bash', 'android/gradlew', ':app:testDebugUnitTest']
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)
        raw = copy.deepcopy(self.raw)
        raw['wrapper']['commands']['go-test']['target'] = 'dustui_hosted_r36'
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

    def test_hosted_go_refuses_shell_wrappers(self):
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_hosted_go']['jobs']['test']['command'] = ['sh','-lc','go test ./...']
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

    def test_race_requires_native_linux_cgo(self):
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_hosted_go']['jobs']['race']['env']['CGO_ENABLED'] = '0'
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_hosted_go']['workflows']['ci'][3]['depends_on'] = ['test', 'vet']
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

    def test_r36_stays_debug_only(self):
        raw = copy.deepcopy(self.raw)
        raw['targets']['dustui_hosted_r36']['jobs']['debug_apk']['gradle_tasks'] = [':app:assembleRelease']
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

    def test_scheduler_bounded(self):
        raw = copy.deepcopy(self.raw)
        raw['workspace']['scheduler']['max_parallel_nodes'] = 99
        with self.assertRaises(ContractError):
            check_document(raw, ROOT)

if __name__ == '__main__':
    unittest.main()
