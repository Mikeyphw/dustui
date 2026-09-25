# DustUI Full-Audit Remediation Roadmap

**Created:** 2026-09-24  
**Roadmap series:** AUD-FIX-01 → AUD-FIX-17 (Full-Audit Remediation)  
**Baseline:** `dustui-20260924-194542-664219.tar.gz` — SHA-256 `a0a9372c0065ecacf3c16c4db3a13df18bd043c972180c68d98dd29de149474b`  
**Handoff:** `dustui_master_audit_roadmap_updated_through_ov14_20260924_WITH_FULL_AUDIT_WIP.zip` — SHA-256 `38edda255161b3adbfc35668753e0da83e96cfec15760dc493d42329c3320186`  
**Input audit:** 33 converged root gap/open-evidence families.  
**Mode of this artifact:** planning only; it does not apply fixes.

## 1. Authority and completion rule

This roadmap preserves the authority order used by the converged audit:

1. `DUSTUI_AUTHORITATIVE_MERGED_AUDIT_GO_UIUX_RECHECKED.md` — product, architecture and UI/UX authority.
2. Original `DUSTUI_GO_REBUILD_ROADMAP_RECHECKED_EXO.md` — promises remain binding unless explicitly superseded.
3. `DUSTUI_GO_REBUILD_ROADMAP_ANDROID_SUSPENDED.md` — overrides only the requirements it intentionally changes; Android suspension ended after OV12.
4. Fresh supplied snapshot — source truth for this remediation campaign.
5. Reconstructed post-REM05 WIP — donor/reference only, never automatically accepted as applied or qualified.

The campaign is complete only when **all 33 root families are closed**, Gate D passes on real evidence, Gate E passes on a qualifying release/device environment, and a final independent audit pass finds no new root family. A source-level PASS or token-based retirement checker is never sufficient by itself.

## 2. Campaign execution policy

- Use a new overlay namespace, **AUD-FIX**, so these fixes cannot be confused with original OV01–OV15 or reconstructed REM02–REM05 artifacts.
- Non-gate overlays **AUD-FIX-01..05, AUD-FIX-07..14, and AUD-FIX-16** are applied with `--no-validate`. They still run narrow overlay-owned tests/checkers, but not the full Devtool validation plan.
- Full Devtool validation occurs only at **AUD-FIX-06**, **AUD-FIX-15 (Gate D)**, and **AUD-FIX-17 (Gate E)**.
- Keep Android buildability mandatory throughout this remediation campaign. The historical OV03–OV12 suspension does not extend into AUD-FIX.
- Every overlay must update the machine-readable requirement ledger and run a regression audit against all previously closed requirements. A new independent root gap blocks progression until it is entered into the ledger and assigned to an overlay.
- Validators must prove behavior, reachability, evidence and runtime authority. Do not use exact predecessor symbols or narrow source-token absence as proof of retirement.
- Gate state is **derived from executed evidence**. No overlay may hand-edit a `closed`, `qualified`, or `ready` flag to make a gate pass.
- Source/token guards and predecessor validators are **retired or successor-proofed in the same overlay that installs their behavioral replacement**. AUD-FIX-16 is only the final leftovers sweep; it must not be the first time stale guards are addressed.
- Product/config identity is evidence-derived after every overlay. Metadata may describe an unqualified capability only as candidate/staged; it may not advertise product-default/qualified state ahead of executed evidence.
- From AUD-FIX-01 onward, new generic build/install/device/APK-discovery/release logic is forbidden in the app repository; Devtool owns those generic operations. Existing duplicates may remain quarantined until the post-Gate-D physical-retirement overlay, but Gate D may not invoke or depend on them.

### Suggested Devtool application pattern

```bash
REPO="$HOME/Code/dustui"
# Ordinary AUD-FIX overlay:
devtool -r "$REPO" --target dustui_android apply /path/to/AUD-FIX-overlay.zip --no-validate

# Gate overlays run their declared workflows/validation instead of --no-validate.
# If invoking Gradle tasks directly through Devtool, pass every Gradle task as its own --task flag.
```

## 3. Non-negotiable end-state invariants

- One Go-owned domain truth: Android/Compose renders state; it does not recompute recommendation, eligibility, duplicate verification, evidence validity, or destructive-action policy.
- One scanner/size/hardlink authority and one duplicate-verification authority, both in Go; privileged code delegates rather than forks semantics.
- Bounded queries remain bounded end-to-end; routine Android screens never drain a complete scan into Compose/ViewModel state.
- Operations are durable truth. Activity, WorkManager, Tasker, notifications and recovery all reference the same Go operation IDs and terminal states.
- Every risky/evidence-dependent action uses a typed Go gate: `allowed`, `reasonCode`, `message`, `remediationActions`.
- Every content route uses `Loading | Empty | Blocked | Error | Content`; unsupported/partial evidence never silently becomes zero/empty content.
- Every async result is bound to route/entity/generation/request identity and optional query/draft fingerprint.
- Phone/tablet may change chrome and density, never semantic route identity.
- Cleanup always follows discover → inspect → select → immutable manifest → review → acknowledge when required → single-use capability → execute → verify.
- Activity is operational truth; Diagnostics is explanatory evidence. Neither substitutes for the other.
- Python/Textual, Room/NDJSON, legacy 16-destination navigation, old Debug/Certification/Deletion Report top-level product semantics, stale predecessor validators, and generic raw adb/release authority are physically retired once their successors are proven.
- Gate D and Gate E close only from complete, same-tree, replayable execution evidence.

## 4. Overlay sequence

