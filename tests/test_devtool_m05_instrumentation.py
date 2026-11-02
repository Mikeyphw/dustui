"""M05 signed release-only instrumentation source contracts; no Android Gradle on Termux."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import dustui_m05_instrumentation as ci
import dustui_m05_instrumentation_device as device

class M05SignedInstrumentation(unittest.TestCase):
    def test_release_only_variant_is_conditional(self):
        app=(ROOT/'android/app/build.gradle.kts').read_text()
        self.assertIn('providers.environmentVariable("DUSTUI_M05_INSTRUMENTATION_BUILD").orNull == "true"',app)
        self.assertIn('testBuildType = "release"',app)
        self.assertIn('testApplicationId = "com.mikeyphw.dustui.test"',app)
        self.assertIn('applicationIdSuffix = ".debug"',app)

    def test_no_privileged_suite_exposed_by_runner(self):
        self.assertEqual(ci.ALLOWLIST,device.ALLOWLIST)
        self.assertEqual(len(ci.ALLOWLIST),3)
        self.assertFalse(any('RootedDestructive' in x or 'DeviceScale' in x for x in ci.ALLOWLIST))
        runner=(ROOT/'scripts/dustui_m05_instrumentation_device.py').read_text()
        self.assertIn("'shell','am','instrument'",runner)
        self.assertIn("'release_qualified':False",runner)
        self.assertNotIn('adb uninstall',runner)
        self.assertNotIn('pm clear',runner)

    def test_tested_app_and_signing_identity(self):
        self.assertEqual(ci.TARGET,device.RELEASE_PKG)
        self.assertEqual(ci.TEST_PKG,device.TEST_PKG)
        self.assertEqual(ci.TEST_RUNNER,device.RUNNER)
        self.assertRegex(device.EXPECTED_CERT,r'^[0-9a-f]{64}$')
        kt=(ROOT/'android/app/src/androidTest/kotlin/com/mikeyphw/dustui/go/M05ReleaseReadOnlyInstrumentedTest.kt').read_text()
        self.assertIn('"domain.schema.verify"',kt)
        self.assertNotIn('executeCleanup(',kt)
        self.assertNotIn('RootServiceClient',kt)

    def test_hosted_workflow_is_manual_restricted_and_cleans_key(self):
        wf=(ROOT/'.github/workflows/devtool-dustui-m05-signed-instrumentation.yml').read_text()
        self.assertIn('workflow_dispatch:',wf)
        self.assertNotIn('pull_request:',wf)
        self.assertNotIn('\n  push:',wf)
        self.assertIn("github.actor == 'Mikeyphw'",wf)
        self.assertIn("github.ref == 'refs/heads/main'",wf)
        self.assertIn('name: dustui-release',wf)
        self.assertIn('cache-disabled: true',wf)
        self.assertIn('DUSTUI_M05_INSTRUMENTATION_BUILD:',wf)
        self.assertIn('rm -f "$RUNNER_TEMP/dustui-m05-private/',wf)

    def test_unsigned_or_unqualified_rejections(self):
        a='Signer #1 certificate SHA-256 digest: '+device.EXPECTED_CERT+'\nVerified using v2 scheme (APK Signature Scheme v2): true\n'
        self.assertEqual(ci.signing_sha(a),device.EXPECTED_CERT)
        with self.assertRaises(ci.ReleaseError):ci.signing_sha(a.replace('true','false'))
        with self.assertRaises(ci.ReleaseError):ci.signing_sha('Not signed')
        with patch.dict('os.environ',{'GITHUB_ACTIONS':'false','DUSTUI_M05_INSTRUMENTATION_BUILD':'true'},clear=True):
            with self.assertRaises(Exception):ci.guard(ROOT)

if __name__=='__main__':unittest.main()
