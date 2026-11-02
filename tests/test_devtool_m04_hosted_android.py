"""M04 native Android provider and package evidence contracts (no Gradle)."""
from __future__ import annotations
import copy
import importlib.util
from pathlib import Path
import struct
import sys
import tempfile
import tomllib
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import dustui_m04_hosted as gate
import dustui_m04_verify_apk as verify
from devtool_actions_contract import check_document, ContractError


def graph():
    return {"target": gate.TARGET, "workflow": gate.WORKFLOW,
            "runtime_sha": gate.DEVTOOL_SHA, "graph_sha256": "a"*64,
            "jobs": [{"id": "node-01", "job": "debug_qualified",
                      "provider": "android-gradle", "profile_id": gate.PROFILES,
                      "toolchain_version": "21", "needs": [], "handoffs": [],
                      "outputs": [gate.OUTPUT], "android_review": {
                          "semantic_request": True, "project_dir": "android", "variant": "debug",
                          "java_version": "21", "wrapper_version": "9.7.1",
                          "wrapper_jar_sha256": "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd",
                          "tasks": list(gate.TASKS), "operations": ["test", "lint", "assemble"],
                          "release_qualified": False, "device_qualified": False,
                          "signer_authority": False}}]}


class M04Contract(unittest.TestCase):
    def test_native_exact_graph(self):
        self.assertEqual(gate.checked_graph(graph())["id"], "node-01")

    def test_refuses_missing_unit_lint_or_release_leakage(self):
        for bad in ([":app:lintDebug", ":app:assembleDebug"],
                    [":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleRelease"],
                    [":app:testDebugUnitTest", ":app:assembleDebug"]):
            mutated = graph()
            mutated["jobs"][0]["android_review"]["tasks"] = bad
            with self.assertRaises(gate.HostedAndroidError):
                gate.checked_graph(mutated)

    def test_refuses_claimed_device_or_release_qualification(self):
        for key in ("release_qualified", "device_qualified", "signer_authority"):
            mutated = graph()
            mutated["jobs"][0]["android_review"][key] = True
            with self.assertRaises(gate.HostedAndroidError):
                gate.checked_graph(mutated)

    def test_rejects_bad_provider_and_source_environment(self):
        mutated = graph(); mutated["jobs"][0]["provider"] = "command"
        with self.assertRaises(gate.HostedAndroidError): gate.checked_graph(mutated)
        with patch.dict("os.environ", {"GITHUB_ACTIONS": "", "GITHUB_SHA": ""}):
            self.assertEqual(gate.main(["run", "--root", str(ROOT)]), 2)

    def test_source_contract_rejects_local_gradle_and_missing_unit(self):
        conf = tomllib.loads((ROOT/".devtool.toml").read_text())
        self.assertEqual(check_document(conf, ROOT)["status"], "PASS")
        mutant = copy.deepcopy(conf)
        mutant["targets"]["dustui_hosted_android"]["jobs"]["debug_qualified"]["gradle_tasks"] = [":app:lintDebug", ":app:assembleDebug"]
        with self.assertRaises(ContractError): check_document(mutant, ROOT)
        mutant = copy.deepcopy(conf)
        mutant["wrapper"]["commands"]["android-build"] = {"target": "dustui_hosted_android", "workflow": "ci"}
        with self.assertRaises(ContractError): check_document(mutant, ROOT)

    def test_workflow_is_hosted_and_separate_from_m03(self):
        source = (ROOT/".github/workflows/devtool-dustui-m04-hosted-debug.yml").read_text()
        self.assertIn("scripts/dustui_m04_hosted.py run", source)
        self.assertIn("scripts/dustui_m04_verify_apk.py", source)
        self.assertIn("ndk;28.2.13676358", source)
        self.assertIn(gate.DEVTOOL_SHA, source)
        self.assertNotIn("./gradlew ", source)
        self.assertNotIn(":app:assembleRelease", source)
        self.assertNotIn("secrets.", source)
        m03 = (ROOT/".github/workflows/devtool-dustui.yml").read_text()
        self.assertNotIn("./gradlew", m03)

    def test_elf_64_arm64_pie_header(self):
        elf = bytearray(64);elf[:7] = b"\x7fELF\x02\x01\x01"
        struct.pack_into("<HH", elf, 16, 3, 183)
        verify.elf_arm64_pie(elf)
        for etype, arch in ((2,183),(3,62)):
            broken = bytearray(elf)
            struct.pack_into("<HH", broken, 16, etype, arch)
            with self.assertRaises(verify.APKError): verify.elf_arm64_pie(broken)

    def test_apk_missing_runtime_and_wrong_abi_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d)/"app.apk"
            with zipfile.ZipFile(apk,"w") as z:
                z.writestr("AndroidManifest.xml",b"manifest")
                z.writestr("classes.dex",b"dex")
                z.writestr("assets/data.bin",b"x"*2000)
            with self.assertRaisesRegex(verify.APKError,"Go runtime"):
                verify.inspect_zip(apk)
            elf = bytearray(64);elf[:7] = b"\x7fELF\x02\x01\x01"
            struct.pack_into("<HH",elf,16,3,183)
            with zipfile.ZipFile(apk,"w") as z:
                z.writestr("AndroidManifest.xml",b"manifest")
                z.writestr("classes.dex",b"dex")
                z.writestr(verify.GO_ENTRY,elf)
                z.writestr("lib/x86_64/libunreviewed.so",b"bad")
                z.writestr("assets/data.bin",b"x"*2000)
            with self.assertRaisesRegex(verify.APKError,"ABI"):
                verify.inspect_zip(apk)

    def test_android_junit_requires_real_passed_cases(self):
        with tempfile.TemporaryDirectory() as d:
            folder=Path(d)
            (folder/"TEST-a.xml").write_text('<testsuite tests="2"><testcase name="one"/><testcase name="two"/></testsuite>')
            self.assertEqual(verify.inspect_unit_tests(folder)["tests"], 2)
            (folder/"TEST-a.xml").write_text('<testsuite tests="1"><testcase name="one"><failure message="bad"/></testcase></testsuite>')
            with self.assertRaises(verify.APKError): verify.inspect_unit_tests(folder)
            (folder/"TEST-a.xml").write_text('<testsuite tests="0"/>')
            with self.assertRaises(verify.APKError): verify.inspect_unit_tests(folder)
            (folder/"TEST-a.xml").write_text('<!DOCTYPE x [ <!ENTITY x "unsafe"> ]><testsuite tests="0"/>')
            with self.assertRaises(verify.APKError): verify.inspect_unit_tests(folder)

    def test_apk_signing_package_and_receipt_cannot_be_forged(self):
        with tempfile.TemporaryDirectory() as d:
            sdk = Path(d)/"sdk/build-tools/36.0.0";sdk.mkdir(parents=True)
            for tool in ("aapt2","apksigner"): (sdk/tool).write_bytes(b"stub")
            apk = Path(d)/"app.apk";apk.write_bytes(b"stub")
            from subprocess import CompletedProcess
            outputs=[CompletedProcess([],0,"package: name='com.mikeyphw.dustui.debug' versionCode='1400' versionName='x'\n",""),
                     CompletedProcess([],0,"Signer #1 certificate SHA-256 digest: " + "a"*64 + "\nVerified using v2 scheme (APK Signature Scheme v2): true\n","")]
            with patch.object(verify.subprocess,"run", side_effect=outputs):
                result = verify.inspect_android_tools(apk,sdk.parent.parent)
                self.assertEqual(result["application_id"], verify.PACKAGE)
            outputs[0] = CompletedProcess([],0,"package: name='com.example.bad' versionCode='1400'\n","")
            with patch.object(verify.subprocess,"run", side_effect=outputs):
                with self.assertRaises(verify.APKError): verify.inspect_android_tools(apk,sdk.parent.parent)

if __name__ == "__main__": unittest.main()
