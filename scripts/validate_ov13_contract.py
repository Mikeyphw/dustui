#!/usr/bin/env python3
"""Validate DustUI OV13 Gate D product reconnect contracts."""
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
    require(am.get('migration_phase') == 'OV13', 'dustui_android migration_phase must be OV13', failures)
    require(gm.get('migration_phase') == 'OV13', 'dustui_go migration_phase must be OV13', failures)
    require(am.get('android_buildability') == 'mandatory', 'Android buildability must be mandatory at OV13', failures)
    require(am.get('gate') == 'Gate D - Android reconnect mandatory', 'Android Gate D metadata missing', failures)
    require(go.get('metadata', {}).get('product_default') is True, 'Go product default flag missing', failures)
    require(gm.get('go_cli_default') is True, 'Go CLI default flag missing', failures)
    require(gm.get('go_tui_default') is True, 'Go TUI default flag missing', failures)
    require(gm.get('android_reconnect_gate') == 'Gate D', 'Android reconnect gate metadata mismatch', failures)
    require(gm.get('legacy_python_textual_production') is False, 'legacy Python/Textual must not be production after Gate D', failures)
    require(gm.get('legacy_kotlin_domain_truth') is False, 'legacy Kotlin domain truth must be false after Gate D', failures)
    wrapper = devtool.get('wrapper', {})
    require(wrapper.get('schema') == 1, 'DustUI devtool wrapper schema=1 missing', failures)
    require(wrapper.get('default_command') == 'gate-d', 'DustUI wrapper default command must be gate-d', failures)
    wrapper_commands = wrapper.get('commands', {})
    for command in ['gate-d', 'fast', 'unit', 'go-fast', 'build', 'test', 'lint']:
        require(command in wrapper_commands, f'DustUI wrapper command missing: {command}', failures)
    jobs = android.get('jobs', {})
    workflows = android.get('workflows', {})
    for job in ['ov13_contract', 'gradle_contract', 'go_test', 'android_unit', 'android_lint', 'android_assemble']:
        require(job in jobs, f'DustUI android target-local EXO job missing: {job}', failures)
    require(workflows.get('fast') == ['job:ov13_contract', 'job:go_test', 'job:android_unit'], 'DustUI fast workflow must use target-local jobs', failures)
    require(workflows.get('gate_d') == ['job:ov13_contract', 'job:go_test', 'job:gradle_contract', 'job:android_unit', 'job:android_lint', 'job:android_assemble'], 'DustUI Gate D workflow must use target-local jobs', failures)
    go_jobs = go.get('jobs', {})
    go_workflows = go.get('workflows', {})
    for job in ['go_test', 'product_contract', 'tui_smoke', 'schema_contract']:
        require(job in go_jobs, f'DustUI Go target-local EXO job missing: {job}', failures)
    require(go_workflows.get('fast') == ['job:go_test', 'job:product_contract', 'job:tui_smoke', 'job:schema_contract'], 'DustUI Go fast workflow must use target-local jobs', failures)
    require((ROOT / 'devtoolw').is_file(), 'devtoolw launcher missing', failures)
    require((ROOT / 'devtoolw.cmd').is_file(), 'devtoolw.cmd launcher missing', failures)
    for rel in [
        'go/internal/product/product.go',
        'go/internal/product/product_test.go',
        'go/internal/tui/tui.go',
        'go/internal/tui/tui_test.go',
        'android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductCutoverContract.kt',
        'android/app/src/test/kotlin/com/mikeyphw/dustui/go/ProductCutoverContractTest.kt',
        'go/internal/state/schema.go',
        'go/internal/readiness/readiness.go',
        'go/cmd/dustui-go/main.go',
    ]:
        require((ROOT / rel).is_file(), f'OV13 file missing: {rel}', failures)
    product = run_json(['go', 'run', './cmd/dustui-go', 'product-contract'])
    require(product.get('phase') == 'OV13', 'product contract phase must be OV13', failures)
    require(product.get('protocol') == 'dustui.product.cutover.v1', 'product protocol mismatch', failures)
    require(product.get('gate') == 'Gate D', 'product gate must be Gate D', failures)
    require(product.get('androidBuildability') == 'mandatory', 'product contract must make Android mandatory', failures)
    require(product.get('androidBuildabilityMandatory') is True, 'product contract must mark Android buildability mandatory', failures)
    require(product.get('productionEngine') is True, 'product contract must make Go production engine', failures)
    require(product.get('goSQLiteProductDefault') is True, 'Go SQLite must be product default', failures)
    require(product.get('legacyDomainEnginesRequired') is False, 'legacy domain engines must not be required', failures)
    require(product.get('legacyPythonTextualProduction') is False, 'Python/Textual must not be production', failures)
    for route in ['overview', 'files', 'clean', 'apps', 'activity', 'system']:
        require(route in product.get('routes', []), f'route missing: {route}', failures)
    surfaces = {item.get('name'): item for item in product.get('surfaces', [])}
    for surface in ['go-cli', 'go-tui', 'android-shell']:
        require(surface in surfaces, f'surface missing: {surface}', failures)
        if surface in surfaces:
            require(surfaces[surface].get('productDefault') is True, f'{surface} must be product default', failures)
            require(surfaces[surface].get('usesSameEngineSemantics') is True, f'{surface} must use same engine semantics', failures)
            require(len(surfaces[surface].get('supportedWorkflows', [])) >= 7, f'{surface} workflow coverage incomplete', failures)
    for evidence in ['android-compile', 'android-unit', 'android-lint', 'android-package', 'route-instrumentation', 'accessibility', 'runtime-apk-binding', 'go-tui-smoke']:
        require(evidence in product.get('requiredEvidence', []), f'Gate D evidence missing: {evidence}', failures)
    require(product.get('dustTuiAliasImplementation') == 'go-cli-or-go-tui-wrapper-after-gate-d', 'dust-tui alias implementation contract missing', failures)
    tui = run_json(['go', 'run', './cmd/dustui-go', 'tui-smoke'])
    require(tui.get('phase') == 'OV13', 'TUI smoke phase must be OV13', failures)
    require(tui.get('surface') == 'go-tui', 'TUI smoke surface mismatch', failures)
    require(tui.get('sharedEngineSemantics') is True, 'TUI smoke must use shared engine semantics', failures)
    require(set(tui.get('layouts', [])) == {'compact', 'wide'}, 'TUI smoke must cover compact/wide layouts', failures)
    readiness = run_json(['go', 'run', './cmd/dustui-go', 'readiness'])
    require(readiness.get('migrationPhase') == 'OV13', 'readiness phase must be OV13', failures)
    require(readiness.get('productionEngine') is True, 'readiness must expose production engine', failures)
    require(readiness.get('androidBuildability') == 'mandatory', 'readiness must expose mandatory Android buildability', failures)
    require(readiness.get('goCliDefault') is True and readiness.get('goTuiDefault') is True, 'readiness must expose Go CLI/TUI defaults', failures)
    schema = run_json(['go', 'run', './cmd/dustui-go', 'schema'])
    require(schema.get('schemaVersion') == 10, 'OV13 schema version must be 10', failures)
    tables = schema.get('requiredTables', [])
    for table in ['product_surface_parity', 'android_reconnect_evidence', 'route_accessibility_contracts']:
        require(table in tables, f'schema must include {table}', failures)
    joined = '\n'.join(schema.get('createStatements', []))
    for token in ['legacy_python_textual_required', 'shared_engine_semantics', 'go_runtime_digest', 'apk_digest', 'signing_identity', 'semantic_identity', 'accessibility_label', 'traversal_group']:
        require(token in joined, f'OV13 schema token missing: {token}', failures)
    android_contract = read('android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductCutoverContract.kt')
    for token in ['AndroidBuildability: String = "mandatory"', 'GoRuntimeAuthority', 'LegacyPythonTextualProduction: Boolean = false', 'LegacyKotlinDomainTruth: Boolean = false', 'phoneAndTabletFixtures']:
        require(token in android_contract, f'Android product cutover token missing: {token}', failures)
    gradle = read('android/build.gradle.kts')
    require('scripts/validate_ov13_contract.py' in gradle, 'verifyDustMigrationContract must point at OV13 validator', failures)
    for rel in [
        'migration/contracts/v1/product_cutover_gateD_ov13.md',
        'migration/contracts/v1/authority_transition_ov13.md',
        'migration/contracts/v1/legacy_paths_ov13.md',
        'migration/contracts/v1/source_token_retirement_ov13.md',
        'migration/audit/v1/ov13_audit_loop.md',
        'migration/ledger/ov13_status.json',
    ]:
        require((ROOT / rel).is_file(), f'OV13 contract missing: {rel}', failures)
    findings = json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase') == 'OV13', 'migration ledger phase must advance to OV13', failures)
    by_id = {item.get('id'): item for item in findings.get('findings', [])}
    for fid in ['RC-01', 'RC-06', 'RC-10', 'RC-11', 'RC-12', 'RC-13', 'RC-14', 'N-05', 'ABH-16']:
        require(fid in by_id and 'OV13' in by_id[fid].get('overlays', []), f'OV13 ledger mapping missing: {fid}', failures)
        require(by_id[fid].get('ov13_status'), f'OV13 ledger status missing: {fid}', failures)
    status = json.loads(read('migration/ledger/ov13_status.json'))
    require(status.get('android_buildability') == 'mandatory', 'OV13 status must mark Android mandatory', failures)
    require(status.get('legacy_domain_engines_required') is False, 'OV13 status must make legacy domain engines unnecessary', failures)
    if audit_loop:
        combined = '\n'.join(read(rel) for rel in [
            'migration/contracts/v1/product_cutover_gateD_ov13.md',
            'migration/contracts/v1/authority_transition_ov13.md',
            'migration/contracts/v1/legacy_paths_ov13.md',
            'migration/contracts/v1/source_token_retirement_ov13.md',
            'migration/audit/v1/ov13_audit_loop.md',
        ])
        for phrase in [
            'Android buildability is mandatory again',
            'Go CLI and Go TUI are product surfaces',
            'Python/Textual is retained only as a frozen parity oracle',
            'Overview, Files, Clean, Apps, Activity, and System',
            'Gate D remains open until target-side Devtool Android validation',
        ]:
            require(phrase in combined, f'OV13 audit loop missing: {phrase}', failures)
    return failures

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--audit-loop', action='store_true')
    args = parser.parse_args()
    failures = validate(args.audit_loop)
    if failures:
        for failure in failures:
            print(f'OV13 contract failure: {failure}', file=sys.stderr)
        return 1
    print('DustUI OV13 product cutover / Gate D contract passed')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
