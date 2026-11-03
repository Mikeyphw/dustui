"""M05 signed release-only instrumentation source contracts; no Android Gradle on Termux."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
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

    def test_aapt2_badging_with_label_and_reordered_fields(self):
        # Real-world aapt2 layouts may contain label between identity fields.
        from dustui_m05_badging import parse_badging
        a = (
            "package: versionCode='1400' name='com.mikeyphw.dustui.test' versionName='1.4.0'\n"
            "instrumentation: name='androidx.test.runner.AndroidJUnitRunner' "
            "label='' targetPackage='com.mikeyphw.dustui'\n"
        )
        b = (
            "package: name='com.mikeyphw.dustui.test' versionCode='1400'\n"
            "instrumentation: targetPackage='com.mikeyphw.dustui' "
            "functionalTest='false' name='androidx.test.runner.AndroidJUnitRunner'\n"
        )
        expected = ((ci.TEST_PKG, '1400'), (ci.TEST_RUNNER, ci.TARGET))
        self.assertEqual(parse_badging(a), expected)
        self.assertEqual(parse_badging(b), expected)
        self.assertIs(ci.parse_badging, device.parse_badging)

    def test_badging_missing_and_ambiguous_metadata_fail_closed(self):
        from dustui_m05_badging import parse_badging, BadgingError
        good = "package: name='com.mikeyphw.dustui' versionCode='1400'\n"
        self.assertEqual(parse_badging(good), ((ci.TARGET, '1400'), None))
        with self.assertRaises(BadgingError): parse_badging('')
        with self.assertRaises(BadgingError): parse_badging(good + good)
        with self.assertRaises(BadgingError): parse_badging(
            "package: name='one' name='two' versionCode='1400'\n")
        with self.assertRaises(BadgingError): parse_badging(
            good + "instrumentation: name='one' targetPackage='a'\n" +
            "instrumentation: name='two' targetPackage='b'\n")
        with self.assertRaises(BadgingError): parse_badging(
            good + "instrumentation: name='one' name='two' targetPackage='a'\n")
        self.assertEqual(parse_badging(good + 'instrumentation: label=\'oops\'\n'),
                         ((ci.TARGET, '1400'), (None, None)))

    def test_aapt2_compiled_manifest_instrumentation_identity(self):
        from dustui_m05_badging import parse_instrumentation_xmltree
        tree = (
            'N: android=http://schemas.android.com/apk/res/android\n'
            'E: manifest (line=2)\n'
            '  A: package="com.mikeyphw.dustui.test" (Raw: "com.mikeyphw.dustui.test")\n'
            '  E: application (line=4)\n'
            '    A: android:name(0x01010003)="SomeApplication" (Raw: "SomeApplication")\n'
            '  E: instrumentation (line=9)\n'
            '    A: android:label(0x01010001)="Tests for DustUI" (Raw: "Tests for DustUI")\n'
            '    A: android:targetPackage(0x01010021)="com.mikeyphw.dustui" (Raw: "com.mikeyphw.dustui")\n'
            '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner" (Raw: "androidx.test.runner.AndroidJUnitRunner")\n'
            '    A: android:handleProfiling(0x01010022)=(type 0x12)0x0\n'
        )
        self.assertEqual(parse_instrumentation_xmltree(tree), (ci.TEST_RUNNER, ci.TARGET))
        self.assertIs(ci.parse_instrumentation_xmltree, device.parse_instrumentation_xmltree)
        # The real aapt2 badging output may omit instrumentation, even when the
        # signed compiled manifest contains the runner and target declaration.
        badging = "package: name='com.mikeyphw.dustui.test' versionCode='1400'\n"
        self.assertIsNone(ci.parse_badging(badging)[1])
        self.assertEqual(parse_instrumentation_xmltree(tree), (ci.TEST_RUNNER, ci.TARGET))
        # Full Android namespace rather than shorthand android: is also valid.
        self.assertEqual(parse_instrumentation_xmltree(tree.replace(
            'android:targetPackage(0x01010021)',
            'http://schemas.android.com/apk/res/android:targetPackage(0x01010021)')),
            (ci.TEST_RUNNER, ci.TARGET))

    def test_device_verifier_reads_signed_apk_compiled_manifest(self):
        badge = "package: name='com.mikeyphw.dustui.test' versionCode='1400'\n"
        tree = ('E: manifest (line=1)\n'
                '  E: instrumentation (line=4)\n'
                '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner"\n'
                '    A: android:targetPackage(0x01010021)="com.mikeyphw.dustui"\n')
        with patch.object(device, 'sdktool', return_value='/fake/aapt2'):
            with patch.object(device, 'call', side_effect=[
                SimpleNamespace(stdout=badge), SimpleNamespace(stdout=tree),
            ]) as invoke:
                pkg, inst = device.package_info(Path('test.apk'))
                self.assertEqual(pkg[0], ci.TEST_PKG)
                self.assertEqual(inst, (ci.TEST_RUNNER, ci.TARGET))
                self.assertEqual(invoke.call_args_list[1].args[0], [
                    '/fake/aapt2', 'dump', 'xmltree', 'test.apk', '--file', 'AndroidManifest.xml',
                ])
        conflicting = badge + (
            "instrumentation: name='bad.runner' targetPackage='com.mikeyphw.dustui'\n"
        )
        with patch.object(device, 'sdktool', return_value='/fake/aapt2'):
            with patch.object(device, 'call', side_effect=[
                SimpleNamespace(stdout=conflicting), SimpleNamespace(stdout=tree),
            ]):
                with self.assertRaises(device.DeviceError):
                    device.package_info(Path('test.apk'))

    def test_xmltree_missing_invalid_and_duplicate_instrumentation_refused(self):
        from dustui_m05_badging import parse_instrumentation_xmltree, BadgingError
        empty='E: manifest (line=1)\n  A: package="com.mikeyphw.dustui.test"\n'
        self.assertIsNone(parse_instrumentation_xmltree(empty))
        inst="  E: instrumentation (line=4)\n"
        with self.assertRaises(BadgingError): parse_instrumentation_xmltree(empty + inst)
        valid=inst + (
            '    A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner"\n'
            '    A: android:targetPackage(0x01010021)="com.mikeyphw.dustui"\n'
        )
        self.assertEqual(parse_instrumentation_xmltree(empty + valid), (ci.TEST_RUNNER, ci.TARGET))
        for variant in (
            empty + valid + valid,
            empty + valid + '    A: android:name(0x01010003)="another.runner"\n',
            empty + valid.replace('0x01010021', '0x01010022'),
            empty + valid.replace('"com.mikeyphw.dustui"', '(type 0x03)0x12345678'),
            empty + valid.replace('    A: android:targetPackage(0x01010021)="com.mikeyphw.dustui"\n', ''),
            empty + valid + '    E: unexpected_child (line=8)\n',
        ):
            with self.subTest(variant=variant):
                with self.assertRaises(BadgingError): parse_instrumentation_xmltree(variant)

    def test_m05_tracing_dependency_is_scoped_to_release_instrumentation(self):
        build = (ROOT/'android/app/build.gradle.kts').read_text()
        rule = (ROOT/'android/app/proguard-m05-release-test-rules.pro').read_text()
        test_rule = (ROOT/'android/app/proguard-androidTest-rules.pro').read_text()
        self.assertIn('proguardFiles("proguard-m05-release-test-rules.pro")', build)
        self.assertEqual(build.count('implementation("androidx.tracing:tracing:1.3.0")'), 1)
        self.assertEqual(build.count('androidTestImplementation("androidx.tracing:tracing:1.3.0")'), 1)
        self.assertIn('DUSTUI_M05_INSTRUMENTATION_BUILD', build)
        self.assertIn('-keep class androidx.tracing.Trace { *; }', rule)
        self.assertIn('-keep class androidx.tracing.Trace { *; }', test_rule)
        workflow = (ROOT/'.github/workflows/devtool-dustui-m05-signed-instrumentation.yml').read_text()
        self.assertIn('scripts/dustui_m05_dex_contract.py', workflow)
        self.assertLess(workflow.index('scripts/dustui_m05_dex_contract.py'), workflow.index('name: Upload source-matched'))

    def test_m05_dex_contract_checks_class_definitions_not_strings(self):
        from dustui_m05_dex_contract import defines_trace, DexContractError
        import struct
        descriptor = b'Landroidx/tracing/Trace;'
        def fake_dex(type_name: bytes, class_count: int):
            data = bytearray(0x70 + 4 + 4 + 32 + 4 + len(type_name) + 1)
            data[0:8] = b'dex\n035\0'
            struct.pack_into('<I', data, 0x20, len(data))
            struct.pack_into('<I', data, 0x24, 0x70)
            struct.pack_into('<I', data, 0x28, 0x12345678)
            struct.pack_into('<II', data, 0x38, 1, 0x70)   # string identifiers
            struct.pack_into('<II', data, 0x40, 1, 0x74)   # type identifiers
            struct.pack_into('<II', data, 0x60, class_count, 0x78)  # class definitions
            struct.pack_into('<I', data, 0x70, 0x98)
            struct.pack_into('<I', data, 0x74, 0)
            struct.pack_into('<I', data, 0x78, 0)
            data[0x98] = len(type_name)
            data[0x99:0x99 + len(type_name)] = type_name
            return bytes(data)
        self.assertTrue(defines_trace(fake_dex(descriptor, 1)))
        self.assertFalse(defines_trace(fake_dex(descriptor, 0)))
        self.assertFalse(defines_trace(fake_dex(b'Landroidx/tracing/Other;', 1)))
        with self.assertRaises(DexContractError):
            defines_trace(b'not dex')

    def test_m05_runner_kotlin_dependencies_are_instrumentation_only(self):
        build=(ROOT/'android/app/build.gradle.kts').read_text()
        rule=(ROOT/'android/app/proguard-m05-release-test-rules.pro').read_text()
        test_rule=(ROOT/'android/app/proguard-androidTest-rules.pro').read_text()
        section=build.split('    androidTestImplementation(libs.androidx.test.runner)', 1)[1]
        self.assertIn('if (providers.environmentVariable("DUSTUI_M05_INSTRUMENTATION_BUILD").orNull == "true")', section)
        self.assertIn('implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")', section)
        self.assertIn('androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")', section)
        self.assertEqual(build.count('org.jetbrains.kotlin:kotlin-stdlib:2.4.10'),2)
        self.assertIn('-keep class kotlin.** { *; }', rule)
        self.assertIn('-keep class kotlin.** { *; }', test_rule)
        self.assertIn('-keep class androidx.test.** { *; }', test_rule)
        self.assertNotIn('kotlin.**', (ROOT/'android/app/proguard-rules.pro').read_text())
        workflow=(ROOT/'.github/workflows/devtool-dustui-m05-signed-instrumentation.yml').read_text()
        self.assertIn('scripts/dustui_m05_dex_contract.py', workflow)
        self.assertLess(workflow.index('scripts/dustui_m05_dex_contract.py'),workflow.index('name: Upload source-matched'))

    def test_m05_runner_bootstrap_definitions_gate_and_missing_fail_closed(self):
        from dustui_m05_dex_contract import (REQUIRED_BOOTSTRAP_CLASSES,
            defined_descriptors, verify_pair, DexContractError)
        from pathlib import Path
        from unittest.mock import patch
        import dustui_m05_dex_contract as dex
        self.assertEqual(REQUIRED_BOOTSTRAP_CLASSES['kotlin.LazyKt'], b'Lkotlin/LazyKt;')
        self.assertIn('kotlin.jvm.internal.Intrinsics', REQUIRED_BOOTSTRAP_CLASSES)
        required=set(REQUIRED_BOOTSTRAP_CLASSES.values())
        # A reference to a class is NOT a DEX class definition.
        import struct
        descr=b'Lkotlin/LazyKt;'
        data=bytearray(0x70+4+4+32+4+len(descr)+1)
        data[:8]=b'dex\n035\0'
        struct.pack_into('<I',data,0x20,len(data))
        struct.pack_into('<I',data,0x24,0x70)
        struct.pack_into('<I',data,0x28,0x12345678)
        struct.pack_into('<II',data,0x38,1,0x70)
        struct.pack_into('<II',data,0x40,1,0x74)
        struct.pack_into('<II',data,0x60,0,0x78)
        struct.pack_into('<I',data,0x70,0x98)
        struct.pack_into('<I',data,0x74,0)
        struct.pack_into('<I',data,0x78,0)
        data[0x98]=len(descr)
        data[0x99:0x99+len(descr)]=descr
        self.assertEqual(defined_descriptors(bytes(data),required),set())
        struct.pack_into('<I',data,0x60,1)
        self.assertEqual(defined_descriptors(bytes(data),required),{descr})
        with patch.object(dex,'apk_defined_descriptors',side_effect=[
                {dex.TRACE_DESCRIPTOR},set()]):
            with self.assertRaisesRegex(DexContractError,'kotlin.LazyKt'):
                verify_pair(Path('release.apk'),Path('test.apk'))
        with patch.object(dex,'apk_defined_descriptors',side_effect=[
                {dex.TRACE_DESCRIPTOR},required-{dex.TRACE_DESCRIPTOR}]):
            report=verify_pair(Path('release.apk'),Path('test.apk'))
            self.assertEqual(report['status'],'HOSTED_RUNNER_BOOTSTRAP_CLASSES_PRESENT_NOT_DEVICE_QUALIFIED')
            self.assertEqual(report['required_class_locations']['kotlin.LazyKt'],
                             {'release':False,'instrumentation':True})

    def test_unsigned_or_unqualified_rejections(self):
        a='Signer #1 certificate SHA-256 digest: '+device.EXPECTED_CERT+'\nVerified using v2 scheme (APK Signature Scheme v2): true\n'
        self.assertEqual(ci.signing_sha(a),device.EXPECTED_CERT)
        with self.assertRaises(ci.ReleaseError):ci.signing_sha(a.replace('true','false'))
        with self.assertRaises(ci.ReleaseError):ci.signing_sha('Not signed')
        with patch.dict('os.environ',{'GITHUB_ACTIONS':'false','DUSTUI_M05_INSTRUMENTATION_BUILD':'true'},clear=True):
            with self.assertRaises(Exception):ci.guard(ROOT)

if __name__=='__main__':unittest.main()
