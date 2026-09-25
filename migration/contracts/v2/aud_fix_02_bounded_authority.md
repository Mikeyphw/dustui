# AUD-FIX-02 — bounded query, scanner, and duplicate-verification authority

This overlay closes the AUD-FIX-02 slice of the remediation roadmap only:

- `AUD-007`: routine Android browsing/search/export/product projection must not rebuild a complete scan in Kotlin memory; pages are bounded at the Go/SQLite boundary.
- `AUD-009`: privileged traversal must delegate to the same `go/internal/scan` authority used by the normal Go path; privilege is transport/identity only.
- `AUD-010`: exact duplicate edge/full hashing, filesystem identity checks, hard-link handling, cancellation, and safe reclaimability are Go/root-worker authority; Kotlin is IPC/presentation only.

## Required invariants

1. `go/internal/rootworker/scan.go` calls `scan.ScanContext` and contains no independent `Walk`, `ReadDir`, inode-dedupe, or traversal implementation.
2. `root-duplicates-verify` is a bounded Go command. Requests are capped, cancellation is typed, files are re-stat'd after full hashing, hard links do not count as separate reclaimable physical copies, and protected/root-boundary checks participate in eligibility.
3. Android root code contains no SHA-256 duplicate implementation and delegates to `GoRootWorker.verifyDuplicates`.
4. `GoDomainStore` does not expose a production whole-scan `entriesOnce`/`observeEntries` drain. Routine Explorer, whole-scan search, category selection, large-file workbench, and CSV export use bounded pages/streaming.
5. `MainViewModel` contains no `allEntries` authority and does not call list-wide repository analysis to rebuild the scan in memory.
6. Bulk category selection refuses truncated result sets instead of silently deleting an incomplete first page.
7. User-visible Explorer and whole-scan search expose paging controls when multiple pages exist.
8. AUD-FIX-03+ authority claims remain open. Gate D remains open.

## Validation

Run:

```bash
python3 scripts/validate_audfix02_contract.py --audit-loop --write-evidence
```

This is a `--no-validate` overlay. Full Devtool validation remains reserved for AUD-FIX-06, AUD-FIX-15, and AUD-FIX-17.