| Overlay | Purpose | Primary gap families | Full validate? |
|---|---|---|---|
| **AUD-FIX-01** | Canonical truth, requirement index, and successor-aware audit machinery | AUD-001, AUD-002, AUD-017, AUD-021, AUD-022, AUD-023, UIX-009 | No |
| **AUD-FIX-02** | Bounded data, scanner convergence, and duplicate verification authority | AUD-007, AUD-009, AUD-010 | No |
| **AUD-FIX-03** | Go domain runtime exposure, persistence parity, and Android authority cutover | AUD-006, AUD-008, AUD-014 | No |
| **AUD-FIX-04** | Durable operation coordinator, Activity truth, and cancellation capability | AUD-011, AUD-012 | No |
| **AUD-FIX-05** | Go diagnostics, bounded retention, problem graph, and sealed support evidence | AUD-013 | No |
| **AUD-FIX-06** | Authority Convergence Gate R1 | AUD-006–AUD-013, AUD-021 | **YES** |
| **AUD-FIX-07** | Go CLI/TUI parity and production host cutover; isolate Python oracle | AUD-003, AUD-004, AUD-017 | No |
| **AUD-FIX-08** | Stable semantic route tree, deep links, and System decomposition | AUD-005, UIX-001, UIX-009 | No |
| **AUD-FIX-09** | Route-bound async protocol, shared screen states, and typed action gates | UIX-002, UIX-003, UIX-004, UIX-009 | No |
| **AUD-FIX-10** | Scan, Overview, and Apps UX truth convergence | UIX-005, UIX-006, UIX-007, UIX-009 | No |
| **AUD-FIX-11** | Activity and Diagnostics product UX convergence | AUD-011, AUD-013, UIX-001, UIX-009 | No |
| **AUD-FIX-12** | Adaptive tablet/master-detail + authoritative UX acceptance matrix | UIX-008, UIX-009 | No |
| **AUD-FIX-13** | Pre-Gate-D acceptance convergence, evidence-derived metadata, and validator lifecycle closure | AUD-016, AUD-017, AUD-021, AUD-023, UIX-009 | No |
| **AUD-FIX-14** | Historical Gate A/B/C evidence recovery and complete Gate-D topology | AUD-018, AUD-019, AUD-021, AUD-023 | No |
| **AUD-FIX-15** | Gate D requalification and Android/Go semantic cutover seal | AUD-001, AUD-003, AUD-018, AUD-020, AUD-022 | **YES** |
| **AUD-FIX-16** | Original-OV14 physical retirement and final predecessor sweep | AUD-003, AUD-014, AUD-015, AUD-016, AUD-017, AUD-023, UIX-009 | No |
| **AUD-FIX-17** | Gate E final qualification, adversarial release proof, and convergence seal | AUD-024 + regression of all prior families | **YES** |

## 5. Detailed overlay contracts

### AUD-FIX-01 — Canonical truth, requirement index, and successor-aware audit machinery

**Depends on:** fresh audited baseline  
**Closes/advances:** AUD-001, AUD-002, AUD-017, AUD-021, AUD-022, AUD-023, UIX-009  
**Goal:** Make the repository incapable of simultaneously claiming incompatible product/gate/UI states, and make every later overlay auditable against one requirement index.

**Implementation scope**
- Create one canonical qualification-state model generated from executed evidence; `.devtool.toml`, product contracts, ledgers, release docs, and validators consume it rather than independently declaring Gate D/OV15 state.
- Convert the 33-family audit ledger plus original-roadmap and UI/UX requirements into a machine-readable requirement index with stable IDs, ownership, supersession rules, evidence kind, and closure criteria.
- Rewrite predecessor-only validators to be successor-aware: test invariants/reachability rather than exact old symbols, tokens, operation names, shell classes, or workflow list equality.
- Upgrade full-roadmap conformance checks so they detect gate-state contradictions, missing evidence topology, raw adb/build/install duplication, stale predecessor UI authority, route-tree omissions, shared-state/gate non-use, and historical EXO dependencies.
- Add negative fixtures that deliberately create contradictory gate truth, stale predecessor routes, fake retirement, and missing evidence nodes; the checker must fail each fixture.
- Keep Gate D explicitly OPEN throughout this overlay.

**Exit criteria**
- Exactly one canonical source computes Gate-D/OV15 qualification state.
- All live validators agree that current Gate D is open before qualification.
- Checker catches every known false-positive class from AUD-001/002/021/022/023 and UIX-009.
- No code path can mark Gate D closed merely from source-token absence.

**Evidence/checks**
- Targeted Python/unit tests for requirement-index and validator fixtures
- Run all audit/retirement validators against the same tree and require non-contradictory results
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-02 — Bounded data, scanner convergence, and duplicate verification authority

**Depends on:** AUD-FIX-01  
**Closes/advances:** AUD-007, AUD-009, AUD-010  
**Goal:** Restore the original bounded-query/scanner/verification contract before more Android product code is migrated.

**Implementation scope**
- Make privileged/root scanning reuse the single Go scan authority instead of maintaining a second traversal/size/hardlink implementation.
- Move exact duplicate edge/full hashing and verification into the Go privileged/runtime boundary; Kotlin becomes presentation/transport only.
- Redesign Android query adapters so routine screens never drain every page into `allEntries`; use bounded paging/aggregation contracts and server-side query/analysis operations.
- Add hard contract tests for page bounds, cancellation, large scans, hardlinks, logical/allocated semantics, duplicate verification, and absence of Kotlin verification authority.
- Remove or quarantine list-wide Android analyzers that defeat the Go query engine.

**Exit criteria**
- One Go scanner authority is used by normal and privileged scan paths.
- No production Kotlin duplicate hash/verification authority remains.
- No normal UI/domain flow materializes the complete scan merely to filter/sort/analyze it.
- The previously failing bounded cleaning performance contract passes as a targeted check.

**Evidence/checks**
- Go scanner/query/duplicate tests
- Android adapter unit tests for bounded paging
- Static authority/reachability audit
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-03 — Go domain runtime exposure, persistence parity, and Android authority cutover

**Depends on:** AUD-FIX-02  
**Closes/advances:** AUD-006, AUD-008  
**Goal:** Make the implemented Go package/storage/intelligence/rules/growth/recommendation/verification domains actual runtime product authorities.

**Implementation scope**
- Expose typed runtime operations for package/storage evidence, residue, cleanup intelligence, recommendations, rules, growth/trends, module evidence, duplicate/residue projection, and verification.
- Give every operation versioned request/response schemas, generation/freshness metadata, explicit unsupported/partial states, and bounded query semantics.
- Wire Android repositories/ViewModels to those runtime operations and delete production-reachable Kotlin policy/analyzer engines as each operation reaches parity.
- Keep Compose limited to presentation transformations; no eligibility/recommendation/evidence truth may be recomputed from incidental local state.
- Add cross-language golden contract tests and malformed/old-schema compatibility tests.
- Prove Go persistence/restart parity for migrated authorities: state survives process restart, generations/freshness remain monotonic/truthful, migrations preserve supported data, and Android reconnects without rebuilding authority from Kotlin/Room/NDJSON.

**Exit criteria**
- Every Go domain claimed as production authority is reachable through the Android runtime protocol.
- Android consumes Go results for all migrated domains.
- No duplicate Kotlin domain-truth engine remains production-reachable for the covered areas.
- Unsupported/partial evidence remains explicit end-to-end.
- Restart/persistence fixtures prove migrated Go state is durable and sufficient for later Room/NDJSON retirement.

