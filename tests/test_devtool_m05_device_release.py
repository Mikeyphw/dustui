"""M05 local-only regression and negative safety checks; no Gradle, signing or ADB."""
from __future__ import annotations
import copy
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import tomllib
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import dustui_m05_device as device
import dustui_m05_release as release
from devtool_actions_contract import check_document, ContractError


def fixture(root: Path):
    apk=root/'app-debug.apk'
    b=bytearray(80);b[:7]=b'\x7fELF\x02\x01\x01';struct.pack_into('<HH',b,16,3,183)
    with zipfile.ZipFile(apk,'w') as z:
        z.writestr('AndroidManifest.xml',b'fake binary manifest')
        z.writestr('classes.dex',b'x'*2048)
        z.writestr('lib/arm64-v8a/libdustui_go.so',bytes(b))
    _, ins=device.inspect_zip(apk)
    doc={"schema":"dustui.devtool.m04.apk-inspection.v1","status":"DEBUG_APK_LOCAL_BYTES_VERIFIED",
         "application_id":"com.mikeyphw.dustui.debug","version_code":1400,
         "debug_signer_certificate_sha256":"a"*64,"verified_v2_signed":True,
         "device_qualified":False,"release_qualified":False,
         "source_sha":"1"*40,"devtool_sha":"2"*40,**ins}
    rec=root/'apk-inspection.json';rec.write_text(json.dumps(doc))
    return apk,rec,doc


class M05(unittest.TestCase):
    def test_config_current_and_hosted_release_guarded(self):
        data=tomllib.loads((ROOT/'.devtool.toml').read_text())
        self.assertEqual(check_document(data,ROOT)['stage'],'M05')
        release.validate_native_config(ROOT)
        self.assertEqual(data['wrapper']['default_command'],'ci-preflight')
        self.assertNotIn('dustui_hosted_release', [r['target'] for r in data['wrapper']['commands'].values()])

    def test_refuse_direct_gradle_in_release_job(self):
        data=tomllib.loads((ROOT/'.devtool.toml').read_text())
        mutant=copy.deepcopy(data)
        mutant['targets']['dustui_hosted_release']['jobs']['release_build']['command']=['./gradlew',':app:assembleRelease']
        with self.assertRaises(ContractError):check_document(mutant,ROOT)

    def test_only_manual_signed_workflow_and_no_embedded_signer(self):
        s=(ROOT/'.github/workflows/devtool-dustui-m05-hosted-release.yml').read_text()
        self.assertIn('workflow_dispatch:',s)
        self.assertNotIn('pull_request:',s)
        self.assertNotIn('\n  push:',s)
        self.assertIn('github.ref == \'refs/heads/main\'',s)
        self.assertIn('environment:',s)
        self.assertIn('secrets.DUSTUI_KEYSTORE_BASE64',s)
        self.assertIn('secrets.DUSTUI_EXPECTED_SIGNER_SHA256',s)
        self.assertIn('--target dustui_hosted_release invoke ci',s)
        self.assertNotIn('keytool -genkeypair',s)
        self.assertNotIn('./gradlew :app:assembleRelease',s)
        self.assertIn('rm -f "$RUNNER_TEMP/dustui-m05-private',s)

    def test_fingerprint_normalizes_and_refuses_mismatch(self):
        fp='AB:'*31+'AB'
        self.assertEqual(release.fingerprint(fp),'ab'*32)
        for malformed in ('','a'*63,'g'*64,'0'*65):
            with self.assertRaises(release.ReleaseError):release.fingerprint(malformed)

    def test_java_properties_escape(self):
        self.assertEqual(release._java_property(r'P a:ss=word!'),r'P\ a\:ss\=word\!')
        for text in ('','pass\nword','word\r','nul\x00'):
            with self.assertRaises(release.ReleaseError):release._java_property(text)

    def test_release_is_refused_outside_github(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t)
            with patch('dustui_m05_release.subprocess.check_output',return_value='1'*40),\
                 patch('dustui_m05_release.subprocess.run') as proc,\
                 patch.dict('os.environ',{'GITHUB_ACTIONS':'false'},clear=True):
                proc.return_value.returncode=0
                with self.assertRaises(release.ReleaseError):release.root_identity(root,True)

    def test_device_receipt_valid_and_tamper_refused(self):
        with tempfile.TemporaryDirectory() as t:
            apk,rec,doc=fixture(Path(t))
            result=device.receipt_check(apk,rec)
            self.assertEqual(result['package'],'com.mikeyphw.dustui.debug')
            for key,value in [('apk_sha256','f'*64),('go_runtime_sha256','f'*64),('device_qualified',True),
                              ('release_qualified',True),('verified_v2_signed',False),('application_id','wrong')]:
                malformed=copy.deepcopy(doc);malformed[key]=value;rec.write_text(json.dumps(malformed))
                with self.subTest(key=key),self.assertRaises(device.DeviceError):device.receipt_check(apk,rec)
            rec.write_text(json.dumps(doc))
            with apk.open('ab') as f:f.write(b'untracked')
            with self.assertRaises(device.DeviceError):device.receipt_check(apk,rec)

    def test_device_run_requires_explicit_consent(self):
        with tempfile.TemporaryDirectory() as t:
            apk,rec,doc=fixture(Path(t))
            self.assertEqual(device.main(['plan','--apk',str(apk),'--inspection',str(rec)]),0)
            self.assertEqual(device.main(['run','--apk',str(apk),'--inspection',str(rec)]),2)
            with patch('dustui_m05_device.verify_signature') as signer:
                existing=Path(t)/'existing.json'; existing.write_text('{}')
                self.assertEqual(device.main(['run','--apk',str(apk),'--inspection',str(rec),
                    '--serial','emulator-5554','--allow-install','--output',str(existing)]),2)
                signer.assert_not_called()

    def test_device_run_refuses_unapproved_installed_package(self):
        with patch('dustui_m05_device.command') as cmd:
            cmd.return_value.stdout='List of devices attached\nemulator-5554\tdevice\n'
            with patch('dustui_m05_device.adb') as adb:
                adb.side_effect=[type('R',(),{'stdout':'1\n'})(),type('R',(),{'stdout':'arm64-v8a\n'})(),
                                 type('R',(),{'stdout':'package:/data/app/base.apk\n'})()]
                with self.assertRaises(device.DeviceError):device.run_device(Path('/tmp/foo.apk'),{'package':'com.mikeyphw.dustui.debug'},'emulator-5554',False)

    def test_rejects_missing_android_release_signer_and_root_authority(self):
        doc=tomllib.loads((ROOT/'.devtool.toml').read_text())
        self.assertNotIn('dustui_hosted_release', doc['targets']['dustui_hosted_android']['workflows'])
        self.assertEqual(doc['targets']['dustui_hosted_android']['jobs']['debug_qualified']['gradle_tasks'],
                         [':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'])

if __name__=='__main__':unittest.main()
