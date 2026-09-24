#!/usr/bin/env python3
from __future__ import annotations
import json, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''
fail=[]
def require(ok,msg):
    if not ok: fail.append(msg)
root=read('android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt')
launcher=read('android/app/src/main/kotlin/com/mikeyphw/dustui/root/GoRootWorker.kt')
client=read('android/app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt')
models=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/CleanupModels.kt')
aidl=read('android/app/src/main/aidl/com/mikeyphw/dustui/root/IDustRootService.aidl')
gradle=read('android/app/build.gradle.kts')
main=read('go/cmd/dustui-go/main.go')
worker=read('go/internal/rootworker/cleanup.go')
scanner=read('go/internal/rootworker/scan.go')
cleanup=read('go/internal/cleanup/cleanup.go')
tests=read('go/internal/rootworker/cleanup_test.go')
for token in ['root-worker-version','root-scan','root-cleanup-review','root-cleanup-execute','root-cleanup-reconcile']:
    require(token in main,f'Go executable missing command {token}')
for token in ['GoRootWorker.runScan','GoRootWorker.reviewCleanup','GoRootWorker.executeCleanup','claimCleanupExecutionPlan','cleanupJournalFile']:
    require(token in root,f'root service missing Go cutover token {token}')
require('NativeDustScanner' not in root,'root service still references NativeDustScanner')
require(not (ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui/root/NativeDustScanner.kt').exists(),'NativeDustScanner bridge still exists')
require(not (ROOT/'android/app/src/main/cpp/dustui_scanner.cpp').exists(),'C++ production scanner still exists')
require(not (ROOT/'android/app/src/main/cpp/CMakeLists.txt').exists(),'CMake production scanner wiring still exists')
require('externalNativeBuild' not in gradle and 'dustui_scanner' not in gradle,'Android build still wires C++ scanner')
for token in ['syscall.Openat','syscall.Unlinkat','SYS_UNLINKAT','O_NOFOLLOW','unreviewed_descendant','mount_boundary_changed','RetainedCopyConstraint','ValidateAgainst','writeJournal','claimExecution','O_EXCL','validateRetainedCopiesAtExecution','executionClaimPath']:
    require(token in worker,f'Go cleanup worker missing safety contract {token}')
for token in ['context_cancelled_during_mutation','MutationInterrupted','post_mutation_verification_started','capability_single_use_claimed']:
    require(token in cleanup,f'Go mutation protocol missing {token}')
for token in ['bufio.NewWriterSize','ROOT_OPEN_FAILED','sameFilesystem','cancelled']:
    require(token in scanner,f'Go root scanner missing {token}')
require('String reconcileCleanup(String operationId);' in aidl,'AIDL reconciliation method missing')
require('root.reconcileCleanup(operationId)' in client,'client does not reconcile lost cleanup response')
for token in ['goManifestJson','goCapabilityJson','dustui.cleanup.mutation.v1']:
    require(token in models,f'Android cleanup review missing Go authority token {token}')
for token in ['TestReplacementAfterReviewFailsClosed','TestAddedDescendantAfterReviewIsNeverDeleted','TestSelectedSymlinkIsRejected','TestExecutionReplayIsDurablyDenied','TestRetainedCopyIsRevalidatedAtExecution','TestExpiredCapabilityFailsBeforeMutation']:
    require(token in tests,f'Go rootworker regression test missing {token}')
status=json.loads(read('migration/ledger/ov14_legacy_retirement_status.json'))
blockers=sorted(status.get('blockers',[])); folded=set(status.get('folded_in',[]))
expected=sorted(['generic_release_truth_duplicated','legacy_navigation_not_proven_retired','python_legacy_target_present'])
require('rem04_privileged_cleanup_mutation_cutover_cpp_retirement' in folded,'REM-04 not folded into truth ledger')
require(blockers==expected,f'REM-04 should leave exactly three blockers, got {status.get("blockers",[])}')
obs=status.get('observed',{})
require(obs.get('cpp_scanner_runtime')=='none','C++ scanner not retired in observed truth')
require(obs.get('legacy_destructive_executor')=='none','legacy destructive executor not retired in observed truth')
cfg=tomllib.loads(read('.devtool.toml')); android=cfg.get('targets',{}).get('dustui_android',{}); jobs=android.get('jobs',{}); flows=android.get('workflows',{})
require('rem04_privileged_cutover' in jobs,'REM-04 EXO job missing')
for name in ['fast','gate_d','validate']:
    require('job:rem04_privileged_cutover' in flows.get(name,[]),f'{name} missing REM-04 validator')
if fail:
    for x in fail: print('REM-04 failure:',x,file=sys.stderr)
    raise SystemExit(1)
print('REM-04 privileged Go scan/mutation cutover PASS (C++ scanner and legacy destructive executor retired; Gate D remains open with three blockers)')
