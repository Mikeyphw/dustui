from __future__ import annotations

import hashlib
import json
import re
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
LIFECYCLE = ROOT / "migration/acceptance/v1/validator_lifecycle.json"
MATRIX = ROOT / "migration/acceptance/v1/ui_acceptance_matrix.json"
QUALIFICATION = ROOT / "migration/qualification/v1/current_state.json"
GATE_EVIDENCE = ROOT / "migration/qualification/v1/gate_d_evidence.json"
REQUIREMENTS = ROOT / "migration/requirements/v2/requirements.json"
DEVTOOL = ROOT / ".devtool.toml"


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def discover_live_source_reading_tests(root: Path = ROOT) -> list[str]:
    base = root / "android/app/src/test/kotlin"
    found: list[str] = []
    if not base.is_dir():
        return found
    for path in sorted(base.rglob("*.kt")):
        body = path.read_text(encoding="utf-8", errors="replace")
        if ".readText(" in body or "readText()" in body:
            found.append(path.relative_to(root).as_posix())
    return found


def gate_node_ids(root: Path = ROOT) -> set[str]:
    data = load_json(root / "migration/qualification/v1/gate_d_evidence.json")
    return {str(n.get("id")) for n in data.get("nodes", []) if isinstance(n, dict) and n.get("id")}


def workflow_ids(root: Path = ROOT) -> set[str]:
    cfg = tomllib.loads((root / ".devtool.toml").read_text(encoding="utf-8"))
    out: set[str] = set()
    for target in cfg.get("targets", {}).values():
        if isinstance(target, dict):
            out.update((target.get("workflows") or {}).keys())
    return out


def resolve_evidence(ref: str, root: Path = ROOT) -> bool:
    if ref.startswith("file:"):
        return (root / ref[5:]).exists()
    if ref.startswith("gate:"):
        return ref[5:] in gate_node_ids(root)
    if ref.startswith("workflow:"):
        return ref[9:] in workflow_ids(root)
    if ref.startswith("future:"):
        # A future evidence owner is valid only when it names the exact roadmap/gate owner.
        return bool(re.fullmatch(r"AUD-FIX-(?:14|15|16|17):[A-Za-z0-9_.-]+", ref[7:]))
    return False


def lifecycle_failures(root: Path = ROOT) -> list[str]:
    failures: list[str] = []
    data = load_json(root / "migration/acceptance/v1/validator_lifecycle.json")
    entries = data.get("validators", [])
    by_path = {e.get("path"): e for e in entries if isinstance(e, dict) and e.get("path")}
    discovered = set(discover_live_source_reading_tests(root))
    declared_live = {p for p, e in by_path.items() if str(e.get("status", "")).startswith("live-")}
    if discovered != declared_live:
        missing = sorted(discovered - declared_live)
        phantom = sorted(declared_live - discovered)
        if missing:
            failures.append("unmapped live source-reading tests: " + ", ".join(missing))
        if phantom:
            failures.append("lifecycle declares non-live source readers: " + ", ".join(phantom))
    historical_scripts = data.get("historical_script_validators", {})
    archive_base = root / "migration/archive/validators/aud_fix_13_pre_gate_d"
    for rel in historical_scripts.get("archived", []):
        rel = str(rel)
        if (root / rel).exists():
            failures.append(f"{rel}: historical validator still live")
        if not (archive_base / rel).is_file():
            failures.append(f"{rel}: archived script copy missing")
    for rel in historical_scripts.get("live_successors", []):
        if not (root / str(rel)).is_file():
            failures.append(f"{rel}: declared live successor missing")

    for path, entry in sorted(by_path.items()):
        status = str(entry.get("status", ""))
        successors = entry.get("successor_evidence") or []
        if not successors:
            failures.append(f"{path}: no exact successor evidence")
        for successor in successors:
            if not resolve_evidence(str(successor), root):
                failures.append(f"{path}: unresolved successor {successor}")
        if status == "archived-historical":
            if (root / path).exists():
                failures.append(f"{path}: archived validator still lives in current test source")
            archive_path = entry.get("archive_path")
            if not archive_path or not (root / str(archive_path)).is_file():
                failures.append(f"{path}: archive copy missing")
        elif status.startswith("live-"):
            if not (root / path).is_file():
                failures.append(f"{path}: live validator missing")
            if entry.get("acceptance_authority") not in {"pre-gate-static-evidence", "fixture-contract"}:
                failures.append(f"{path}: live source reader has invalid acceptance authority")
        else:
            failures.append(f"{path}: unknown lifecycle status {status!r}")
    return failures