**Evidence/checks**
- Go protocol/domain tests
- Android runtime adapter tests
- Cross-language golden fixtures
- Static Kotlin-authority reachability audit
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-04 — Durable operation coordinator, Activity truth, and cancellation capability

**Depends on:** AUD-FIX-03  
**Closes/advances:** AUD-011, AUD-012  
**Goal:** Make scans, analyses, cleanup, scheduled/Tasker work, notifications, and Activity converge on one durable terminal Go operation truth.

**Implementation scope**
- Define/finish the durable operation lifecycle: accepted → running → terminal success/failure/cancelled/recovery-required, with persisted generation and evidence correlation.
- Make WorkManager and Tasker return/observe durable operation identity instead of treating launch/"accepted" as terminal success.
- Expose durable activity list/detail/reconcile/retry/cancel operations to Android.
- Replace notification cancellation with a Go-issued capability carrying nonce, expiry, operation binding, and single-use/replay protection.
- Add process-death/reconnect tests proving terminal truth survives UI/worker recreation.

**Exit criteria**
- WorkManager, Tasker, notifications, and Android UI all reference the same operation IDs and terminal states.
- No adapter reports success merely because a run was launched.
- Cancel/retry/reconcile are action-gated by Go and replay-safe.
- Durable run history is sufficient for the later Activity UI without process-local reconstruction.

**Evidence/checks**
- Go operation lifecycle/replay tests
- Android WorkManager/Tasker adapter tests
- Notification capability tests
- Process recreation unit/integration fixtures
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-05 — Go diagnostics, bounded retention, problem graph, and sealed support evidence authority

**Depends on:** AUD-FIX-04  
**Closes/advances:** AUD-013  
**Goal:** Move diagnostic explanation, remediation, freshness, privacy and support export to durable Go evidence before the Diagnostics UI is remodeled.

**Implementation scope**
- Expose the Go ProblemGraph/root-cause model, runtime/scan/evidence health, remediation actions, and correlation to durable operations.
- Move support-bundle authorship to a bounded Go operation that produces a sealed manifest with privacy classification, retention, hashes, and evidence generation.
- Keep raw debug/probe/trace detail available only as secondary/developer evidence, never as the primary product diagnosis.
- Define bounded diagnostic/support retention, compaction/expiry, and deletion semantics in Go; no unbounded process-local diagnostic history may be required for product truth.
- Add redaction/privacy, bounded-retention, expiry/compaction, and stale-evidence invalidation tests.

**Exit criteria**
- Android can obtain problem graph, health, remediation and support-export state from Go.
- Support export is a durable/sealed Go operation, not a Kotlin process-local assembly.
- Privacy/redaction and freshness are machine-testable contracts.

**Evidence/checks**
- Go diagnostics/support tests
- Android adapter contract tests
- Privacy/redaction fixture tests
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-06 — Authority Convergence Gate R1

**Depends on:** AUD-FIX-05  
**Closes/advances:** AUD-006, AUD-007, AUD-008, AUD-009, AUD-010, AUD-011, AUD-012, AUD-013, AUD-021  
**Goal:** First full validation point: prove the new backend/runtime authorities are coherent before host and UI cutover continue.

**Implementation scope**
- Run the upgraded requirement-index checker and authority/reachability validators.
- Run Go test/build/product/schema evidence and Android Debug unit/lint/assemble plus migration contract.
- Run bounded-query, duplicate, operation lifecycle, diagnostics/privacy, and cross-language contract suites.
- Capture canonical EXO/Devtool execution evidence for this remediation gate; do not change Gate-D truth.

**Exit criteria**
- All AUD-FIX-02..05 authority contracts pass in one clean tree.
- No known Kotlin/domain/scanner/duplicate/operation/diagnostic authority regression remains.
- Gate R1 evidence is sealed/replayable and linked to exact tree/artifact identity.
- Gate D remains OPEN.

**Evidence/checks**
- FULL DEVTOOL VALIDATION #1
- Validation tasks must be passed as separate `--task` flags where tasks are used
- Canonical log/artifact hashes recorded

### AUD-FIX-07 — Go CLI/TUI parity and production host cutover

**Depends on:** AUD-FIX-06  
**Closes/advances:** AUD-003, AUD-004, AUD-017  
**Goal:** Only after Go runtime authority is real, make Go the supported/default host authority and isolate the Python/Textual parity oracle from production; physical deletion remains an original-OV14 task after Gate D.

**Implementation scope**
- Implement real Go CLI commands for query/search, activity, cleanup review, cleanup execute, diagnostics, support export, readiness/schema/product inspection, and documented scan workflows.
- Implement an interactive Go TUI run loop using the same domain/runtime contracts rather than Render/Smoke scaffolding.
- Add CLI JSON/machine-readable parity and TUI smoke/interaction tests.
- Cut supported host entry points/default-run declarations over to Go and move any Python/Textual parity oracle into an explicitly **non-production, non-default, non-packaged** migration/test-only location. Do not physically delete the final oracle solely to make this overlay look complete; AUD-FIX-16 performs the original-OV14 physical retirement after Gate D proof.
- Update `.devtool.toml` identity from executed/reachable evidence so repository declarations match the actual product-default host while Gate D remains open.
- Retire or successor-proof the source/token tests and host validators invalidated by this cutover in this same overlay.

**Exit criteria**
- Product metadata claims only capabilities that real Go CLI/TUI commands implement.
- Go CLI/TUI cover the promised workflows using the same authorities as Android.
- No Python/Textual **production/default** host or entry point remains; any surviving oracle is isolated as non-production pending AUD-FIX-16 physical retirement.
- Repository identity declarations are truthful and still mark Gate D unqualified.

**Evidence/checks**
- Go CLI command tests and JSON contracts
- TUI interaction/smoke tests
- Static Python/Textual production reachability audit
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-08 — Stable semantic route tree, deep links, and System decomposition

**Depends on:** AUD-FIX-07  
**Closes/advances:** AUD-005, UIX-001  
**Goal:** Replace navigation veneers with the authoritative task/domain route tree and stable entity/detail routes.

