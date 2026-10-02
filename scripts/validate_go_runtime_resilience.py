#!/usr/bin/env python3
"""Targeted contract for DustUI Android <-> Go runtime resilience.

This deliberately validates the production paths, not merely the existence of
models: Android transport classification/recovery, mutation replay blocking,
durable diagnostic handoff after restart, and Go request-loop fault isolation.
"""
from __future__ import annotations

from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise SystemExit(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8", errors="replace")


failures: list[str] = []
process = read("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
failure = read("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeFailure.kt")
store = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoDomainStore.kt")
android_test = read("android/app/src/test/kotlin/com/mikeyphw/dustui/go/GoRuntimeRecoveryPolicyTest.kt")
main_go = read("go/cmd/dustui-go/main.go")
server_go = read("go/internal/runtimeio/server.go")
server_test = read("go/internal/runtimeio/server_test.go")

for token in (
    "BoundedStderrCapture",
    "process.errorStream",
    "GoRuntimeTransportException.Code.EOF",
    "GoRuntimeTransportException.Code.PROTOCOL",
    "stderrCapture.snapshot(waitForDrain = !alive)",
    "requestType = request?.type",
    "requestId = request?.requestId",
    "sequence = request?.sequence",
    "generation = request?.generation",
    "fun diagnosticStderrTail()",
    'GoRuntimeRequestException.from(request.type, "request_exceeds_bounded_stream")',
):
    if token not in process:
        failures.append(f"Android runtime process missing {token}")

for forbidden in (
    'error("go_runtime_eof_requires_reconcile")',
    'check(process.isAlive) { "go_runtime_dead_requires_reconcile" }',
    ".redirectError(ProcessBuilder.Redirect.INHERIT)",
):
    if forbidden in process:
        failures.append(f"legacy opaque runtime failure path remains: {forbidden}")

for token in (
    "GoRuntimeRecoveryPolicy.mayReplay(type)",
    "invalidateRuntime(firstRuntime)",
    "GoRuntimeReconciliationRequiredException",
    "runtimeRequest(replayRuntime",
    "recordRuntimeIncidentBestEffort",
    '"domain.diagnostics.record"',
    '"go_runtime_transport_failure"',
    '"runtime_restarted_mutation_replay_blocked"',
    "decodeResponseWithIncident",
    "recordRuntimeRequestFailureBestEffort",
    '"go_runtime_request_failure"',
):
    if token not in store:
        failures.append(f"Android domain recovery path missing {token}")

if "GoRuntimeRequestException.from(type, code)" not in store:
    failures.append("runtime-internal response errors can still leak raw codes to the UI")

for token in (
    '"domain.scan.latest"',
    '"domain.authority.query"',
    '"domain.product.insights"',
    '"domain.cleanup.operations.list"',
    '"domain.diagnostics.snapshot"',
):
    if token not in failure:
        failures.append(f"read-only replay allowlist missing {token}")

for mutation in (
    '"domain.scan.session.upsert"',
    '"domain.cleanup.operation.claim"',
    '"domain.automation.advance"',
    '"domain.diagnostics.record"',
    '"domain.support.bundle"',
):
    # Mutating type strings may appear in tests/store code, but must never be
    # in GoRuntimeFailure.kt's production replay-safe allowlist.
    if mutation in failure:
        failures.append(f"mutation accidentally listed as replay-safe: {mutation}")

for token in (
    "internalRuntimeSentinelsNeverBecomeUserFacingMessages",
    "mutationsFailClosedInsteadOfBlindReplay",
    "observationalStorageRequestsMayReplayAfterRuntimeDeath",
    "internalServerErrorsAreMappedToUserSafeMessages",
):
    if token not in android_test:
        failures.append(f"Android unit regression coverage missing {token}")

for token in (
    '"github.com/mikeyphw/dustui/go/internal/runtimeio"',
    "runtimeio.ServeFrames(",
    "MaxFrameBytes: runtimecontract.MaxFrameBytes",
):
    if token not in main_go:
        failures.append(f"production Go serve path is not wired through resilient runtime I/O: {token}")

for forbidden in (
    "bufio.NewScanner(os.Stdin)",
    "return fmt.Errorf(\"decode frame:",
    "return fmt.Errorf(\"unsupported session protocol",
):
    if forbidden in main_go:
        failures.append(f"old process-fatal serve behavior remains in production main: {forbidden}")

for token in (
    "func ServeFrames(",
    "func readBoundedLine(",
    "runtime_handler_panic",
    "runtime_internal_error",
    "request_exceeds_bounded_stream",
    "response_exceeds_bounded_stream",
    "runtime_response_encode_failed",
    "recover()",
    "drainLine(reader)",
    "tailBounded(detail, maxDiagnosticDetail)",
    "tailBounded(stack, maxDiagnosticStack)",
):
    if token not in server_go:
        failures.append(f"Go resilient request loop missing {token}")

for token in (
    "TestRuntimeServeMalformedFrameDoesNotKillSession",
    "TestRuntimeServeHandlerPanicIsRequestScoped",
    "TestRuntimeServeDomainErrorAndOversizedResponseAreNonFatal",
    "TestRuntimeServeOversizedRequestIsDrainedAndNextRequestRuns",
    "TestRuntimeServeProtocolAndIdentityFailuresStayRequestScoped",
    "TestRuntimeServeResponseEncodePanicFallsBackAndContinues",
    "TestRuntimeServePanicDiagnosticsAreBoundedAndCorrelated",
):
    if token not in server_test:
        failures.append(f"Go runtime I/O regression coverage missing {token}")

config = tomllib.loads(read(".devtool.toml"))
android = config.get("targets", {}).get("dustui_android", {})
go = config.get("targets", {}).get("dustui_go", {})
android_job = android.get("jobs", {}).get("go_runtime_resilience_contract", {})
android_workflow = android.get("workflows", {}).get("go_runtime_resilience")
go_job = go.get("jobs", {}).get("runtime_resilience", {})
go_workflow = go.get("workflows", {}).get("runtime_resilience")
wrapper_command = config.get("wrapper", {}).get("commands", {}).get("go-runtime-resilience", {})

if android_job.get("command") != ["python3", "scripts/validate_go_runtime_resilience.py"]:
    failures.append("dustui_android targeted resilience contract job is missing or drifted")
if not android_workflow:
    failures.append("dustui_android go_runtime_resilience workflow is missing")
if "go test ./internal/runtimeio -count=1" not in " ".join(go_job.get("command", [])):
    failures.append("dustui_go runtime_resilience job does not execute the isolated Go runtime I/O tests")
if go_workflow != ["job:runtime_resilience"]:
    failures.append("dustui_go runtime_resilience workflow is missing or drifted")
if wrapper_command.get("workflow") != "go_runtime_resilience" or wrapper_command.get("target") != "dustui_android":
    failures.append("go-runtime-resilience wrapper command is missing or drifted")

if failures:
    for item in failures:
        print(f"Go runtime resilience failure: {item}", file=sys.stderr)
    raise SystemExit(1)

print("DustUI Go runtime resilience contract PASS")
