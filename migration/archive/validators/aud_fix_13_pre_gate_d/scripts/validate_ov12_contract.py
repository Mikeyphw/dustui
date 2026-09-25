#!/usr/bin/env python3
"""Validate DustUI OV12 diagnostics/evidence/privacy contracts."""
from __future__ import annotations
import argparse, json, subprocess, sys, tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')

def require(cond: bool, msg: str, failures: list[str]) -> None:
    if not cond:
        failures.append(msg)

def run_json(args: list[str]) -> dict:
    proc = subprocess.run(args, cwd=ROOT / 'go', text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    return json.loads(proc.stdout)

def validate(audit_loop: bool = False) -> list[str]:
    failures: list[str] = []
    devtool = tomllib.loads(read('.devtool.toml'))
    targets = devtool.get('targets', {})
    android = targets.get('dustui_android', {})
    go = targets.get('dustui_go', {})
    am = android.get('metadata', {})
    gm = go.get('metadata', {})
    require(am.get('migration_phase') == 'OV12', 'dustui_android migration_phase must be OV12', failures)
    require(gm.get('migration_phase') == 'OV12', 'dustui_go migration_phase must be OV12', failures)
    require(am.get('android_buildability') == 'intentionally-suspended', 'Android buildability must remain intentionally suspended', failures)
    require(am.get('android_buildability_suspended_until') == 'OV13', 'Android reconnect gate must remain OV13', failures)
    require(gm.get('diagnostics_evidence_authority') is True, 'OV12 must claim diagnostics/evidence authority', failures)
    require(gm.get('diagnostics_evidence_protocol') == 'dustui.diagnostics.evidence.v1', 'diagnostics protocol mismatch', failures)
    require(gm.get('privacy_bounded_support_bundle') is True, 'privacy-bounded support bundle flag missing', failures)
    require('jobs' not in android and 'workflows' not in android, 'unsupported Devtool jobs/workflows must not be reintroduced', failures)
    for rel in [
        'go/internal/diagnostics/diagnostics.go',
        'go/internal/diagnostics/diagnostics_test.go',
        'android/app/src/main/kotlin/com/mikeyphw/dustui/go/DiagnosticsEvidenceContract.kt',
        'go/internal/state/schema.go',
        'go/internal/readiness/readiness.go',
        'go/cmd/dustui-go/main.go',
    ]:
        require((ROOT / rel).is_file(), f'OV12 file missing: {rel}', failures)
    contract = run_json(['go', 'run', './cmd/dustui-go', 'diagnostics-contract'])
    require(contract.get('protocol') == 'dustui.diagnostics.evidence.v1', 'diagnostics protocol mismatch', failures)
    require(contract.get('phase') == 'OV12', 'diagnostics phase must be OV12', failures)
    require(contract.get('completedAloneHealthy') is False, 'COMPLETED alone must never be healthy', failures)
    require(contract.get('derivedHealthInvalidates') is True, 'derived health invalidation missing', failures)
    require(contract.get('problemGraph') is True, 'problem graph missing', failures)
    require(contract.get('rootCauseSuppression') is True, 'root-cause suppression missing', failures)
    require(contract.get('stableProblemIds') is True, 'stable problem IDs missing', failures)
    require(contract.get('structuredPersistentEvents') is True, 'structured diagnostics missing', failures)
    require(contract.get('redactionBeforePersistence') is True, 'redaction before persistence missing', failures)
    require(contract.get('boundedRetention') is True, 'bounded retention missing', failures)
    require(contract.get('automaticUpload') is False, 'automatic upload must be false', failures)
    require(contract.get('supportBundle') is True, 'support bundle contract missing', failures)
    require(contract.get('canonicalUx') == 'System > Diagnostics', 'canonical UX must be System > Diagnostics', failures)
    for state in ['missing', 'stale', 'current', 'degraded']:
        require(state in contract.get('evidenceStates', []), f'evidence state missing: {state}', failures)
    for privacy in ['raw_paths', 'hashes', 'package_names', 'root_device_metadata']:
        require(privacy in contract.get('privacyClasses', []), f'privacy class missing: {privacy}', failures)
    readiness = run_json(['go', 'run', './cmd/dustui-go', 'readiness'])
    require(readiness.get('migrationPhase') == 'OV12', 'readiness phase must be OV12', failures)
    require(readiness.get('diagnosticsEvidence') is True, 'readiness must expose diagnostics evidence', failures)
    require(readiness.get('privacyBoundedSupport') is True, 'readiness must expose privacy-bounded support', failures)
    require(readiness.get('productionEngine') is False, 'Android production engine must remain suspended until OV13', failures)
    schema = run_json(['go', 'run', './cmd/dustui-go', 'schema'])
    require(schema.get('schemaVersion') == 9, 'OV12 schema version must be 9', failures)
    tables = schema.get('requiredTables', [])
    for table in ['evidence_health_generations', 'problem_graph_nodes', 'diagnostic_events', 'significant_problem_records', 'privacy_retention_rules', 'support_bundles']:
        require(table in tables, f'schema must include {table}', failures)
    joined = '\n'.join(schema.get('createStatements', []))
    for token in ['completed_alone_healthy', 'invalidated_by_json', 'stable_id', 'suppressed_by_root', 'values_redacted_json', 'raw_values_persisted', 'auto_upload', 'export_redaction', 'backup_transfer_eligible', 'includes_file_content', 'reproducible_export_key']:
        require(token in joined, f'OV12 schema token missing: {token}', failures)
    for rel in [
        'migration/contracts/v1/diagnostics_evidence_privacy_ov12.md',
        'migration/contracts/v1/authority_transition_ov12.md',
        'migration/contracts/v1/legacy_paths_ov12.md',
        'migration/contracts/v1/source_token_retirement_ov12.md',
        'migration/audit/v1/ov12_audit_loop.md',
        'migration/ledger/ov12_status.json',
    ]:
        require((ROOT / rel).is_file(), f'OV12 contract missing: {rel}', failures)
    findings = json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase') == 'OV12', 'migration ledger phase must advance to OV12', failures)
    by_id = {item.get('id'): item for item in findings.get('findings', [])}
    for fid in ['RC-01', 'RC-11', 'RC-14', 'N-05', 'ABH-01', 'ABH-16', 'GO14']:
        require(fid in by_id and 'OV12' in by_id[fid].get('overlays', []), f'OV12 ledger mapping missing: {fid}', failures)
        require(by_id[fid].get('ov12_status'), f'OV12 ledger status missing: {fid}', failures)
    if audit_loop:
        combined = '\n'.join(read(rel) for rel in [
            'migration/contracts/v1/diagnostics_evidence_privacy_ov12.md',
            'migration/contracts/v1/authority_transition_ov12.md',
            'migration/contracts/v1/legacy_paths_ov12.md',
            'migration/contracts/v1/source_token_retirement_ov12.md',
            'migration/audit/v1/ov12_audit_loop.md',
        ])
        for phrase in [
            'COMPLETED alone can never produce healthy',
            'stale evidence is visibly stale',
            'root causes suppress dependent symptoms',
            'redaction happens before persistence',
            'retention/pruning/export rules are deterministic',
            'support bundle is sealed',
            'Android buildability remains intentionally suspended',
        ]:
            require(phrase in combined, f'OV12 audit loop missing: {phrase}', failures)
    return failures

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--audit-loop', action='store_true')
    args = parser.parse_args()
    failures = validate(args.audit_loop)
    if failures:
        for failure in failures:
            print(f'OV12 contract failure: {failure}', file=sys.stderr)
        return 1
    print('DustUI OV12 diagnostics/evidence/privacy contract passed')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