**Implementation scope**
- Implement stable semantic routes for Overview (`home/composition/trends/scan-history`), Files (`explorer/search/large/treemap/item/{id}`), Clean (`recommended/duplicates/residue/rules/candidate/{id}/review/{manifestId}`), Apps (`list/package/{packageName}/paths`), Activity (`list/run/{operationId}/evidence`), and System (`runtime/modules/automation/diagnostics/certification/settings`).
- Remove the legacy 16-destination shell and any phone/tablet destination substitution; layout may change chrome only.
- Implement Android deep links/ACTION_VIEW/nav integration and Go deep-link translation for nested/entity routes with missing/stale-entity behavior.
- Decompose `SystemHome` so System is not the old Tools catch-all; keep canonical ownership and contextual links.
- Persist/restores semantic route + entity identity across recreation.

**Exit criteria**
- Every authoritative route has a stable ID and canonical parent domain.
- Compact/wide layouts preserve route meaning.
- Entity/deep-link routes resolve or fail with typed stale/not-found state, never local ad-hoc sheets as the only identity.
- Old top-level Scan/Debug/Certification/Deletion Reports semantics are retired as peer destinations.

**Evidence/checks**
- Route inventory/round-trip tests
- Deep-link translation tests
- State restoration tests
- Static no-legacy-shell/no-layout-remap checks
- Retire/version predecessor navigation source-token guards/docs invalidated by this route cutover in the same overlay.
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-09 — Route-bound async protocol, shared screen states, and typed action gates

**Depends on:** AUD-FIX-08  
**Closes/advances:** UIX-002, UIX-003, UIX-004  
**Goal:** Give every content route the same stale-response, presentation-state, and domain-gating semantics.

**Implementation scope**
- Implement the binding tuple `routeKey + routeGeneration + entityKind + entityId + requestId + optional query/draft fingerprint` through Go protocol, Android repository/ViewModel, and Compose consumption.
- Reject late responses unless all relevant identities/generations still match; remove scattered AtomicLong/local heuristics once replaced.
- Make every content route render through `Loading`, `Empty(reason + recovery)`, `Blocked(reason + remediation)`, `Error(problem + retry/support)`, or `Content(data + freshness + action gates)`.
- Implement typed Go action gates exactly as `allowed`, stable `reasonCode`, plain-language `message`, and typed `remediationActions`; eliminate duplicate Kotlin gate logic for risky/evidence-dependent actions.
- Add adversarial stale-response tests for directory/package/scan generation/cleanup review/Activity/diagnostics switching.

**Exit criteria**
- All production content routes consume the shared state contract.
- All risky/evidence-dependent actions consume Go gates.
- Late async results cannot populate a newer/different route or entity.
- Blocked is distinguishable from empty/error and exposes remediation.

**Evidence/checks**
- Protocol/generation tests
- Compose/ViewModel state transition tests
- Stale-response adversarial tests
- Static no-local-gate duplication checks
- Retire/version predecessor screen-state/action-gate guards invalidated by this protocol cutover in the same overlay.
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-10 — Scan, Overview, and Apps UX truth convergence

**Depends on:** AUD-FIX-09  
**Closes/advances:** UIX-005, UIX-006, UIX-007  
**Goal:** Close the three concrete task-flow truth gaps exposed by the UI/UX audit.

**Implementation scope**
- Rebuild scan as mandatory scope/preset → preflight → review request → explicit Start → Activity/global active operation → completion health evaluation; remove one-tap preset/custom execution bypasses.
- Make Overview show scan identity/freshness/integrity, explicit logical vs allocated semantics, active operation, recent activity, warnings/problems, and truthful best-next-actions; fix Review evidence to open Diagnostics rather than Scan.
- Carry package inventory/storage evidence completeness through the Go report and Android UI as explicit supported/partial/unavailable state; never coerce missing evidence to ordinary zero-valued buckets.
- Use typed action gates/remediation for no-scan, stale-after-mutation, wrong-scope, degraded integrity, root unavailable, and unsupported evidence states.

**Exit criteria**
- No scan starts before mandatory preflight and user review/Start.
- Overview next actions navigate to the correct semantic routes and expose byte semantics.
- Apps cannot visually confuse unavailable/partial evidence with measured zero.
- First launch remains useful without redirecting to a Scan destination.

**Evidence/checks**
- Scan flow state-machine tests
- Overview semantic navigation tests
- Apps evidence completeness tests
- No direct scan-start bypass static checks
- Update successor UX fixtures/docs for Scan/Overview/Apps in the same overlay; stale predecessor acceptance must not remain authoritative.
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-11 — Activity and Diagnostics product UX convergence

**Depends on:** AUD-FIX-10  
**Closes/advances:** AUD-011, AUD-013, UIX-001  
**Goal:** Make the two most important operational/explanatory surfaces render the durable authorities created earlier.

**Implementation scope**
- Build Activity list/run/evidence screens from Go durable operation truth: active scans/analyses/cleanup/automation, completed/failed/interrupted/recovery-required states, filters, retry/cancel/reconcile gates, verification and diagnostics correlation.
- Add the global active-operation surface (including Overview entry point) whose tap resolves to Activity run detail.
- Replace primary Debug Workbench presentation with System → Diagnostics backed by Go ProblemGraph, runtime/scan/evidence health, root causes, remediation, freshness, and support actions.
- Keep raw probes/traces/debug overlay behind progressive disclosure/advanced detail.
- Treat deletion reports as Activity run details/evidence rather than a separate top-level product.

**Exit criteria**
- Activity no longer renders cleanup/deletion reports as a substitute for operation truth.
- Run detail survives navigation/recreation and exposes recovery when required.
- Diagnostics leads with user-comprehensible root cause/remediation, not developer console content.
- Operation/evidence correlation is navigable in both directions.

**Evidence/checks**
- Activity state/recovery UI tests
- Diagnostics problem/remediation UI tests
- Navigation/correlation tests
- Replace predecessor Debug Workbench/deletion-report acceptance artifacts with Activity/Diagnostics behavioral acceptance in the same overlay.
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-12 — Adaptive tablet/master-detail and route-continuity UX

**Depends on:** AUD-FIX-11  
**Closes/advances:** UIX-008  
**Goal:** Finish the adaptive contract: denser layouts without changing route identity or task semantics.

**Implementation scope**
- Implement master/detail or supporting-pane layouts for high-density domains (Files, Clean, Apps, Activity, System where appropriate) rather than only NavigationRail + one content column.
- Preserve the same semantic route/entity across compact↔expanded window changes, rotation, and recreation.
- Keep selection/detail identity addressable through the route model rather than layout-local state.
- Add tablet/phone previews and Compose/device tests for route continuity, pane behavior, focus/traversal, and accessibility equivalents for charts/treemap.

**Exit criteria**
- Phone/tablet differ in chrome/density only, not destination meaning.
- Selected detail survives resize/rotation without substitution or stale response acceptance.
- Wide layouts provide useful master/detail density for audited domains.
- Accessibility semantics remain equivalent across layouts.

