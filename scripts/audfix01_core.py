from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

HISTORICAL_GATE_D_NODES = (
    "historical_gate_a_exo",
    "historical_gate_b_exo_arm64",
    "historical_gate_c_exo",
)

REQUIRED_GATE_D_NODES = (
    *HISTORICAL_GATE_D_NODES,
    "requirement_index_conformance",
    "go_test",
    "go_build",
    "go_schema",
    "go_product_contract",
    "go_cli_smoke",
    "go_tui_smoke",
    "android_debug_unit",
    "android_debug_lint",
    "android_debug_assemble",
    "android_release_unit",
    "android_release_lint",
    "android_release_assemble",
    "migration_validators",
    "install_launch_smoke",
    "route_deeplink_recreation_rotation",
    "accessibility",
    "rooted_arm64_runtime",
    "runtime_apk_binding",
    "devtool_artifact_identity",
    "durable_operation_terminal_truth",
    "diagnostics_support_seal",
    "production_reachability",
)

REQUIRED_GATE_E_NODES = (
    "gate_e_contract",
    "canonical_devtool_release",
    "full_roadmap_conformance",
    "aud16_retirement_regression",
    "successor_migration_validators",
    "go_full_regression",
    "go_adversarial_failure_windows",
    "go_race_where_supported",
    "android_debug_unit",
    "android_debug_lint",
    "android_debug_assemble",
    "android_release_unit",
    "android_release_lint",
    "android_release_assemble",
    "connected_instrumentation_full",
    "rooted_arm64_runtime",
    "runtime_apk_binding",
    "final_migration_audit",
    "final_promise_audit",
    "counterexample_convergence",
)

DESIRED_RETIREMENT = {
    "cpp_scanner_runtime": "none",
    "python_runtime": "none",
    "python_textual_oracle": "none",
    "room_domain_artifacts": "none",
    "legacy_destructive_executor": "none",
    "legacy_navigation": "none",
    "go_authority": "product-default-qualified",
    "android_go_runtime_binding": "production-qualified",
    "go_sqlite_persistence": "go-owned-sqlite",
    "generic_release_truth": "devtool-only",
}


def read_text(root: Path, rel: str) -> str:
    p = root / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _any_file(root: Path, rels: list[str]) -> bool:
    return any((root / r).is_file() for r in rels)


