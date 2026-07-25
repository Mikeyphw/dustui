#!/usr/bin/env python3
"""Generate DustUI cleaning-intelligence qualification evidence."""
from __future__ import annotations
import argparse, json, re, sys, time, xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

@dataclass(frozen=True)
class TestCase:
    class_name: str
    name: str
    failed: bool
    skipped: bool
    seconds: float

def args():
    p=argparse.ArgumentParser(); p.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[1]); p.add_argument("--output",type=Path); p.add_argument("--allow-missing-build-evidence",action="store_true"); return p.parse_args()
def read(path: Path) -> str:
    if not path.is_file(): raise RuntimeError(f"Missing required file: {path}")
    return path.read_text(encoding="utf-8")
def parse_tests(root: Path) -> list[TestCase]:
    out=[]
    for xml_path in sorted((root/"app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        suite=ET.parse(xml_path).getroot()
        for n in suite.findall("testcase"):
            out.append(TestCase(n.attrib.get("classname",""),n.attrib.get("name",""),n.find("failure") is not None or n.find("error") is not None,n.find("skipped") is not None,float(n.attrib.get("time","0") or 0)))
    return out
def parse_lint(root: Path):
    p=root/"app/build/reports/lint-results-debug.sarif"
    if not p.is_file(): return -1,-1
    sarif=json.loads(p.read_text()); errors=warnings=0
    for run in sarif.get("runs",[]):
        for result in run.get("results",[]):
            level=str(result.get("level","warning")).lower(); errors += level=="error"; warnings += level=="warning"
    return errors,warnings
def class_passed(cases: Iterable[TestCase], suffix: str, minimum: int=1):
    found=[c for c in cases if c.class_name.endswith(suffix)]
    return len(found)>=minimum and all(not c.failed and not c.skipped for c in found)
def source_audit(root: Path):
    main=root/"app/src/main/kotlin/com/mikeyphw/dustui"; ui=main/"ui"; data=main/"data"; service=main/"root"
    active=[*ui.rglob("*.kt"),*service.rglob("*.kt"),data/"ScanRepository.kt",data/"CleanupVerification.kt"]
    patterns=(".dustui-quarantine","restoreCleanup(","insertQuarantine","QuarantineScreen","Move to quarantine")
    quarantine=[]
    for path in active:
        if path.is_file():
            text=path.read_text()
            quarantine += [f"{path.relative_to(root)}:{pattern}" for pattern in patterns if pattern in text]
    surfaces={
        "overview_and_workbenches":ui/"DustUiApp.kt",
        "recommendations":ui/"CleanupRecommendationsScreen.kt",
        "rules":ui/"CleaningIntelligenceScreens.kt",
        "apps":ui/"AppStorageInspectorScreen.kt",
        "treemap":ui/"Treemap.kt",
    }
    failures=[]
    for name,path in surfaces.items():
        text=read(path)
        required=("onOpen(entry)","Role.Button") if name=="treemap" else (("Plan deletion","Explorer","Show exact paths") if name=="rules" else ("Details","Explorer","Select","Plan deletion"))
        failures += [f"{name}:{marker}" for marker in required if marker not in text]
        if "executeCleanup(" in text: failures.append(f"{name}:direct executeCleanup")
    charts=read(ui/"StorageCharts.kt"); treemap=read(ui/"Treemap.kt"); screens=read(ui/"CleaningIntelligenceScreens.kt")
    accessibility=all(("heightIn(min = 48.dp)" in charts,"role = Role.Button" in charts,"selected = selectedLabel == slice.label" in charts,"clickable(role = Role.Button)" in treemap,"Accessible timeline points" in screens,"contentDescription = \"Storage timeline." in screens))
    db=read(data/"DustDatabase.kt"); container=read(root/"app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt")
    m=re.search(r"version\s*=\s*(\d+)",db); version=int(m.group(1)) if m else -1; schema=root/f"app/schemas/com.mikeyphw.dustui.data.DustDatabase/{version}.json"
    schema_ok=schema.is_file() and "addMigrations(MIGRATION_1_2)" in container and "fallbackToDestructiveMigration" not in container
    if schema.is_file(): schema_ok = schema_ok and json.loads(schema.read_text()).get("database",{}).get("version")==version
    return {"database":schema_ok,"version":version,"quarantine":quarantine,"surfaces":failures,"accessibility":accessibility}
def main():
    a=args(); root=a.root.resolve(); output=(a.output or root/"build/cleaning-intelligence").resolve(); output.mkdir(parents=True,exist_ok=True)
    cases=parse_tests(root); lint_errors,lint_warnings=parse_lint(root)
    if not cases and not a.allow_missing_build_evidence: raise RuntimeError("Unit-test XML evidence is missing")
    if lint_errors<0 and not a.allow_missing_build_evidence: raise RuntimeError("Lint SARIF evidence is missing")
    source=source_audit(root); passed=sum(not c.failed and not c.skipped for c in cases); failed=sum(c.failed for c in cases)
    perf=[c for c in cases if c.class_name.endswith("CleaningPerformanceContractTest")]; perf_ms=int(sum(c.seconds for c in perf)*1000)
    checks={
        "tests":passed>0 and failed==0,"lint":lint_errors==0 and lint_warnings==0,
        "databaseSchemaPassed":bool(source["database"]) and class_passed(cases,"DatabaseSchemaContractTest",3),
        "protectedPathSuitePassed":class_passed(cases,"CleanupSafetyPolicyTest",5) and class_passed(cases,"CleaningIntelligenceRegressionTest",5),
        "fingerprintSuitePassed":class_passed(cases,"CleanupSafetyPolicyTest",5) and class_passed(cases,"ReleaseGatePolicyTest",2),
        "duplicateHardLinkSuitePassed":class_passed(cases,"DuplicateVerificationTest",2) and class_passed(cases,"DuplicateWorkbenchTest",1),
        "residueOwnershipSuitePassed":class_passed(cases,"AppResidueTest",8) and class_passed(cases,"AppStorageInspectorTest",4),
        "cleanupRuleSuitePassed":class_passed(cases,"CleanupRulesTest",7),"historicalPathSuitePassed":class_passed(cases,"StorageGrowthTest",5),
        "reportRedactionSuitePassed":class_passed(cases,"CleanupVerificationTest",9),"interruptedOperationSuitePassed":class_passed(cases,"OperationRecoveryTest",2) and class_passed(cases,"CleaningRecoverySourceContractTest",3),
        "accessibilitySuitePassed":bool(source["accessibility"]) and class_passed(cases,"CleaningAccessibilityContractTest",4),
        "largeDatasetSuitePassed":class_passed(cases,"CleaningPerformanceContractTest",1) and perf_ms<=15000,
        "surfacedPathsActionable":not source["surfaces"] and class_passed(cases,"UiSourceContractTest",10),
        "noQuarantineRuntime":not source["quarantine"],
    }
    ready=all(checks.values())
    evidence={"schemaVersion":1,"generatedAtEpochMillis":int(time.time()*1000),"ready":ready,"testsPassed":passed,"testsFailed":failed,"lintErrors":max(lint_errors,0),"lintWarnings":max(lint_warnings,0),"sampleDatasetEntries":10000,"sampleDurationMillis":perf_ms,"roomSchemaVersion":source["version"],"quarantineRuntimeReferences":len(source["quarantine"]),"checks":checks,"diagnostics":{"quarantineHits":source["quarantine"],"surfaceFailures":source["surfaces"]}}
    (output/"qualification.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
    lines=["# DustUI cleaning intelligence qualification","",f"Decision: {'PASS' if ready else 'FAIL'}","",f"- Tests: {passed} passed, {failed} failed",f"- Lint: {max(lint_errors,0)} errors, {max(lint_warnings,0)} warnings",f"- Large dataset: 10,000 entries in {perf_ms} ms",f"- Room schema: {source['version']}",f"- Active quarantine references: {len(source['quarantine'])}","","## Checks",""]+[f"- [{'x' if ok else ' '}] {name}" for name,ok in checks.items()]
    (output/"QUALIFICATION.md").write_text("\n".join(lines)+"\n")
    print(f"DustUI cleaning intelligence qualification: {'PASS' if ready else 'FAIL'}")
    return 0 if ready else 1
if __name__=="__main__":
    try: raise SystemExit(main())
    except Exception as exc:
        print(f"Cleaning intelligence audit failed: {exc}",file=sys.stderr); raise SystemExit(1)