**Evidence/checks**
- Compose adaptive layout tests
- Rotation/window-size route-continuity tests
- Accessibility semantics tests
- Execute the authoritative visual/product acceptance rules here as testable assertions where possible: one dominant question/action, destructive actions only in review contexts, plain-language primary copy, distinct empty/stale/failed/blocked states, and exact textual equivalents for visual data.
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-13 — Pre-Gate-D acceptance convergence, evidence-derived metadata, and validator lifecycle closure

**Depends on:** AUD-FIX-12  
**Closes/advances:** AUD-016, AUD-017, AUD-021, AUD-023, UIX-009  
**Goal:** Ensure the candidate Gate-D tree has one successor-aware acceptance system and truthful metadata before qualification; do not perform original-OV14 physical retirement yet.

**Implementation scope**
- Re-inventory every remaining source-reading Contract/SourceContract test. Any guard whose behavioral replacement already exists must be retired or rewritten now; each mapping names the exact successor test/evidence, not a generic future gate.
- Version/archive historical validators that must describe an older tree; make live validators successor-aware around behavioral/authority contracts. No current-tree validator may require a retired implementation token.
- Rewrite current UI docs, checklists, runtime copy, and acceptance scripts so they describe the successor semantic product. Historical docs may remain only under an explicitly archived/non-authoritative namespace.
- Make `.devtool.toml`, product declarations, migration ledgers, and release docs consume evidence-derived candidate/qualified state. No product-default/qualified claim may outrun executed evidence.
- Ensure the upcoming Gate-D workflow calls Devtool-owned generic build/install/device/artifact operations directly. Existing app-local generic adb/APK/release scripts are quarantined and non-authoritative until AUD-FIX-16 physically removes them.
- Run a blind requirement-index counterexample pass specifically against the full UI/UX authority, including the visual/product acceptance rules and complete UX task matrix.

**Exit criteria**
- No live source-token test or validator contradicts the implementation it is supposed to validate.
- Every retired guard has a concrete behavioral/evidence successor; leftovers are explicitly listed for AUD-FIX-16 physical sweep only.
- Current docs/checklists/copy assert one semantic product and one acceptance model.
- Metadata is evidence-derived and Gate D remains OPEN/unqualified.
- Gate-D configuration uses Devtool generic authority and does not depend on quarantined repo-local adb/APK/release helpers.

**Evidence/checks**
- Source-token guard inventory and successor mapping
- Successor-validator compatibility suite
- UI acceptance-authority scan
- Evidence-derived metadata contradiction fixtures
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-14 — Historical Gate A/B/C evidence recovery and complete Gate-D topology

**Depends on:** AUD-FIX-13  
**Closes/advances:** AUD-018, AUD-019, AUD-021, AUD-023  
**Goal:** Close evidence-system gaps before Gate D runs; do not use Gate D to discover missing proof nodes or rewrite history.

**Implementation scope**
- Recover original Gate A/B/C commit/tree identities from repository history/ledgers. Re-run canonical EXO evidence on the exact gate tree where recoverable; Gate B must include the required ARM64 device/runtime proof.
- Record commit/tree hash, Devtool version, target/workflow, host/device architecture, terminal result, artifact/log hashes, replay metadata, and any environment constraints for each historical gate.
- If an exact historical gate tree cannot be recovered, mark it `UNRECOVERABLE_HISTORICAL_EVIDENCE`; do not silently treat current evidence as historical proof. Full original-roadmap conformance remains blocked unless the evidence is recovered or a separately authorized roadmap amendment changes that requirement.
- Build the complete **OV13/Gate-D** workflow topology: requirement checker; live successor validators; Go tests/build/product/schema/CLI/TUI; Android Debug and Release unit/lint/assemble; migration contract; install/smoke; connected route/deep-link/rotation tests; accessibility traversal; rooted runtime smoke; runtime/APK binding; Devtool artifact identity; privacy/support evidence.
- Gate D proves that predecessor authorities are **not production-reachable** and the successor is product-default. It does **not** require original-OV14 physical deletion of quarantined Python parity files, Room/NDJSON migration remnants, or quarantined legacy helper scripts; that deletion belongs to AUD-FIX-16.
- Make every node produce canonical EXO evidence and bind all Gate-D nodes to the same clean tree/build identity.
- Run topology/configuration self-tests only; do not close Gate D in this overlay.

**Exit criteria**
- Gate A/B/C historical evidence is either genuinely recovered on exact trees or explicitly remains a blocker—never fabricated/relabelled.
- Gate-D graph contains every proof node required by the original roadmap, amendment, and UI/UX authority for OV13.
- Full-roadmap checker verifies topology completeness, exact-tree binding, and distinction between production-reachability cutover (Gate D) and post-Gate-D physical retirement (OV14).
- Gate D remains OPEN.

**Evidence/checks**
- Historical evidence inventory/replay checks
- Gate-D workflow graph unit/static tests
- EXO node identity/replayability checks
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-15 — Gate D requalification and Android/Go semantic cutover seal

**Depends on:** AUD-FIX-14  
**Closes/advances:** AUD-001, AUD-003, AUD-018, AUD-020, AUD-022  
**Goal:** Second full validation point: qualify the complete OV13-equivalent product cutover on real Android/runtime evidence and only then derive Gate-D closure.

**Implementation scope**
- Run the complete AUD-FIX-14 Gate-D workflow on one clean tree through Devtool/EXO.
- Execute Debug + Release unit/lint/build, install/smoke, connected semantic route/deep-link/rotation/accessibility tests, ARM64/root runtime smoke, Go tests/CLI/TUI, support/privacy checks, production-reachability cutover checks and full requirement-index conformance.
- Bind built APK, runtime binary, Git tree, Devtool artifact, device run and logs by hashes/identity.
- Prove supported host entry points resolve to Go; any surviving Python/Textual oracle is non-production/non-default/non-packaged and cannot be reached by supported workflows.
- Require zero failing mandatory nodes and zero unmapped blocking/high pre-OV14 audit families; rerun the audit loop after the gate result.
- Only after success generate canonical Gate-D-qualified state. Record the exact **Gate-D tree hash** separately from later current-tree identity so AUD-FIX-16 can modify the tree without pretending those modifications were part of the Gate-D run.