def _all_source(root: Path, base: str, suffixes=(".kt", ".go", ".py", ".sh", ".md")) -> str:
    p = root / base
    if not p.exists():
        return ""
    chunks: list[str] = []
    for f in sorted(p.rglob("*")):
        if f.is_file() and f.suffix in suffixes:
            chunks.append(f.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(chunks)


def repository_observations(root: Path) -> dict[str, Any]:
    devtool = read_text(root, ".devtool.toml")
    android_src = _all_source(root, "android/app/src/main/kotlin", (".kt",))
    ui_src = _all_source(root, "android/app/src/main/kotlin/com/mikeyphw/dustui/ui", (".kt",))
    root_src = _all_source(root, "android/app/src/main/kotlin/com/mikeyphw/dustui/root", (".kt",))
    android_build_gradle = read_text(root, "android/app/build.gradle.kts")
    go_runtime = read_text(root, "go/cmd/dustui-go/runtime_domain.go") + "\n" + read_text(root, "go/cmd/dustui-go/main.go")
    go_state = _all_source(root, "go/internal/state", (".go",))
    gomod = read_text(root, "go/go.mod")

    python_present = any(p.is_file() for p in (root / "src/dust_tui").glob("**/*")) or (
        "kind = \"python\"" in devtool and "run_module = \"dust_tui\"" in devtool
    )
    python_oracle_present = (root / "migration/oracle/python_textual/src/dust_tui").is_dir() and (
        root / "migration/oracle/python_textual/pyproject.toml"
    ).is_file()
    room_artifacts = _any_file(root, [
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/DustDatabase.kt",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt",
    ]) or any(t in (android_build_gradle + android_src) for t in ["androidx.room", "RoomDatabase", "@Dao", "@Entity"])
    ndjson_present = "NdjsonTailReader" in android_src or "scan-streams" in android_src
    legacy_navigation = any(t in ui_src for t in ["enum class DustDestination", "object DustShellNavigation", "layoutDestination("])
    cpp_scanner = "NativeDustScanner" in root_src and any(t in root_src for t in ["NativeDustScanner.scan(", "System.loadLibrary(\"dustui_scanner\")"])
    destructive = "NativeDustScanner.deleteReviewedPath(" in root_src
    go_process = "ProcessBuilder(" in android_src and "libdustui_go.so" in android_src
    go_packaged = "libdustui_go.so" in android_build_gradle and any(t in android_build_gradle for t in ["jniLibs", "go build", "Exec"])
    sqlite_code = "database/sql" in go_state and ("sql.Open(" in go_state or "sql.OpenDB(" in go_state)
    sqlite_driver = any(t in gomod for t in ["modernc.org/sqlite", "github.com/mattn/go-sqlite3", "github.com/ncruces/go-sqlite3"])

    raw_tooling: list[str] = []
    scripts = root / "android/scripts"
    if scripts.is_dir():
        raw_adb = re.compile(r"(?m)(?:^|[;|&()\s])(?:adb|\$\{?adb_bin\}?|\"\$adb_bin\")\s+")
        generic_names = re.compile(r"(?:install|release|package|final|certif|device|rooted|multi-user|performance)", re.I)
        for p in sorted(scripts.glob("*")):
            if not p.is_file():
                continue
            s = p.read_text(encoding="utf-8", errors="replace")
            if generic_names.search(p.name) and (raw_adb.search(s) or "android_build_gradle" in s or "gradle " in s):
                raw_tooling.append(str(p.relative_to(root)))

    release_kotlin = [
        r for r in [
            "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleasePackaging.kt",
            "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleaseReadiness.kt",
        ] if (root / r).is_file()
    ]
    predecessor_source_tests = [
        r for r in [
            "android/app/src/test/kotlin/com/mikeyphw/dustui/automation/AudFix04AutomationContractTest.kt",
            "android/app/src/test/kotlin/com/mikeyphw/dustui/diagnostics/AudFix05DiagnosticsContractTest.kt",
            "android/app/src/test/kotlin/com/mikeyphw/dustui/go/DomainAuthorityProductionReachabilityContractTest.kt",
            "android/app/src/test/kotlin/com/mikeyphw/dustui/go/GoRuntimeProductionBindingContractTest.kt",
        ] if (root / r).is_file()
    ]

    return {
        "legacy_python_textual_present": python_present,
        "python_textual_oracle_present": python_oracle_present,
        "room_artifacts_present": room_artifacts,
        "ndjson_lifecycle_present": ndjson_present,
        "legacy_navigation_present": legacy_navigation,
        "cpp_scanner_present": cpp_scanner,
        "legacy_destructive_executor_present": destructive,
        "android_go_process_binding_present": go_process,
        "android_go_packaging_present": go_packaged,
        "go_sqlite_implementation_present": sqlite_code and sqlite_driver,
        "generic_tooling_duplication_paths": raw_tooling,
        "release_kotlin_authority_paths": release_kotlin,
        "predecessor_source_token_paths": predecessor_source_tests,
        "go_runtime_operation_count": len(set(re.findall(r'"(domain\.[a-z0-9_.-]+)"', go_runtime))),
    }


def compute_qualification(root: Path, registry: dict[str, Any]) -> dict[str, Any]:
    nodes = {n.get("id"): n for n in registry.get("nodes", []) if isinstance(n, dict) and n.get("id")}
    missing_definitions = [n for n in REQUIRED_GATE_D_NODES if n not in nodes]
    nonpassing: list[str] = []
    invalid_bindings: list[str] = []
    historical_blockers: list[str] = []
    gate_tree_hashes: set[str] = set()

    historical = set(HISTORICAL_GATE_D_NODES)
    for node_id in REQUIRED_GATE_D_NODES:
        node = nodes.get(node_id)
        if not node:
            continue
        status = str(node.get("status") or "")
        binding = node.get("binding") or {}
        if status != "pass":
            nonpassing.append(node_id)
            if node_id in historical:
                historical_blockers.append(f"{node_id}:{status or 'missing-status'}")
            continue

        if node_id in historical:
            commit = str(binding.get("historical_commit") or "").strip()
            tree = str(binding.get("historical_tree") or "").strip()
            replay_tree = str(binding.get("replay_tree") or "").strip()
            workflow = str(binding.get("workflow") or "").strip()
            devtool_version = str(binding.get("devtool_version") or "").strip()
            run_id = str(binding.get("run_id") or "").strip()
            log_sha = str(binding.get("log_sha256") or "").strip()
            if not (commit and tree and replay_tree and workflow and devtool_version and run_id and log_sha):
                invalid_bindings.append(node_id)
                historical_blockers.append(f"{node_id}:historical-binding-incomplete")
            elif tree != replay_tree:
                invalid_bindings.append(node_id)
                historical_blockers.append(f"{node_id}:replay-tree-mismatch")
            if node_id == "historical_gate_b_exo_arm64":
                arch = str(binding.get("device_arch") or "").lower()
                device_evidence = str(binding.get("device_evidence_sha256") or "").strip()
                if arch not in {"arm64", "arm64-v8a", "aarch64"} or not device_evidence:
                    invalid_bindings.append(node_id)
                    historical_blockers.append(f"{node_id}:arm64-device-proof-missing")
            continue

        tree = str(binding.get("source_tree_sha256") or "").strip()
        if not tree:
            invalid_bindings.append(node_id)
        else:
            gate_tree_hashes.add(tree)
        if node.get("requires_artifact_binding") and not binding.get("artifact_sha256"):
            invalid_bindings.append(node_id)

    coherent_tree = len(gate_tree_hashes) <= 1
    qualified = not missing_definitions and not nonpassing and not invalid_bindings and coherent_tree
    observations = repository_observations(root)
    retirement_blockers = []
    if observations["python_textual_oracle_present"]:
        retirement_blockers.append("python-textual-oracle-live")
    if observations["room_artifacts_present"]:
        retirement_blockers.append("room-production-integration-live")
    if observations["ndjson_lifecycle_present"]:
        retirement_blockers.append("ndjson-state-transfer-live")
    if observations["generic_tooling_duplication_paths"]:
        retirement_blockers.append("generic-app-local-tooling-live")
    if observations["release_kotlin_authority_paths"]:
        retirement_blockers.append("release-kotlin-authority-live")
    if observations["predecessor_source_token_paths"]:
        retirement_blockers.append("predecessor-source-token-tests-live")
    if observations["legacy_python_textual_present"] or observations["legacy_navigation_present"] or observations["cpp_scanner_present"] or observations["legacy_destructive_executor_present"]:
        retirement_blockers.append("legacy-production-surface-live")
    if not observations["go_sqlite_implementation_present"] or not observations["android_go_process_binding_present"] or not observations["android_go_packaging_present"]:
        retirement_blockers.append("successor-authority-incomplete")

    retirement_complete = qualified and not retirement_blockers
    retirement_record_path = root / "migration/qualification/v1/ov14_retirement.json"
    retirement_record = load_json(retirement_record_path) if retirement_record_path.is_file() else {}
    retirement_record_valid = bool(
        retirement_complete
        and retirement_record.get("status") == "pass"
        and retirement_record.get("gate_d_resealed") is False
        and retirement_record.get("gate_d_ancestor_source_sha256") == next(iter(gate_tree_hashes), None)
    )

    gate_e_path = root / "migration/qualification/v1/gate_e_evidence.json"
    gate_e_record = load_json(gate_e_path) if gate_e_path.is_file() else {}
    gate_e_nodes = {
        n.get("id"): n
        for n in gate_e_record.get("nodes", [])
        if isinstance(n, dict) and n.get("id")
    }
    gate_e_missing = [n for n in REQUIRED_GATE_E_NODES if n not in gate_e_nodes]
    gate_e_nonpassing = [n for n in REQUIRED_GATE_E_NODES if gate_e_nodes.get(n, {}).get("status") != "pass"]
    gate_e_invalid: list[str] = []
    expected_post_retirement = str(retirement_record.get("post_retirement_source_sha256") or "")
    if gate_e_record:
        gate_e_schema = str(gate_e_record.get("schema") or "")
        if gate_e_schema not in {"dustui.gate-e.evidence-registry.v1", "dustui.gate-e.evidence-registry.v2"} or gate_e_record.get("status") != "pass":
            gate_e_invalid.append("registry-status-or-schema")
        if list(gate_e_record.get("required_nodes") or []) != list(REQUIRED_GATE_E_NODES):
            gate_e_invalid.append("required-node-topology")
        qualified_projection = str(
            gate_e_record.get("qualified_source_projection_sha256")
            or gate_e_record.get("source_projection_sha256")
            or ""
        )
        if qualified_projection != expected_post_retirement:
            gate_e_invalid.append("source-projection-binding")
        if gate_e_schema == "dustui.gate-e.evidence-registry.v2":
            if gate_e_record.get("qualification_identity") != "source_projection_sha256":
                gate_e_invalid.append("qualification-identity")
            if gate_e_record.get("git_identity_role") != "provenance-not-validity":
                gate_e_invalid.append("git-identity-role")
        if str(gate_e_record.get("gate_d_ancestor_source_sha256") or "") != next(iter(gate_tree_hashes), None):
            gate_e_invalid.append("gate-d-ancestor-binding")
        validation_run = str(gate_e_record.get("validation_run_id") or "").strip()
        release_run = str(gate_e_record.get("release_run_id") or "").strip()
        if not validation_run or not release_run or validation_run == release_run:
            gate_e_invalid.append("validation-release-run-cross-binding")
        if not str(gate_e_record.get("source_git_head") or "").strip() or not str(gate_e_record.get("source_git_tree") or "").strip():
            gate_e_invalid.append("git-identity-binding")
        for key in ("release_report_sha256", "release_apk_sha256", "signing_identity_sha256", "go_runtime_sha256"):
            value = str(gate_e_record.get(key) or "")
            if not re.fullmatch(r"[0-9a-fA-F]{64}", value):
                gate_e_invalid.append(key)
        for node_id in REQUIRED_GATE_E_NODES:
            node = gate_e_nodes.get(node_id)
            if not node:
                continue
            binding = node.get("binding") or {}
            if str(binding.get("source_projection_sha256") or "") != expected_post_retirement:
                gate_e_invalid.append(f"{node_id}:source-projection")
            if gate_e_schema == "dustui.gate-e.evidence-registry.v1":
                if str(binding.get("validation_run_id") or "") != validation_run:
                    gate_e_invalid.append(f"{node_id}:validation-run")
            else:
                if not str(binding.get("validated_source_projection_sha256") or "").strip():
                    gate_e_invalid.append(f"{node_id}:validated-source-projection")
                if not str(binding.get("validated_at_git_head") or "").strip():
                    gate_e_invalid.append(f"{node_id}:validated-git-provenance")
                fingerprint = str(binding.get("input_fingerprint_sha256") or "")
                if not re.fullmatch(r"[0-9a-fA-F]{64}", fingerprint):
                    gate_e_invalid.append(f"{node_id}:input-fingerprint")
                reused = binding.get("evidence_reused")
                if not isinstance(reused, bool):
                    gate_e_invalid.append(f"{node_id}:reuse-flag")
                elif reused and not str(binding.get("reused_from_validation_run_id") or binding.get("validation_run_id") or "").strip():
                    gate_e_invalid.append(f"{node_id}:reuse-lineage")
        release_node = gate_e_nodes.get("canonical_devtool_release", {})
        release_binding = release_node.get("binding") or {}
        if str(release_binding.get("release_run_id") or "") != release_run:
            gate_e_invalid.append("canonical_devtool_release:release-run")
        if str(release_binding.get("artifact_sha256") or "") != str(gate_e_record.get("release_apk_sha256") or ""):
            gate_e_invalid.append("canonical_devtool_release:artifact")
        if str(release_binding.get("runtime_sha256") or "") != str(gate_e_record.get("go_runtime_sha256") or ""):
            gate_e_invalid.append("canonical_devtool_release:runtime")
        if str(release_binding.get("signing_identity_sha256") or "") != str(gate_e_record.get("signing_identity_sha256") or ""):
            gate_e_invalid.append("canonical_devtool_release:signer")

    gate_e_qualified = bool(
        qualified
        and retirement_complete
        and retirement_record_valid
        and gate_e_record
        and not gate_e_missing
        and not gate_e_nonpassing
        and not gate_e_invalid
    )

    if qualified:
        gate_status = "qualified"
        reason = "all-required-gate-d-evidence-passed-and-bound"
        if retirement_complete and retirement_record_valid:
            ov14_status = "physically-retired"
            if gate_e_qualified:
                gate_e_status = "qualified"
            elif gate_e_record:
                gate_e_status = "qualification-evidence-invalid"
            else:
                gate_e_status = "eligible-for-qualification"
        else:
            ov14_status = "retirement-evidence-pending" if retirement_complete else "eligible-for-physical-retirement"
            gate_e_status = "blocked-on-ov14-retirement"
    else:
        gate_status = "open"
        reason = "historical-gate-evidence-incomplete-or-unrecoverable" if historical_blockers else "required-gate-d-evidence-incomplete-or-unbound"
        ov14_status = "blocked-on-gate-d"
        gate_e_status = "blocked-on-gate-d-and-ov14-retirement"

    return {
        "schema": "dustui.qualification-state.v2",
        "generated_by": "scripts/refresh_qualification_state.py",
        "evidence_registry": "migration/qualification/v1/gate_d_evidence.json",
        "historical_evidence_inventory": "migration/qualification/v1/historical_gate_evidence.json",
        "gate_d_topology": "migration/qualification/v1/gate_d_topology.json",
        "requirement_index": "migration/requirements/v2/requirements.json",
        "source_snapshot_sha256": registry.get("source_snapshot_sha256"),
        "gate_d": {
            "status": gate_status,
            "qualified": qualified,
            "reason": reason,
            "required_nodes": list(REQUIRED_GATE_D_NODES),
            "historical_nodes": list(HISTORICAL_GATE_D_NODES),
            "historical_blockers": sorted(set(historical_blockers)),
            "missing_node_definitions": missing_definitions,
            "nonpassing_nodes": nonpassing,
            "invalid_binding_nodes": sorted(set(invalid_bindings)),
            "coherent_source_tree_binding": coherent_tree,
            "passing_source_tree_sha256": next(iter(gate_tree_hashes), None) if coherent_tree else None,
        },
        "ov14": {
            "status": ov14_status,
            "retirement_complete": retirement_complete,
            "retirement_blockers": sorted(retirement_blockers),
            "retirement_evidence": "migration/qualification/v1/ov14_retirement.json" if retirement_record_valid else None,
            "post_retirement_source_sha256": retirement_record.get("post_retirement_source_sha256") if retirement_record_valid else None,
        },
        "gate_e": {
            "status": gate_e_status,
            "qualified": gate_e_qualified,
            "evidence_registry": "migration/qualification/v1/gate_e_evidence.json" if gate_e_record else None,
            "required_nodes": list(REQUIRED_GATE_E_NODES),
            "missing_node_definitions": gate_e_missing,
            "nonpassing_nodes": gate_e_nonpassing,
            "invalid_binding_nodes": sorted(set(gate_e_invalid)),
            "source_projection_sha256": (gate_e_record.get("qualified_source_projection_sha256") or gate_e_record.get("source_projection_sha256")) if gate_e_record else None,
            "source_git_head": gate_e_record.get("source_git_head") if gate_e_record else None,
            "qualification_identity": gate_e_record.get("qualification_identity") if gate_e_record else None,
            "validation_run_id": gate_e_record.get("validation_run_id") if gate_e_record else None,
            "reuse": gate_e_record.get("reuse") if gate_e_record else None,
            "release_run_id": gate_e_record.get("release_run_id") if gate_e_record else None,
            "release_apk_sha256": gate_e_record.get("release_apk_sha256") if gate_e_record else None,
            "signing_identity_sha256": gate_e_record.get("signing_identity_sha256") if gate_e_record else None,
            "go_runtime_sha256": gate_e_record.get("go_runtime_sha256") if gate_e_record else None,
        },
        "observed_repository": observations,
    }


def retirement_observation(root: Path, qualification: dict[str, Any]) -> tuple[dict[str, str], list[str]]:
    obs = qualification["observed_repository"]
    q = qualification["gate_d"]["qualified"]
    observed = {
        "cpp_scanner_runtime": "legacy-artifact-or-runtime-present" if obs["cpp_scanner_present"] else "none",
        "python_runtime": "legacy-python-textual-present" if obs["legacy_python_textual_present"] else "none",
        "python_textual_oracle": "live-parity-oracle-present" if obs["python_textual_oracle_present"] else "none",
        "room_domain_artifacts": "legacy-room-artifacts-present" if obs["room_artifacts_present"] or obs["ndjson_lifecycle_present"] else "none",
        "legacy_destructive_executor": "legacy-executor-present" if obs["legacy_destructive_executor_present"] else "none",
        "legacy_navigation": "predecessor-navigation-present" if obs["legacy_navigation_present"] else "none",
        "go_authority": "product-default-qualified" if q else "not-gate-d-qualified",
        "android_go_runtime_binding": "production-qualified" if q else ("source-binding-present-unqualified" if obs["android_go_process_binding_present"] and obs["android_go_packaging_present"] else "missing-or-unqualified"),
        "go_sqlite_persistence": "go-owned-sqlite" if obs["go_sqlite_implementation_present"] else "missing-implementation",
        "generic_release_truth": "duplicated-app-local" if (obs["generic_tooling_duplication_paths"] or obs["release_kotlin_authority_paths"]) else "devtool-only",
    }
    blockers = []
    for key, desired in DESIRED_RETIREMENT.items():
        if observed[key] != desired:
            blockers.append(f"{key}:{observed[key]}")
    return observed, sorted(blockers)


def predecessor_ui_authority_paths(root: Path) -> list[str]:
    candidates = [
        "UI_UX_CONTRACT.md",
        "RELEASE_CHECKLIST.md",
        "docs/UI_UX_CONTRACT.md",
        "docs/ANDROID_MODERN_DARK_PORTRAIT_UI.md",
        "docs/ANDROID_MODERN_DARK_PORTRAIT_FINAL_SEAL.md",
        "docs/ANDROID_DEBUG_WORKBENCH.md",
        "docs/ANDROID_DEBUG_WORKBENCH_FINAL_SEAL.md",
        "android/scripts/validate-modern-dark-portrait-ui.py",
        "android/scripts/validate-debug-workbench-contract.py",
        "docs/ANDROID_UI_UX_PHASE12_13.md",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DustNavigationModelTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/ModernDarkPortraitFinalContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/UiSourceContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DebugWorkbenchFinalContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DebugWorkbenchSourceContractTest.kt",
    ]
    stale = []
    marker = "AUD-FIX successor-aware"
    for rel in candidates:
        p = root / rel
        if p.is_file() and marker not in p.read_text(encoding="utf-8", errors="replace"):
            stale.append(rel)
    return stale


def independent_gate_claims(root: Path) -> list[str]:
    claims: list[str] = []
    dev = read_text(root, ".devtool.toml")
    for key in ["gate_d_truth", "ov15_status", "production_authority_qualified", "android_cutover_complete"]:
        if re.search(rf"(?m)^\s*{re.escape(key)}\s*=", dev):
            claims.append(f".devtool.toml:{key}")
    ledger = root / "migration/ledger/ov14_legacy_retirement_status.json"
    if ledger.is_file():
        try:
            obj = load_json(ledger)
            for key in ["gate_d_truth", "ov15_status"]:
                if key in obj:
                    claims.append(f"migration/ledger/ov14_legacy_retirement_status.json:{key}")
            if obj.get("status") == "closed" and obj.get("qualification_state_source") != "migration/qualification/v1/current_state.json":
                claims.append("migration/ledger/ov14_legacy_retirement_status.json:closed-without-canonical-state")
        except Exception:
            claims.append("migration/ledger/ov14_legacy_retirement_status.json:invalid-json")
    for rel in [
        "migration/audit/v1/rem05_legacy_surface_release_truth_retirement_audit.md",
        "migration/contracts/v1/rem05_legacy_surface_release_truth_retirement.md",
    ]:
        body = read_text(root, rel)
        if body and "AUD-FIX successor-aware" not in body and re.search(r"(?i)(gate d.{0,40}closed|ov15.{0,40}(ready|eligible))", body):
            claims.append(f"{rel}:unmarked-predecessor-gate-claim")
    product = read_text(root, "go/internal/product/product.go")
    if re.search(r"GateDQualified:\s*(?:true|false)", product):
        claims.append("go/internal/product/product.go:literal-GateDQualified")
    return claims


def evaluate_fixture(fixture: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    if fixture.get("independent_gate_claims"):
        failures.append("contradictory-independent-gate-truth")
    if fixture.get("stale_predecessor_ui_authority"):
        failures.append("stale-predecessor-ui-authority")
    if fixture.get("retirement_claim") == "closed" and fixture.get("retirement_reachable_legacy", False):
        failures.append("fake-retirement-closure")
    missing = fixture.get("missing_required_evidence_nodes") or []
    if missing and fixture.get("gate_d_claim") == "qualified":
        failures.append("gate-d-qualified-with-missing-evidence")
    if fixture.get("raw_tooling_duplication"):
        failures.append("generic-devtool-authority-duplication")
    if fixture.get("route_tree_missing"):
        failures.append("semantic-route-tree-incomplete")
    if fixture.get("shared_state_declared_but_unused"):
        failures.append("shared-state-gate-declaration-without-use")
    if fixture.get("historical_exo_missing"):
        failures.append("historical-exo-gate-evidence-missing")
    return failures
