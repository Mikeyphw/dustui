#!/usr/bin/env python3
"""AUD-FIX-17 / Gate-E final qualification evidence orchestration.

This script deliberately consumes Devtool's canonical Android release report; it
never rebuilds generic release/signing/device truth itself. DustUI adds only its
product-specific migration/runtime/evidence assertions around that report.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any, Iterable

from audfix01_core import load_json, repository_observations, sha256_file
from audfix16_retirement import source_projection_sha256

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/evidence/gate-e"
QUAL_DIR = ROOT / "migration/qualification/v1"
STATE = QUAL_DIR / "current_state.json"
RETIREMENT = QUAL_DIR / "ov14_retirement.json"
GATE_E_EVIDENCE = QUAL_DIR / "gate_e_evidence.json"
GATE_E_BUNDLE = QUAL_DIR / "gate_e_bundle.json"
TOPOLOGY = QUAL_DIR / "gate_e_topology.json"
RELEASE_EVIDENCE = OUT / "canonical-release.json"
ROOTED_EVIDENCE = OUT / "rooted-arm64-runtime.json"
RUNTIME_BINDING = OUT / "runtime-apk-binding.json"
MIGRATION_PREFLIGHT = OUT / "migration-preflight.json"
PROMISE_PREFLIGHT = OUT / "promise-preflight.json"
COUNTEREXAMPLE = OUT / "counterexample-convergence.json"
RACE_EVIDENCE = OUT / "go-race.json"

PASS_WORDS = {"pass", "passed", "success", "successful", "qualified", "ok", "complete", "completed"}
FAIL_WORDS = {"fail", "failed", "failure", "error", "blocked", "invalid"}
HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")


def write_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run(*argv: str, cwd: Path | None = None, timeout: int = 180) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(argv), cwd=str(cwd or ROOT), text=True, capture_output=True,
        check=False, timeout=timeout,
    )


def git_value(*args: str) -> str:
    cp = run("git", *args, timeout=30)
    if cp.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {cp.stderr.strip()}")
    return cp.stdout.strip()


def current_identity(require_clean: bool = True) -> dict[str, str]:
    head = git_value("rev-parse", "HEAD")
    tree = git_value("rev-parse", "HEAD^{tree}")
    if require_clean:
        dirty = git_value("status", "--porcelain")
        if dirty:
            raise RuntimeError("Gate E requires a clean Git worktree; commit or discard source changes first")
    return {
        "git_head": head,
        "git_tree": tree,
        "source_projection_sha256": source_projection_sha256(),
    }


def walk(obj: Any) -> Iterable[tuple[str, Any]]:
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield str(k), v
            yield from walk(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from walk(v)


def values_for_keys(obj: Any, keys: set[str]) -> list[Any]:
    out: list[Any] = []
    wanted = {re.sub(r"[^a-z0-9]", "", k.lower()) for k in keys}
    for k, v in walk(obj):
        nk = re.sub(r"[^a-z0-9]", "", k.lower())
        if nk in wanted:
            out.append(v)
    return out


def text_values(obj: Any) -> list[str]:
    vals: list[str] = []
    if isinstance(obj, dict):
        for v in obj.values():
            vals.extend(text_values(v))
    elif isinstance(obj, list):
        for v in obj:
            vals.extend(text_values(v))
    elif isinstance(obj, (str, int, float, bool)):
        vals.append(str(obj))
    return vals


def normalized_status(value: Any) -> str:
    return str(value or "").strip().lower().replace("_", "-")


def report_is_pass(report: dict[str, Any]) -> bool:
    preferred = values_for_keys(report, {"final_verdict", "finalVerdict", "verdict"})
    if preferred:
        return any(normalized_status(x) in PASS_WORDS for x in preferred)
    top = report.get("status") or report.get("result")
    return normalized_status(top) in PASS_WORDS


def report_has_failure(report: dict[str, Any]) -> bool:
    for key, value in walk(report):
        nk = re.sub(r"[^a-z0-9]", "", key.lower())
        if nk in {"status", "outcome", "verdict", "finalverdict"}:
            if normalized_status(value) in FAIL_WORDS:
                return True
    return False


def release_report_candidates() -> list[Path]:
    base = ROOT / ".devtool/reports/android-release"
    if not base.is_dir():
        return []
    return sorted(
        (p for p in base.rglob("*.json") if p.is_file()),
        key=lambda p: p.stat().st_mtime_ns,
        reverse=True,
    )


def extract_release_run_id(report: dict[str, Any]) -> str:
    for value in values_for_keys(report, {"run_id", "runId", "release_run_id", "releaseRunId"}):
        s = str(value or "").strip()
        if s:
            return s
    return ""


def extract_fingerprints(report: dict[str, Any]) -> list[str]:
    out: set[str] = set()
    for key, value in walk(report):
        nk = re.sub(r"[^a-z0-9]", "", key.lower())
        interesting = "fingerprint" in nk or ("certificate" in nk and "sha" in nk) or ("signer" in nk and "sha" in nk)
        if not interesting:
            continue
        if isinstance(value, str):
            compact = re.sub(r"[^0-9a-fA-F]", "", value)
            if HEX64.fullmatch(compact):
                out.add(compact.lower())
    return sorted(out)


def artifact_dicts(obj: Any) -> Iterable[dict[str, Any]]:
    if isinstance(obj, dict):
        yield obj
        for v in obj.values():
            yield from artifact_dicts(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from artifact_dicts(v)


def dict_sha(item: dict[str, Any]) -> str:
    for key in ("sha256", "sha_256", "artifact_sha256", "digest", "hash"):
        value = item.get(key)
        if isinstance(value, str):
            compact = value.lower().removeprefix("sha256:").strip()
            if HEX64.fullmatch(compact):
                return compact
    return ""


def dict_path(item: dict[str, Any]) -> str:
    for key in ("path", "artifact_path", "canonical_path", "source_path", "exported_path"):
        value = item.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def resolve_path(raw: str) -> Path | None:
    if not raw:
        return None
    p = Path(raw).expanduser()
    if not p.is_absolute():
        p = ROOT / p
    try:
        p = p.resolve()
    except OSError:
        pass
    return p if p.is_file() else None


def registry_items() -> list[dict[str, Any]]:
    sources = [ROOT / ".devtool/artifacts/registry.json"]
    shard_dir = ROOT / ".devtool/artifacts.d"
    if shard_dir.is_dir():
        sources.extend(sorted(shard_dir.glob("*.json"), reverse=True))
    out: list[dict[str, Any]] = []
    for path in sources:
        if not path.is_file():
            continue
        try:
            data = load_json(path)
        except Exception:
            continue
        out.extend(list(artifact_dicts(data)))
    return out


def resolve_release_apk(report: dict[str, Any], run_id: str) -> tuple[Path, str]:
    candidates: list[tuple[int, Path, str]] = []
    for item in artifact_dicts(report):
        path_s = dict_path(item)
        digest = dict_sha(item)
        if not path_s.lower().endswith(".apk") or not digest:
            continue
        p = resolve_path(path_s)
        if p:
            score = 10 + (10 if "release" in path_s.lower() else 0)
            candidates.append((score, p, digest))

    report_hashes = {dict_sha(item) for item in artifact_dicts(report)}
    report_hashes.discard("")
    for item in registry_items():
        path_s = dict_path(item)
        digest = dict_sha(item)
        if not path_s.lower().endswith(".apk") or not digest:
            continue
        item_run = str(item.get("run_id") or item.get("runId") or "")
        if run_id and item_run and item_run != run_id:
            continue
        if report_hashes and digest not in report_hashes and (not run_id or item_run != run_id):
            continue
        p = resolve_path(path_s)
        if p:
            score = 20 + (10 if "release" in path_s.lower() else 0) + (10 if run_id and item_run == run_id else 0)
            candidates.append((score, p, digest))

    if not candidates:
        raise RuntimeError("canonical Devtool release report has no resolvable verified APK artifact")
    candidates.sort(key=lambda x: (x[0], x[1].stat().st_mtime_ns), reverse=True)
    _score, path, declared = candidates[0]
    actual = sha256_file(path)
    if actual != declared:
        raise RuntimeError(f"release APK hash drift: report/registry={declared} actual={actual}")
    return path, actual


def apk_runtime_sha(apk: Path) -> str:
    member = "lib/arm64-v8a/libdustui_go.so"
    with zipfile.ZipFile(apk) as zf:
        try:
            data = zf.read(member)
        except KeyError as exc:
            raise RuntimeError(f"release APK does not contain {member}") from exc
    return hashlib.sha256(data).hexdigest()


def generated_runtime() -> tuple[Path, str]:
    path = ROOT / "android/app/build/generated/goRuntime/jniLibs/arm64-v8a/libdustui_go.so"
    if not path.is_file():
        raise RuntimeError(f"generated Go runtime missing: {path.relative_to(ROOT)}")
    return path, sha256_file(path)


def select_release_report(identity: dict[str, str]) -> tuple[Path, dict[str, Any]]:
    failures: list[str] = []
    for path in release_report_candidates():
        try:
            report = load_json(path)
        except Exception as exc:
            failures.append(f"{path.name}: invalid JSON: {exc}")
            continue
        if not isinstance(report, dict):
            continue
        blob = json.dumps(report, sort_keys=True)
        target_values = {str(v) for v in values_for_keys(report, {"target", "target_name", "targetName"})}
        variant_values = {str(v).lower() for v in values_for_keys(report, {"variant", "build_variant", "buildVariant"})}
        if target_values and "dustui_android" not in target_values:
            continue
        if variant_values and "release" not in variant_values:
            continue
        if identity["git_head"] not in blob:
            failures.append(f"{path.name}: source Git HEAD does not match current Gate-E tree")
            continue
        if not report_is_pass(report) or report_has_failure(report):
            failures.append(f"{path.name}: final release verdict is not passing")
            continue
        return path, report
    detail = "; ".join(failures[:5]) if failures else "no Android release reports found"
    raise RuntimeError("no current-tree passing canonical Devtool Android release report: " + detail)


def preflight() -> dict[str, Any]:
    identity = current_identity(require_clean=True)
    state = load_json(STATE)
    retirement = load_json(RETIREMENT)
    if not state.get("gate_d", {}).get("qualified"):
        raise RuntimeError("Gate E requires already-qualified Gate D")
    if state.get("ov14", {}).get("status") != "physically-retired":
        raise RuntimeError("Gate E requires completed AUD-FIX-16 physical retirement")
    if retirement.get("status") != "pass" or retirement.get("gate_d_resealed") is not False:
        raise RuntimeError("AUD-FIX-16 retirement evidence is not canonical")
    recorded = str(retirement.get("post_retirement_source_sha256") or "")
    if recorded != identity["source_projection_sha256"]:
        raise RuntimeError("AUD-FIX-16 post-retirement source projection is stale")
    return {
        "schema": "dustui.gate-e.preflight.v1",
        "status": "pass",
        **identity,
        "gate_d_ancestor_source_sha256": retirement.get("gate_d_ancestor_source_sha256"),
        "ov14_status": state.get("ov14", {}).get("status"),
        "gate_e_status_before_run": state.get("gate_e", {}).get("status"),
        "validation_run_id": os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID"),
    }


def canonical_release() -> dict[str, Any]:
    identity = preflight()
    path, report = select_release_report(identity)
    run_id = extract_release_run_id(report)
    if not run_id:
        raise RuntimeError("canonical release report does not expose its Devtool run identity")
    fingerprints = extract_fingerprints(report)
    if not fingerprints:
        raise RuntimeError("canonical release report does not expose a signing certificate/signer SHA-256 identity")
    apk_path, apk_sha = resolve_release_apk(report, run_id)
    runtime_path, runtime_sha = generated_runtime()
    embedded_sha = apk_runtime_sha(apk_path)
    if embedded_sha != runtime_sha:
        raise RuntimeError(f"signed release APK runtime mismatch: apk={embedded_sha} generated={runtime_sha}")
    obj = {
        "schema": "dustui.gate-e.canonical-release.v1",
        "status": "pass",
        "release_run_id": run_id,
        "release_report": str(path.relative_to(ROOT)),
        "release_report_sha256": sha256_file(path),
        "release_apk": str(apk_path.relative_to(ROOT)) if ROOT in apk_path.parents else str(apk_path),
        "release_apk_sha256": apk_sha,
        "signing_identity_sha256": fingerprints[0],
        "all_signing_identity_sha256": fingerprints,
        "generated_runtime": str(runtime_path.relative_to(ROOT)),
        "generated_runtime_sha256": runtime_sha,
        "embedded_runtime_sha256": embedded_sha,
        "source_git_head": identity["git_head"],
        "source_git_tree": identity["git_tree"],
        "source_projection_sha256": identity["source_projection_sha256"],
        "devtool_owned_release_evidence": True,
    }
    write_json(RELEASE_EVIDENCE, obj)
    return obj


def invoke_gate_d_probe(action: str, output_name: str) -> dict[str, Any]:
    identity = preflight()
    cp = run("python3", "scripts/audfix14_gate_d_probe.py", action, timeout=120)
    sys.stdout.write(cp.stdout)
    sys.stderr.write(cp.stderr)
    if cp.returncode != 0:
        raise RuntimeError(f"Gate-E probe {action} failed")
    source = ROOT / "build/evidence/gate-d" / f"{output_name}.json"
    if not source.is_file():
        raise RuntimeError(f"Gate-D probe did not produce {source.relative_to(ROOT)}")
    data = load_json(source)
    if data.get("status") != "pass":
        raise RuntimeError(f"Gate-E probe {action} did not pass")
    data = dict(data)
    data.update({
        "gate_e_validation_run_id": os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID"),
        "gate_e_source_git_head": identity["git_head"],
        "gate_e_source_projection_sha256": identity["source_projection_sha256"],
    })
    dest = ROOTED_EVIDENCE if output_name == "rooted-arm64-runtime" else RUNTIME_BINDING
    write_json(dest, data)
    return data


def migration_preflight() -> dict[str, Any]:
    identity = preflight()
    obs = repository_observations(ROOT)
    failures: list[str] = []
    expected_false = [
        "legacy_python_textual_present", "python_textual_oracle_present", "room_artifacts_present",
        "ndjson_lifecycle_present", "legacy_navigation_present", "cpp_scanner_present",
        "legacy_destructive_executor_present",
    ]
    for key in expected_false:
        if obs.get(key):
            failures.append(key)
    if obs.get("generic_tooling_duplication_paths"):
        failures.append("generic_tooling_duplication_paths")
    if obs.get("predecessor_source_token_paths"):
        failures.append("predecessor_source_token_paths")
    for key in ("android_go_process_binding_present", "android_go_packaging_present", "go_sqlite_implementation_present"):
        if not obs.get(key):
            failures.append(key)
    if failures:
        raise RuntimeError("final migration preflight failed: " + ", ".join(failures))
    obj = {
        "schema": "dustui.gate-e.migration-preflight.v1",
        "status": "pass",
        "runtime": "go-native-android",
        "android_ui": "Kotlin/Compose",
        "engine_database": "Go-owned SQLite",
        "python_runtime": "none",
        "cpp_scanner_runtime": "none",
        "room_domain_authority": "none",
        "legacy_destructive_executor": "none",
        "legacy_navigation": "none",
        "open_migration_promises_before_gate_e_finalization": ["GAP:AUD-024"],
        "open_migration_promises_if_gate_e_sealed": 0,
        "source_git_head": identity["git_head"],
        "source_projection_sha256": identity["source_projection_sha256"],
    }
    write_json(MIGRATION_PREFLIGHT, obj)
    return obj


def promise_preflight() -> dict[str, Any]:
    identity = preflight()
    cp = run("python3", "scripts/validate_full_roadmap_conformance.py", "--scope", "all", "--json", timeout=180)
    if cp.returncode != 0:
        sys.stdout.write(cp.stdout); sys.stderr.write(cp.stderr)
        raise RuntimeError("full roadmap conformance is not clean before Gate E")
    payload = json.loads(cp.stdout)
    findings = payload.get("findings") or []
    if findings:
        raise RuntimeError(f"full roadmap conformance still has {len(findings)} findings")
    req = load_json(ROOT / "migration/requirements/v2/requirements.json")
    gate_e_rows = [r for r in req.get("requirements", []) if "AUD-FIX-17" in (r.get("owner") or [])]
    aud024 = [r for r in gate_e_rows if r.get("id") == "GAP:AUD-024"]
    if len(aud024) != 1:
        raise RuntimeError("requirement index lost the unique GAP:AUD-024 Gate-E promise")
    obj = {
        "schema": "dustui.gate-e.promise-preflight.v1",
        "status": "pass",
        "requirement_index_count": len(req.get("requirements", [])),
        "aud_fix_17_owned_requirement_ids": sorted(str(r.get("id")) for r in gate_e_rows),
        "final_gate_promise": "GAP:AUD-024",
        "final_gate_promise_status_before_seal": aud024[0].get("fresh_status"),
        "full_roadmap_findings": 0,
        "source_git_head": identity["git_head"],
        "source_projection_sha256": identity["source_projection_sha256"],
    }
    write_json(PROMISE_PREFLIGHT, obj)
    return obj


def counterexample_convergence() -> dict[str, Any]:
    identity = preflight()
    passes = []
    for index in range(2):
        cp = run("python3", "scripts/validate_full_roadmap_conformance.py", "--scope", "all", "--json", timeout=180)
        if cp.returncode != 0:
            sys.stdout.write(cp.stdout); sys.stderr.write(cp.stderr)
            raise RuntimeError(f"counterexample/conformance pass {index + 1} failed")
        payload = json.loads(cp.stdout)
        findings = payload.get("findings") or []
        if findings:
            raise RuntimeError(f"counterexample/conformance pass {index + 1} found {len(findings)} root families")
        passes.append({"pass": index + 1, "finding_count": 0, "schema": payload.get("schema")})
    obj = {
        "schema": "dustui.gate-e.counterexample-convergence.v1",
        "status": "pass",
        "fresh_passes": passes,
        "new_independent_root_family_count": 0,
        "source_git_head": identity["git_head"],
        "source_projection_sha256": identity["source_projection_sha256"],
    }
    write_json(COUNTEREXAMPLE, obj)
    return obj


def go_race() -> dict[str, Any]:
    identity = preflight()
    cp = run("go", "env", "GOOS", "GOARCH", "CGO_ENABLED", cwd=ROOT / "go", timeout=30)
    if cp.returncode != 0:
        raise RuntimeError("go env failed for race support detection")
    env = cp.stdout.splitlines()
    goos, goarch, cgo = (env + ["", "", ""])[:3]
    supported = goos in {"linux", "darwin", "freebsd", "windows"} and cgo == "1"
    if supported:
        cmd = [
            "go", "test", "-race", "-count=1",
            "./internal/operations", "./internal/runtime", "./internal/privileged", "./internal/state",
        ]
        test = run(*cmd, cwd=ROOT / "go", timeout=900)
        sys.stdout.write(test.stdout); sys.stderr.write(test.stderr)
        if test.returncode != 0:
            raise RuntimeError("Go race qualification failed")
        disposition = "executed-pass"
    else:
        cmd = []
        disposition = "not-supported-by-current-go-target"
    obj = {
        "schema": "dustui.gate-e.go-race.v1",
        "status": "pass",
        "goos": goos,
        "goarch": goarch,
        "cgo_enabled": cgo,
        "race_supported": supported,
        "disposition": disposition,
        "command": cmd,
        "source_git_head": identity["git_head"],
        "source_projection_sha256": identity["source_projection_sha256"],
    }
    write_json(RACE_EVIDENCE, obj)
    return obj


def load_required(path: Path, schema: str | None = None) -> dict[str, Any]:
    if not path.is_file():
        raise RuntimeError(f"required Gate-E evidence missing: {path.relative_to(ROOT)}")
    data = load_json(path)
    if data.get("status") != "pass":
        raise RuntimeError(f"required Gate-E evidence did not pass: {path.relative_to(ROOT)}")
    if schema and data.get("schema") != schema:
        raise RuntimeError(f"unexpected evidence schema for {path.relative_to(ROOT)}")
    return data


def finalize() -> dict[str, Any]:
    identity = preflight()
    topology = load_json(TOPOLOGY)
    release = load_required(RELEASE_EVIDENCE, "dustui.gate-e.canonical-release.v1")
    rooted = load_required(ROOTED_EVIDENCE)
    runtime = load_required(RUNTIME_BINDING)
    migration = load_required(MIGRATION_PREFLIGHT, "dustui.gate-e.migration-preflight.v1")
    promises = load_required(PROMISE_PREFLIGHT, "dustui.gate-e.promise-preflight.v1")
    convergence = load_required(COUNTEREXAMPLE, "dustui.gate-e.counterexample-convergence.v1")
    race = load_required(RACE_EVIDENCE, "dustui.gate-e.go-race.v1")

    for name, data in [
        ("release", release), ("rooted", rooted), ("runtime", runtime),
        ("migration", migration), ("promises", promises), ("convergence", convergence), ("race", race),
    ]:
        bound = str(data.get("source_projection_sha256") or data.get("gate_e_source_projection_sha256") or "")
        if bound and bound != identity["source_projection_sha256"]:
            raise RuntimeError(f"{name} evidence is bound to a different Gate-E source projection")
        bound_head = str(data.get("source_git_head") or data.get("gate_e_source_git_head") or "")
        if bound_head and bound_head != identity["git_head"]:
            raise RuntimeError(f"{name} evidence is bound to a different Git HEAD")

    if runtime.get("generated_runtime_sha256") and runtime.get("generated_runtime_sha256") != release.get("generated_runtime_sha256"):
        raise RuntimeError("Gate-E release/runtime probe hashes disagree")

    validation_run_id = os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID")
    if not validation_run_id:
        raise RuntimeError("Gate-E finalization requires canonical Devtool validation run identity")
    required_nodes = list(topology.get("required_nodes") or [])
    if not required_nodes:
        raise RuntimeError("Gate-E topology has no required nodes")
    nodes = []
    for node_id in required_nodes:
        binding = {
            "source_projection_sha256": identity["source_projection_sha256"],
            "source_git_head": identity["git_head"],
            "source_git_tree": identity["git_tree"],
            "validation_run_id": validation_run_id,
        }
        if node_id == "canonical_devtool_release":
            binding.update({
                "release_run_id": release["release_run_id"],
                "release_report_sha256": release["release_report_sha256"],
                "artifact_sha256": release["release_apk_sha256"],
                "signing_identity_sha256": release["signing_identity_sha256"],
                "runtime_sha256": release["embedded_runtime_sha256"],
            })
        elif node_id == "rooted_arm64_runtime":
            binding.update({"device_serial": rooted.get("device_serial"), "device_arch": rooted.get("device_arch")})
        elif node_id == "runtime_apk_binding":
            binding.update({"runtime_sha256": release["embedded_runtime_sha256"], "artifact_sha256": release["release_apk_sha256"]})
        nodes.append({"id": node_id, "status": "pass", "binding": binding})

    retirement = load_json(RETIREMENT)
    registry = {
        "schema": "dustui.gate-e.evidence-registry.v1",
        "status": "pass",
        "generated_by": "scripts/audfix17_gate_e.py finalize",
        "source_git_head": identity["git_head"],
        "source_git_tree": identity["git_tree"],
        "source_projection_sha256": identity["source_projection_sha256"],
        "gate_d_ancestor_source_sha256": retirement.get("gate_d_ancestor_source_sha256"),
        "validation_run_id": validation_run_id,
        "release_run_id": release["release_run_id"],
        "release_report_sha256": release["release_report_sha256"],
        "release_apk_sha256": release["release_apk_sha256"],
        "signing_identity_sha256": release["signing_identity_sha256"],
        "go_runtime_sha256": release["embedded_runtime_sha256"],
        "required_nodes": required_nodes,
        "nodes": nodes,
    }
    write_json(GATE_E_EVIDENCE, registry)

    final_migration_audit = {
        "runtime": "go-native-android",
        "android_ui": "Kotlin/Compose",
        "engine_database": "Go-owned SQLite",
        "python_runtime": "none",
        "cpp_scanner_runtime": "none",
        "room_domain_authority": "none",
        "legacy_destructive_executor": "none",
        "legacy_navigation": "none",
        "open_migration_promises": 0,
    }
    bundle = {
        "schema": "dustui.gate-e.final-bundle.v1",
        "status": "pass",
        "gate": "Gate E",
        "phase": "OV15",
        "aud_fix": "AUD-FIX-17",
        "source_git_head": identity["git_head"],
        "source_git_tree": identity["git_tree"],
        "source_projection_sha256": identity["source_projection_sha256"],
        "gate_d_ancestor_source_sha256": retirement.get("gate_d_ancestor_source_sha256"),
        "validation_run_id": validation_run_id,
        "release_run_id": release["release_run_id"],
        "release_report_sha256": release["release_report_sha256"],
        "release_apk_sha256": release["release_apk_sha256"],
        "signing_identity_sha256": release["signing_identity_sha256"],
        "go_runtime_sha256": release["embedded_runtime_sha256"],
        "counterexample_new_root_family_count": convergence["new_independent_root_family_count"],
        "go_race_disposition": race["disposition"],
        "final_migration_audit": final_migration_audit,
        "promise_ledger": {
            "open_root_gaps": 0,
            "final_gate_promise": "GAP:AUD-024",
            "status": "closed-by-executed-gate-e-evidence",
        },
        "evidence_registry": "migration/qualification/v1/gate_e_evidence.json",
    }
    write_json(GATE_E_BUNDLE, bundle)

    for cmd in [
        ["python3", "scripts/refresh_qualification_state.py"],
        ["python3", "scripts/refresh_acceptance_state.py"],
    ]:
        cp = subprocess.run(cmd, cwd=ROOT, text=True)
        if cp.returncode != 0:
            raise RuntimeError(f"generated-state refresh failed: {' '.join(cmd)}")

    state = load_json(STATE)
    if state.get("gate_e", {}).get("status") != "qualified" or not state.get("gate_e", {}).get("qualified"):
        raise RuntimeError("Gate-E evidence was written but canonical qualification state did not close Gate E")
    return bundle


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("action", choices=[
        "preflight", "release-evidence", "rooted-runtime", "runtime-apk-binding",
        "migration-preflight", "promise-preflight", "counterexample-convergence",
        "go-race", "finalize",
    ])
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    try:
        if args.action == "preflight": obj = preflight()
        elif args.action == "release-evidence": obj = canonical_release()
        elif args.action == "rooted-runtime": obj = invoke_gate_d_probe("rooted-runtime", "rooted-arm64-runtime")
        elif args.action == "runtime-apk-binding": obj = invoke_gate_d_probe("runtime-apk-binding", "runtime-apk-binding")
        elif args.action == "migration-preflight": obj = migration_preflight()
        elif args.action == "promise-preflight": obj = promise_preflight()
        elif args.action == "counterexample-convergence": obj = counterexample_convergence()
        elif args.action == "go-race": obj = go_race()
        else: obj = finalize()
    except Exception as exc:
        print(f"AUD-FIX-17 {args.action} failure: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(obj, indent=2, sort_keys=True))
    elif args.action not in {"rooted-runtime", "runtime-apk-binding"}:
        print(f"AUD-FIX-17 {args.action} PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