**Exit criteria**
- Complete Gate-D graph passes in a clean, reproducible execution.
- Every root family required through OV13/Gate D is closed; original-OV14 physical-removal families may remain explicitly open for AUD-FIX-16.
- Gate-D state is evidence-derived and every consumer agrees on the qualified Gate-D tree/artifact identity.
- OV14 physical retirement becomes eligible only after this overlay succeeds.

**Evidence/checks**
- **FULL DEVTOOL VALIDATION #2 — Gate D**
- Separate Gradle tasks supplied as separate `--task` flags where invoked directly
- Gate-D audit loop must produce no new independent pre-OV14 gap family

### AUD-FIX-16 — Original-OV14 physical retirement and final predecessor sweep

**Depends on:** AUD-FIX-15  
**Closes/advances:** AUD-003, AUD-014, AUD-015, AUD-016, AUD-017, AUD-023, UIX-009  
**Goal:** Perform the physical retirement that the original roadmap places after Gate D, while preserving the qualified successor behavior and preparing the final Gate-E tree.

**Implementation scope**
- Physically remove the isolated Python/Textual parity oracle, package remnants, and obsolete host scaffolding that are no longer needed after Gate-D proof.
- Remove Room database/DAO/schema production integration, NDJSON tail-reader/migration writers, and obsolete dependencies; retained historical fixtures move to explicit archive-only locations.
- Remove quarantined generic repo-local adb/install/launch/logcat/APK-discovery/release helpers; Devtool remains the generic build/install/device/signing/artifact/release authority.
- Sweep remaining source/token Contract/SourceContract tests and predecessor validators/docs/checklists/copy. A leftover is allowed only if it is explicitly historical, non-authoritative, and cannot run as a current-tree acceptance gate.
- Recompute evidence-derived product/config identity for the **post-OV14 current tree**, preserving the distinct Gate-D qualified ancestor/tree identity.
- Run successor behavioral tests, build/lint smoke, migration/archive checks, and the full audit loop with `--no-validate`; do not manufacture a second Gate-D seal for this changed tree.

**Exit criteria**
- No production or current-acceptance Python/Textual, Room/NDJSON, legacy shell, stale predecessor validator/doc, source-token guard, or generic raw adb/release authority remains.
- Archived historical material is clearly non-authoritative and excluded from live validators/build/runtime.
- The post-retirement tree remains buildable and all successor targeted tests pass.
- Gate D remains a truthful milestone bound to its recorded AUD-FIX-15 tree; current-tree identity is distinct and ready for Gate E.

**Evidence/checks**
- Physical-file/dependency/reachability audit
- Raw adb/generic tooling scan
- Archive/non-authority checks
- Successor validator + targeted build/lint tests
- No full Devtool validate; apply with `--no-validate`

### AUD-FIX-17 — Gate E final qualification, adversarial release proof, and convergence seal

**Depends on:** AUD-FIX-16  
**Closes/advances:** AUD-024 and regression of every prior family  
**Goal:** Final qualification of the signed post-OV14 release product across destructive safety, recovery, device/runtime, UI/UX, privacy, retirement and evidence boundaries.

**Implementation scope**
- Qualify the final signed release artifact and runtime binding; include clean install and supported upgrade/migration behavior.
- Re-run the Gate-D core regression set on the final post-retirement tree in addition to Gate-E-specific adversarial tests; Gate E must prove that AUD-FIX-16 retirement did not regress the qualified product.
- Exercise rooted ARM64 runtime and non-root/unsupported degraded behavior, storage scopes/volumes available to the product, process death/recreation, long-running operation reconnect, cancellation/replay protection, cleanup verification/recovery, Tasker/WorkManager/notification terminal truth, and stale/deleted deep links.
- Run the complete authoritative UI/UX task matrix on phone and tablet/adaptive configurations, including accessibility traversal, exact chart/treemap textual equivalents, one dominant question/action, destructive-action review containment, plain-language primary copy, and distinct empty/stale/failed/blocked semantics.
- Validate diagnostics/support privacy, redaction, bounded retention/expiry, bundle sealing and evidence freshness.
- Run the full requirement-index checker plus an independent counterexample audit loop repeatedly until a fresh pass produces no new independent root-gap family.
- If a qualifying Android/root/device environment is unavailable, emit only a qualification-deferred checkpoint and keep Gate E OPEN; never manufacture a seal.

**Exit criteria**
- Final release artifact is cryptographically/structurally bound to the post-OV14 tree/runtime/evidence.
- All 33 audited root families are CLOSED with executed or structurally appropriate evidence; no family is merely hidden by retirement.
- Every original-roadmap, amendment, and authoritative UI/UX requirement is satisfied or explicitly superseded by an already-authorized amendment.
- Final audit ledger has zero open root gaps and a convergence pass with no new independent family.
- Gate E closes only from executed qualifying evidence.

**Evidence/checks**
- **FULL DEVTOOL VALIDATION #3 — Gate E**
- Gate-D core regression + adversarial/root/device/UI/accessibility/privacy/retirement qualification
- Final independent audit convergence loop

## 6. AUD-FIX-15 Gate-D mandatory evidence graph

AUD-FIX-15 may not start until AUD-FIX-14 proves that every node below exists and binds to one clean tree. The gate must include at least:

- Full requirement-index / 33-family conformance checker and retirement/reachability validators.
- Go: `go test ./...`, build, product contract, schema/protocol contracts, real CLI workflows, TUI smoke/interaction.
- Android Debug: unit tests, lint, assemble/package.
- Android Release: unit tests, lint, assemble/package.
- `verifyDustMigrationContract` and all successor authority/cutover validators.
- Install and launch smoke through Devtool-owned device authority.
- Connected instrumentation for semantic routes, nested/deep links, stale/missing entities, process recreation, rotation/window changes and route identity.
- Accessibility traversal/semantics, including chart/treemap textual equivalents.
- Rooted ARM64 runtime smoke and privileged cleanup/duplicate/cancel/recovery contracts.
- Runtime binary ↔ APK ↔ Git tree ↔ Devtool artifact identity/hash binding.
- Activity/WorkManager/Tasker/notification terminal-truth tests.
- Diagnostics/support bundle privacy, redaction, freshness and sealing tests.
- Production-reachability/cutover scans: supported workflows cannot reach Python/Textual, Room/NDJSON authority, the legacy shell, stale predecessor acceptance, or repo-local generic adb/release helpers. Quarantined migration/parity remnants may still exist physically until AUD-FIX-16.

Any mandatory node that is skipped, unavailable, stale, bound to a different tree, or fails keeps Gate D **OPEN**. Physical-removal-only OV14 checks are deliberately excluded here and become mandatory at AUD-FIX-16/AUD-FIX-17.

