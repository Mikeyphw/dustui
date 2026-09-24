#!/usr/bin/env python3
"""Validate DustUI Devtool wrapper + EXO target-local job adoption.

This validator intentionally accepts both the Devtool JSON shape and the Rich
text/table output shape because the new wrapper/jobs/plan commands may render
human output even when older validators expect a specific JSON object layout.
The static TOML contract remains strict; dynamic checks prove the installed
Devtool can see the same wrapper commands, jobs, and Gate D plan.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]


def require(cond: bool, msg: str, failures: list[str]) -> None:
    if not cond:
        failures.append(msg)


def run_capture(argv: list[str]) -> tuple[str, Any | None]:
    proc = subprocess.run(argv, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out = (proc.stdout or '').strip()
    err = (proc.stderr or '').strip()
    combined = '\n'.join(part for part in [out, err] if part)
    if proc.returncode != 0:
        raise RuntimeError(combined)
    try:
        return combined, json.loads(out)
    except Exception:
        return combined, None


def flatten_json(value: Any) -> str:
    parts: list[str] = []
    def walk(v: Any) -> None:
        if isinstance(v, dict):
            for k, vv in v.items():
                parts.append(str(k))
                walk(vv)
        elif isinstance(v, list):
            for item in v:
                walk(item)
        elif v is not None:
            parts.append(str(v))
    walk(value)
    return ' '.join(parts)


def contains_any(haystack: str, needles: list[str]) -> bool:
    return any(needle in haystack for needle in needles)


def static_validate() -> list[str]:
    failures: list[str] = []
    cfg = tomllib.loads((ROOT / '.devtool.toml').read_text(encoding='utf-8'))
    wrapper = cfg.get('wrapper', {})
    commands = wrapper.get('commands', {})
    targets = cfg.get('targets', {})
    android = targets.get('dustui_android', {})
    go = targets.get('dustui_go', {})

    require(wrapper.get('schema') == 1, '[wrapper].schema must be 1', failures)
    require(wrapper.get('default_command') == 'gate-d', '[wrapper].default_command must be gate-d', failures)
    for command in ['gate-d', 'cutover-audit', 'fast', 'unit', 'go-fast', 'build', 'test', 'lint']:
        require(command in commands, f'wrapper command missing: {command}', failures)
    require(commands.get('gate-d', {}).get('workflow') == 'gate_d', 'gate-d must resolve to gate_d workflow', failures)
    require(commands.get('gate-d', {}).get('target') == 'dustui_android', 'gate-d must target dustui_android', failures)
    require(commands.get('go-fast', {}).get('workflow') == 'fast', 'go-fast must resolve to Go fast workflow', failures)
    require(commands.get('go-fast', {}).get('target') == 'dustui_go', 'go-fast must target dustui_go', failures)

    android_jobs = android.get('jobs', {})
    android_workflows = android.get('workflows', {})
    for job in ['retirement_audit', 'retirement_gate', 'ov13_contract', 'gradle_contract', 'go_test', 'android_unit', 'android_lint', 'android_assemble']:
        require(job in android_jobs, f'android target-local job missing: {job}', failures)
    require(android_jobs.get('go_test', {}).get('root') == '../go', 'android go_test job must execute from ../go', failures)
    require(android_jobs.get('android_unit', {}).get('gradle_tasks') == [':app:testDebugUnitTest'], 'android_unit gradle task mismatch', failures)
    require(android_workflows.get('fast') == ['job:ov13_contract', 'job:go_test', 'job:android_unit'], 'android fast workflow mismatch', failures)
    require(android_workflows.get('cutover_audit') == ['job:retirement_audit'], 'android cutover_audit workflow mismatch', failures)
    require(android_workflows.get('gate_d') == ['job:retirement_gate', 'job:ov13_contract', 'job:go_test', 'job:gradle_contract', 'job:android_unit', 'job:android_lint', 'job:android_assemble'], 'android gate_d workflow must begin with retirement truth gate', failures)

    go_jobs = go.get('jobs', {})
    go_workflows = go.get('workflows', {})
    for job in ['go_test', 'product_contract', 'tui_smoke', 'schema_contract']:
        require(job in go_jobs, f'go target-local job missing: {job}', failures)
    require(go_workflows.get('fast') == ['job:go_test', 'job:product_contract', 'job:tui_smoke', 'job:schema_contract'], 'go fast workflow mismatch', failures)

    launcher = ROOT / 'devtoolw'
    launcher_cmd = ROOT / 'devtoolw.cmd'
    require(launcher.is_file(), 'devtoolw launcher missing', failures)
    require(launcher_cmd.is_file(), 'devtoolw.cmd launcher missing', failures)
    if launcher.is_file():
        text = launcher.read_text(encoding='utf-8')
        require('DEVTOOL_WRAPPER_LAUNCHER=1' in text, 'devtoolw marker missing', failures)
        require('wrapper exec' in text, 'devtoolw must delegate to wrapper exec', failures)
    if launcher_cmd.is_file():
        text = launcher_cmd.read_text(encoding='utf-8', errors='ignore')
        require('DEVTOOL_WRAPPER_LAUNCHER=1' in text, 'devtoolw.cmd marker missing', failures)
        require('wrapper exec' in text, 'devtoolw.cmd must delegate to wrapper exec', failures)

    return failures


def dynamic_validate() -> list[str]:
    failures: list[str] = []
    devtool = shutil.which('devtool')
    if not devtool:
        return ['devtool executable not found; install/apply the uploaded Devtool snapshot before dynamic wrapper validation']

    describe_text, describe_json = run_capture([devtool, '-r', str(ROOT), 'wrapper', 'describe', '--json'])
    describe_blob = describe_text + '\n' + (flatten_json(describe_json) if describe_json is not None else '')
    require(
        contains_any(describe_blob, ['default_command gate-d', 'default_command: gate-d', 'Default command: gate-d', 'Default command gate-d']),
        'devtool wrapper describe default command mismatch',
        failures,
    )
    for command in ['gate-d', 'fast', 'unit', 'go-fast']:
        require(command in describe_blob, f'devtool wrapper describe missing command: {command}', failures)

    jobs_text, jobs_json = run_capture([devtool, '-r', str(ROOT), 'jobs', '--target', 'dustui_android', '--json'])
    jobs_blob = jobs_text + '\n' + (flatten_json(jobs_json) if jobs_json is not None else '')
    for job in ['retirement_audit', 'retirement_gate', 'ov13_contract', 'gradle_contract', 'android_unit']:
        require(
            contains_any(jobs_blob, [job, f'job:{job}', f'local={job}', f'target:dustui_android.{job}']),
            f'devtool jobs missing dustui_android target job: {job}',
            failures,
        )

    plan_text, plan_json = run_capture([devtool, '-r', str(ROOT), 'plan', 'gate_d', '--target', 'dustui_android', '--json'])
    plan_blob = plan_text + '\n' + (flatten_json(plan_json) if plan_json is not None else '')
    require(
        contains_any(plan_blob, ['target-local jobs', 'target_local_job_count', 'target-local job', 'target_local_jobs']),
        'Gate D plan must expose target-local jobs',
        failures,
    )
    for job in ['retirement_gate', 'ov13_contract', 'android_unit']:
        require(
            contains_any(plan_blob, [f'job:{job}', f'job-{job}', f'target:dustui_android.{job}', job]),
            f'Gate D plan missing job:{job}',
            failures,
        )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--static-only', action='store_true', help='skip devtool CLI dynamic wrapper checks')
    args = parser.parse_args()
    failures = static_validate()
    if not args.static_only:
        try:
            failures.extend(dynamic_validate())
        except Exception as exc:
            failures.append(f'dynamic Devtool wrapper validation failed: {exc}')
    if failures:
        for failure in failures:
            print(f'DustUI wrapper/EXO validation failure: {failure}', file=sys.stderr)
        return 1
    print('DustUI Devtool wrapper + EXO target-local jobs contract passed')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
