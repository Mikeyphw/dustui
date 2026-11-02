"""Source-only hosted R36 DustUI checks; no Gradle, Android SDK or device."""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tomllib
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import dustui_r36_hosted as gate


class R36ConsumerTests(unittest.TestCase):
    def test_r36_debug_isolated_from_current_local_go(self):
        raw = tomllib.loads((ROOT / '.devtool.toml').read_text())
        self.assertEqual(set(raw['targets']), {'dustui_go', 'dustui_hosted_go', 'dustui_hosted_r36'})
        self.assertEqual(raw['workspace']['default_target'], 'dustui_go')
        self.assertEqual(raw['wrapper']['default_command'], 'ci-preflight')
        self.assertEqual(raw['targets']['dustui_go']['root'], 'go')
        self.assertEqual(raw['targets']['dustui_hosted_r36']['jobs']['debug_apk']['gradle_tasks'], gate.TASKS)
        self.assertNotIn('dustui_android', raw['targets'])
        for name, row in raw['wrapper']['commands'].items():
            self.assertEqual(row['target'], 'dustui_go', name)
            self.assertNotIn('gradle', name.lower())

    def test_reviewed_project_matches_pins(self):
        from devtool.actions_graph_export import workflow_yaml
        from devtool.actions_language_providers import provider_plan
        p = provider_plan(ROOT, target=gate.TARGET, workflow=gate.WORKFLOW, devtool_sha=gate.DEVTOOL_SHA)
        self.assertEqual(len(p['nodes']), 1)
        r = p['nodes'][0]['provider']
        self.assertEqual(r['tasks'], gate.TASKS)
        self.assertEqual(r['java_version'], '21')
        self.assertEqual(r['wrapper_version'], '9.7.1')
        self.assertEqual(r['project_dir'], 'android')
        self.assertTrue(r['semantic_request'])
        self.assertEqual(r['wrapper_jar_sha256'], hashlib.sha256((ROOT / 'android/gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest())
        graph = gate.checked_graph(ROOT)
        self.assertIn('devtool.actions_graph_export run-node', workflow_yaml(graph))
        self.assertFalse(r['release_qualified'])

    def test_local_run_refused_even_if_plan_works(self):
        for key in ('GITHUB_ACTIONS', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT'):
            with mock.patch.dict(os.environ, {key: ''}):
                self.assertEqual(gate.main(['run', '--root', str(ROOT), '--inputs', '/tmp/none', '--outputs', '/tmp/none']), 2)
                break

    def test_hosted_workflow_has_no_direct_gradle_execution(self):
        path = ROOT / '.github/workflows/devtool-dustui-r36-hosted-debug.yml'
        text = path.read_text()
        self.assertIn('scripts/dustui_r36_hosted.py run', text)
        self.assertIn('scripts/dustui_r36_hosted.py plan', text)
        self.assertIn('actions/setup-go@d35c59', text)
        self.assertIn('ndk;28.2.13676358', text)
        self.assertIn(gate.DEVTOOL_SHA, text)
        self.assertNotIn('assembleRelease', text)
        self.assertNotIn('./gradlew ', text)
        self.assertNotIn('secrets.', text)

    def test_release_and_device_tasks_are_not_admitted(self):
        from devtool.actions_android_provider import AndroidGradleProvider, AndroidProviderError
        from devtool.config import load_wrapper_project_config
        cfg = load_wrapper_project_config(str(ROOT), None)
        job = cfg.targets[gate.TARGET].jobs['debug_apk']
        provider = AndroidGradleProvider()
        for bad in (':app:assembleRelease', ':app:connectedDebugAndroidTest'):
            with self.subTest(task=bad):
                with self.assertRaises(AndroidProviderError):
                    provider.inspect(ROOT, job, ['./gradlew', '--no-daemon', bad])

if __name__ == '__main__':
    unittest.main()