## 7. AUD-FIX-17 Gate-E final qualification matrix

- Signed release artifact identity and supported clean-install/upgrade path.
- Rooted ARM64 device and non-root/unsupported degraded behavior.
- Long operations across navigation, recreation/process death and reconnect.
- Cancellation nonce expiry/single-use/replay resistance.
- Destructive cleanup on disposable fixtures: exact manifest review, mutation, verification, interrupted recovery and stale-scan consequence.
- WorkManager + Tasker + notification flows to terminal durable Activity truth.
- Phone and tablet/adaptive task matrix: Overview, Files/search/large/treemap, Clean/duplicates/residue/review, Apps, Activity/recovery, System/Diagnostics/support.
- Deep links to valid, stale and deleted entities/runs/manifests.
- Accessibility traversal and exact textual equivalents for visual storage information.
- Support export privacy/redaction/bounded-retention/sealing/freshness.
- Post-Gate-D physical-retirement proof: no live Python/Textual, Room/NDJSON, generic raw adb/release helpers, predecessor source-token guards, or stale UI acceptance authorities.
- Final requirement-index audit plus blind counterexample audit loop until one whole fresh pass yields no new independent root-gap family.

If the qualifying environment is missing, AUD-FIX-17 produces only a **qualification-deferred checkpoint** and Gate E remains open.

## 8. Historical Gate A/B/C evidence rule

AUD-019 is unusual because evidence cannot be truthfully created retroactively by renaming a current run. AUD-FIX-14 therefore uses this order:

1. Locate the exact original Gate A/B/C commit/tree identities and original gate definitions from Git history and ledgers.
2. Recreate each gate in a detached worktree and run canonical/replayable evidence on that exact tree. Gate B additionally requires its ARM64 device proof.
3. Preserve the result as **requalified historical-tree evidence**, including tree hash, tool version, host/device identity class, logs and artifact hashes.
4. If an exact gate tree or required environment cannot be recovered, record `UNRECOVERABLE_HISTORICAL_EVIDENCE`. Do not call the original gate closed.
5. Because the user requested every gap fixed, unrecoverable historical evidence remains a blocker to full original-roadmap conformance unless a separate, explicit roadmap amendment is later authorized. AUD-FIX itself does not waive it.

## 9. Audit-loop protocol for every AUD-FIX overlay

After each overlay, run this loop before moving on:

1. **Requirement pass:** evaluate every requirement owned by the overlay against the authoritative UI/UX audit, original roadmap and amendment.
2. **Regression pass:** re-evaluate every gap family previously marked closed; any regression reopens its family.
3. **Counterexample pass:** deliberately search for alternate production paths, stale docs/tests, layout-specific route substitutions, local domain logic, unbounded list drains, direct destructive bypasses, and evidence-state contradictions not covered by the current checker.
4. **Guard-lifecycle pass:** any source/token test, validator, doc, checklist or acceptance script invalidated by this overlay must be rewritten, versioned/archived, or retired in this same overlay; it cannot be deferred merely because AUD-FIX-16 has a final sweep.
5. **Validator-vs-source pass:** compare what validators claim with production reachability; a validator PASS does not override contradictory source/runtime evidence.
6. **WIP-donor pass:** if code is borrowed from the reconstructed WIP, audit it as new code; never inherit its PASS/closed state.
7. **Ledger pass:** add any genuinely new independent root family to the machine ledger and assign it before proceeding.
8. Continue until a complete fresh pass yields **no new independent root family**. At AUD-FIX-15 and AUD-FIX-17 this is a gate condition, not just documentation.

## 10. Complete 33-gap coverage map — semantic recheck

The first roadmap version had all 33 IDs present, but this second pass checked **closure semantics and ordering**, not just ID membership. The revised map below has no merely-symbolic mappings.