def ui_matrix_failures(root: Path = ROOT) -> list[str]:
    failures: list[str] = []
    req = load_json(root / "migration/requirements/v2/requirements.json")
    required = {r["id"] for r in req.get("requirements", []) if r.get("class") == "uiux-authority"}
    matrix = load_json(root / "migration/acceptance/v1/ui_acceptance_matrix.json")
    rows = {r.get("requirement_id"): r for r in matrix.get("requirements", []) if isinstance(r, dict)}
    missing = sorted(required - set(rows))
    extra = sorted(set(rows) - required)
    if missing:
        failures.append("UI acceptance matrix missing: " + ", ".join(missing))
    if extra:
        failures.append("UI acceptance matrix has unknown IDs: " + ", ".join(extra))
    allowed = {"candidate-covered", "gate-d-required", "gate-e-required"}
    for rid in sorted(required & set(rows)):
        row = rows[rid]
        status = row.get("candidate_status")
        if status not in allowed:
            failures.append(f"{rid}: invalid candidate_status {status!r}")
        evidence = row.get("evidence") or []
        if not evidence:
            failures.append(f"{rid}: no evidence mapping")
        for ref in evidence:
            if not resolve_evidence(str(ref), root):
                failures.append(f"{rid}: unresolved evidence {ref}")
    tasks=matrix.get("user_tasks") or []
    if len(tasks) != 14:
        failures.append(f"UX task matrix must contain 14 authoritative tasks, got {len(tasks)}")
    rules=matrix.get("visual_product_rules") or []
    if [r.get("id") for r in rules] != [f"VR-{i:02d}" for i in range(1,11)]:
        failures.append("visual/product acceptance rules must preserve VR-01..VR-10")
    for collection_name, collection in (("task",tasks),("visual-rule",rules)):
        for row in collection:
            for ref in row.get("evidence") or []:
                if not resolve_evidence(str(ref), root):
                    failures.append(f"{collection_name} {row.get('id')}: unresolved evidence {ref}")
    return failures


def quarantine_paths(root: Path = ROOT) -> list[str]:
    state = load_json(root / "migration/qualification/v1/current_state.json")
    return sorted(state.get("observed_repository", {}).get("generic_tooling_duplication_paths", []))


def metadata_failures(root: Path = ROOT) -> list[str]:
    failures: list[str] = []
    qualification = load_json(root / "migration/qualification/v1/current_state.json")
    cfg = tomllib.loads((root / ".devtool.toml").read_text(encoding="utf-8"))
    targets = cfg.get("targets", {})
    for target_name in ("dustui_android", "dustui_go"):
        md = (targets.get(target_name) or {}).get("metadata", {})
        if md.get("qualification_state_source") != "migration/qualification/v1/current_state.json":
            failures.append(f"{target_name}: qualification state source is not canonical")
        if md.get("acceptance_state_source") != "migration/acceptance/v1/current_state.json":
            failures.append(f"{target_name}: acceptance state source is not canonical")
        for forbidden in ("production_authority_qualified", "android_cutover_complete", "gate_d_truth", "ov15_status"):
            if forbidden in md:
                failures.append(f"{target_name}: independent qualified claim {forbidden}")
    gate = qualification.get("gate_d", {})
    if gate.get("status") not in {"open", "qualified"} or not isinstance(gate.get("qualified"), bool):
        failures.append("canonical Gate-D qualification state is invalid")
    go_md = (targets.get("dustui_go") or {}).get("metadata", {})
    if go_md.get("target_product_default_scope") != "host-cli-tui-only":
        failures.append("Go target product-default declaration is not explicitly host-scoped")
    # Gate-D/current acceptance orchestration must not call quarantined generic helpers.
    serialized = json.dumps({
        "aud_fix_13": ((targets.get("dustui_android") or {}).get("workflows") or {}).get("aud_fix_13"),
        "gate_d": ((targets.get("dustui_android") or {}).get("workflows") or {}).get("gate_d"),
    })
    for path in quarantine_paths(root):
        if path in serialized or Path(path).name in serialized:
            failures.append(f"Devtool workflow depends on quarantined helper {path}")
    return failures


