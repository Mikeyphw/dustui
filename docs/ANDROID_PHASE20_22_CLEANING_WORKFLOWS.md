# Android Phases 20–22 · Complete cleaning workflows

This combined phase builds on the validated Phase 19 baseline at commit `cfd46f7`.

## Phase 20 · Saved cleanup rules

Cleanup rules are versioned current-scan searches, never deletion commands. The editor previews drafts automatically and can reveal the exact matched and blocked paths before a rule is saved. Conditions cover include/exclude roots, extension, minimum/maximum size, age, category, package, safe bounded path expression, relative depth, file/directory type, verified duplicates, and residue confidence.

Depth is measured relative to the longest matching include root, or the scan root. Path expressions use a deliberately bounded subset rather than Java's unrestricted backtracking grammar. Imports receive new IDs, remain disabled, and cannot be enabled until the user opens and saves them. Rules can be duplicated, exported, deleted, and converted from recommendations or current growth results. Empty libraries remain empty.

## Phase 21 · Storage growth and history

The history workbench compares arbitrary completed-scan endpoints using leaf files and distinguishes added, grown, shrunk, and removed paths. It provides a multi-scan timeline with an equivalent accessible point list, endpoint controls, deterministic paging, and grouping by category, directory, package, extension, root module, or mount.

Insights identify new large files, files crossing 100 MiB, fast-growing directories, sudden log/cache growth, root-module growth, and mount growth. Snapshot retention is configurable and pruning removes old entries before sessions. Understood state is reversible and scoped to the exact comparison identity.

Only a comparison ending at the newest active scan is actionable. Historical endpoints, including their apparently current-side rows, remain informational and cannot reveal as live files, create rules, start targeted rescans, select, or plan deletion.

## Phase 22 · Deletion verification and reports

Every accepted top-level path is reinspected after root execution. Reports distinguish missing, still present, and verification unavailable, then classify each path as Verified deleted, Partial, Failed, Blocked, or Changed since review. Root-reported bytes and free-space changes remain supporting evidence only.

Reports retain planned, attempted, root-reported, confirmed, partial, and failed byte totals; fingerprints; verification attempts; device and filesystem context; and per-path outcomes. Report persistence failures are returned in the cleanup result instead of being swallowed. Malformed stored reports surface diagnostics. Schema-1 reports migrate into the schema-2 accounting model.

Users can search by operation/path, filter by root/date/status, retry verification, export redacted JSON or text, and start a normal targeted rescan from the smallest safe common parent. Reports remain readable without an active scan and never trigger deletion directly.

## Shared safety contract

- no automatic or background deletion;
- exact current paths before confirmation;
- imported rules require explicit review;
- protected paths cannot be overridden;
- active scan/root binding and fingerprint revalidation remain mandatory;
- historical endpoints never become live deletion targets;
- inspection failures never count as confirmed deletion;
- successful mutation makes current analyses stale;
- all destructive actions route through the existing permanent-deletion planner;
- Android build, tests, lint, and source contracts require zero warnings and zero errors.