| Gap | Impact | Area | Planned closure / proof overlay(s) | Recheck |
|---|---|---|---|---|
| **AUD-001** | blocking | Gate/ledger truth | AUD-FIX-01, AUD-FIX-15 | **FULL after recheck** |
| **AUD-002** | blocking | Audit machinery / retirement truth | AUD-FIX-01, AUD-FIX-13 | **FULL after recheck** |
| **AUD-003** | blocking | OV13/OV14 host retirement | AUD-FIX-07, AUD-FIX-15, AUD-FIX-16 | **FULL after recheck** |
| **AUD-004** | blocking | OV13 Go CLI/TUI parity | AUD-FIX-07, AUD-FIX-15 | **FULL after recheck** |
| **AUD-005** | blocking | OV13/OV14 Android navigation cutover | AUD-FIX-08, AUD-FIX-15 | **FULL after recheck** |
| **AUD-006** | blocking | OV09–OV13 domain authority | AUD-FIX-03, AUD-FIX-06, AUD-FIX-15 | **FULL after recheck** |
| **AUD-007** | high | OV04 bounded query / scale | AUD-FIX-02, AUD-FIX-06, AUD-FIX-17 | **FULL after recheck** |
| **AUD-008** | blocking | OV09–OV12 Go runtime authority exposure | AUD-FIX-03, AUD-FIX-06, AUD-FIX-15 | **FULL after recheck** |
| **AUD-009** | high | OV03/OV06 scan authority convergence | AUD-FIX-02, AUD-FIX-06 | **FULL after recheck** |
| **AUD-010** | high | OV10 duplicate verification authority | AUD-FIX-02, AUD-FIX-06 | **FULL after recheck** |
| **AUD-011** | blocking | OV11 automation and Activity ownership | AUD-FIX-04, AUD-FIX-06, AUD-FIX-11, AUD-FIX-17 | **FULL after recheck** |
| **AUD-012** | high | OV11 notification cancellation capability | AUD-FIX-04, AUD-FIX-06, AUD-FIX-17 | **FULL after recheck** |
| **AUD-013** | blocking | OV12 diagnostics / support authority | AUD-FIX-05, AUD-FIX-06, AUD-FIX-11, AUD-FIX-17 | **FULL after recheck** |
| **AUD-014** | high | OV14 Room/NDJSON physical retirement | AUD-FIX-03, AUD-FIX-16, AUD-FIX-17 | **FULL after recheck** |
| **AUD-015** | high | OV01/OV14 Devtool generic authority | AUD-FIX-13, AUD-FIX-16, AUD-FIX-17 | **FULL after recheck** |
| **AUD-016** | high | Source/token test retirement | AUD-FIX-01, AUD-FIX-02, AUD-FIX-03, AUD-FIX-04, AUD-FIX-05, AUD-FIX-07, AUD-FIX-08, AUD-FIX-09, AUD-FIX-10, AUD-FIX-11, AUD-FIX-12, AUD-FIX-13, AUD-FIX-16 | **FULL after recheck** |
| **AUD-017** | high | Repository/product identity declarations | AUD-FIX-01, AUD-FIX-07, AUD-FIX-13, AUD-FIX-15, AUD-FIX-16, AUD-FIX-17 | **FULL after recheck** |
| **AUD-018** | blocking | Gate D required evidence topology | AUD-FIX-14, AUD-FIX-15 | **FULL after recheck** |
| **AUD-019** | blocking | Historical Gate A/B/C EXO closure | AUD-FIX-14 | **FULL after recheck** |
| **AUD-020** | blocking | Gate D executed validation | AUD-FIX-15 | **FULL after recheck** |
| **AUD-021** | high | Full-roadmap conformance checker quality | AUD-FIX-01, AUD-FIX-06, AUD-FIX-13, AUD-FIX-14, AUD-FIX-17 | **FULL after recheck** |
| **AUD-022** | blocking | Reconstructed WIP gate truth | AUD-FIX-01, AUD-FIX-15 | **FULL after recheck** |
| **AUD-023** | high | Reconstructed WIP successor validator compatibility | AUD-FIX-01, AUD-FIX-13, AUD-FIX-14, AUD-FIX-16 | **FULL after recheck** |
| **AUD-024** | open-final-gate | OV15 / Gate E final qualification | AUD-FIX-17 | **FULL after recheck** |
| **UIX-001** | blocking | Semantic route tree / deep links | AUD-FIX-08, AUD-FIX-11, AUD-FIX-15 | **FULL after recheck** |
| **UIX-002** | blocking | Route/stale-response binding | AUD-FIX-09, AUD-FIX-15 | **FULL after recheck** |
| **UIX-003** | blocking | Shared screen-state contract | AUD-FIX-09, AUD-FIX-12, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-004** | blocking | Typed action gates / disabled-state remediation | AUD-FIX-09, AUD-FIX-12, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-005** | blocking | Scan workflow semantics | AUD-FIX-10, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-006** | high | Overview contract / next-action correctness | AUD-FIX-10, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-007** | high | Apps evidence truthfulness | AUD-FIX-03, AUD-FIX-10, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-008** | high | Tablet/adaptive master-detail UX | AUD-FIX-12, AUD-FIX-15, AUD-FIX-17 | **FULL after recheck** |
| **UIX-009** | high | UI/UX acceptance authority / stale predecessor artifacts | AUD-FIX-01, AUD-FIX-08, AUD-FIX-09, AUD-FIX-10, AUD-FIX-11, AUD-FIX-12, AUD-FIX-13, AUD-FIX-16, AUD-FIX-17 | **FULL after recheck** |

Coverage invariant after recheck: **33/33 FULL, 0 PARTIAL, 0 MISSING**. Continuous families such as AUD-016/AUD-023/UIX-009 are intentionally touched in the same overlay that invalidates a predecessor guard, with AUD-FIX-16 only the final physical sweep.

### Coverage defects corrected by this recheck

1. **Original OV14 ordering restored.** Physical deletion of Python/Textual, Room/NDJSON, generic helper scripts and final predecessor artifacts now happens in AUD-FIX-16 **after** Gate D, rather than being pulled ahead of Gate D. Gate E moves to AUD-FIX-17.
2. **Source/token guards no longer wait for the final sweep.** AUD-016 now has a same-overlay lifecycle rule; each behavioral replacement retires or rewrites its invalidated source guard immediately.
3. **Successor-validator/UI acceptance drift is continuous, not deferred.** AUD-023/UIX-009 are checked and migrated throughout AUD-FIX-08..13, then physically swept in AUD-FIX-16.
4. **AUD-008/AUD-014 now include persistence/restart parity.** Go authority exposure explicitly proves durable restart/migration behavior before Room/NDJSON physical removal.
5. **AUD-013 now explicitly includes bounded diagnostic retention/expiry/compaction**, not just redaction and support-bundle sealing.
6. **AUD-017 is now enforced after every overlay through evidence-derived metadata**, preventing aspirational product-default or qualification declarations from reappearing between gates.
7. **The full authoritative UX acceptance rules are explicit before and at final qualification**, including dominant action/question, destructive-review containment, state distinctions, plain-language primary copy, and exact textual equivalents.

## 11. Progression / stop rules

- Do not proceed past AUD-FIX-06 if backend authority still has duplicate Kotlin/scanner/operation/diagnostics truth.
- Do not proceed to AUD-FIX-13 acceptance convergence until replacement host/UI/runtime flows are demonstrably reachable.
- Do not run AUD-FIX-15 if AUD-FIX-14 reports missing Gate-D evidence nodes or unresolved historical evidence required for full conformance.
- Do not run AUD-FIX-16 physical retirement unless AUD-FIX-15 has produced a valid Gate-D seal on its exact tree; preserve that Gate-D tree identity after retirement.
- Do not set `production_authority_qualified=true`, `android_cutover_complete=true`, Gate-D closed, or OV15 ready before AUD-FIX-15 succeeds.
- Do not apply the bundled historical OV15/Gate-E artifacts as-is; AUD-FIX-17 must be generated from the post-retirement AUD-FIX-16 tree.
- If AUD-FIX-17 lacks the qualifying device/root/release environment, stop at qualification-deferred. That is a truthful campaign state, not a failure to follow the roadmap.

## 12. Definition of done

DustUI is done with this remediation campaign only when:

- All 33 root gap families are CLOSED with evidence, not merely absent tokens.
- No new independent gap family appears in the final convergence loop.
- Original roadmap requirements still in force are satisfied; amendment-overridden items are explicitly tagged as superseded rather than silently ignored.
- Authoritative UI/UX sections 5–14, 17 and 18 are represented in automated or executed acceptance evidence.
- Gate D is evidence-derived and closed on the final product architecture.
- Gate E qualifies the final release artifact in the required environment and binds release/runtime/tree/evidence identities.
- The repository contains one coherent product truth, one coherent acceptance authority, and no predecessor validation system that asserts the opposite architecture.

---

**Planning note:** this roadmap intentionally does not treat the reconstructed WIP as a patch to apply wholesale. Its useful changes should be harvested overlay-by-overlay against the fresh baseline, because the audit proved that the WIP still contains gate-truth, UI/UX and validator contradictions.