def docs_failures(root: Path = ROOT) -> list[str]:
    failures: list[str] = []
    markers = ("AUD-FIX-13 acceptance authority", "AUD-FIX successor-aware")
    for rel in ("README.md", "RELEASE_CHECKLIST.md", "VALIDATION.md", "UI_UX_CONTRACT.md", "docs/UI_UX_CONTRACT.md"):
        p = root / rel
        text = p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""
        if not any(marker in text for marker in markers):
            failures.append(f"current acceptance doc not successorized: {rel}")
    return failures


def compute_state(root: Path = ROOT) -> dict[str, Any]:
    lifecycle = lifecycle_failures(root)
    ui = ui_matrix_failures(root)
    metadata = metadata_failures(root)
    docs = docs_failures(root)
    failures = lifecycle + ui + metadata + docs
    qualification = load_json(root / "migration/qualification/v1/current_state.json")
    matrix = load_json(root / "migration/acceptance/v1/ui_acceptance_matrix.json")
    counts: dict[str, int] = {}
    for row in matrix.get("requirements", []):
        status = str(row.get("candidate_status"))
        counts[status] = counts.get(status, 0) + 1
    return {
        "schema": "dustui.acceptance-state.v1",
        "generated_by": "scripts/refresh_acceptance_state.py",
        "qualification_state_source": "migration/qualification/v1/current_state.json",
        "requirement_index": "migration/requirements/v2/requirements.json",
        "validator_lifecycle": "migration/acceptance/v1/validator_lifecycle.json",
        "ui_acceptance_matrix": "migration/acceptance/v1/ui_acceptance_matrix.json",
        "source_snapshot_sha256": qualification.get("source_snapshot_sha256"),
        "candidate": {
            "acceptance_converged": not failures,
            "status": (
                "gate-e-qualified-final-converged"
                if qualification.get("gate_e", {}).get("qualified")
                else ("post-gate-d-successor-converged" if qualification.get("gate_d", {}).get("qualified") else "pre-gate-d-ready")
            ) if not failures else "acceptance-contradiction",
            "failures": failures,
            "ui_requirement_status_counts": counts,
            "live_source_reading_validators": discover_live_source_reading_tests(root),
            "quarantined_generic_helpers": quarantine_paths(root),
        },
        "qualification": {
            "gate_d_status": qualification.get("gate_d", {}).get("status"),
            "gate_d_qualified": qualification.get("gate_d", {}).get("qualified"),
            "gate_e_status": qualification.get("gate_e", {}).get("status"),
            "gate_e_qualified": qualification.get("gate_e", {}).get("qualified"),
            "statement": "acceptance convergence follows canonical Gate-D/Gate-E truth and does not independently qualify any gate",
        },
    }


def evaluate_counterexample(case: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    if case.get("unmapped_live_source_guard"):
        failures.append("unmapped-live-source-guard")
    if case.get("missing_successor_evidence"):
        failures.append("missing-exact-successor-evidence")
    if case.get("gate_d_qualified_without_evidence"):
        failures.append("false-gate-d-qualified-metadata")
    if case.get("quarantined_helper_in_gate_d"):
        failures.append("quarantined-generic-helper-in-gate-d")
    if case.get("missing_uiux_requirement"):
        failures.append("uiux-acceptance-matrix-incomplete")
    if case.get("predecessor_doc_authoritative"):
        failures.append("predecessor-doc-current-authority")
    if case.get("visual_without_text_equivalent"):
        failures.append("visual-data-without-text-equivalent")
    if case.get("destructive_action_outside_review"):
        failures.append("destructive-action-outside-review-context")
    if case.get("state_collapse"):
        failures.append("empty-stale-failed-blocked-collapsed")
    return failures
