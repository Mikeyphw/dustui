# AUD-FIX-03 — Go domain runtime authority and persistence parity

Status: implementation complete; Gate D remains open.

## Authority boundary

Android may collect platform observations (PackageManager/root inventory), maintain UI preferences, and perform presentation-only filtering/sorting/pagination. Product truth for the migrated domains is returned by versioned Go runtime operations and carries `scanId`, `generation`, freshness, explicit evidence completeness/degradation, boundedness, durability, and source identity.

Canonical schemas:

- request: `dustui.domain.authority.request.v1`
- response: `dustui.domain.authority.response.v1`
- source: `go-sqlite`

Typed Android-consumed domains are package/storage evidence, residue, cleanup intelligence, recommendations, growth, modules, rules preview, duplicate projection, and cleanup verification.

## Persistence/restart contract

Go SQLite persists package evidence, duplicate projections, residue/recommendation/rule projections, and cleanup verification reports. `TestAUD_FIX03AuthoritySurvivesRestartWithGenerationAndEvidenceTruth` closes and reopens SQLite and requires generation/evidence truth plus cleanup verification to survive. Android report list/get reconnect through Go; `CleanupReportStore` is export serialization only and is not a product authority.

Room/NDJSON physical deletion is **not** part of AUD-FIX-03. This overlay establishes the persistence proof required before AUD-FIX-16 may delete those predecessor artifacts.

## Kotlin boundary

Legacy analyzer implementations may remain as compatibility/test code until later physical retirement, but production must not call their domain-authority entry points. Presentation-only `evaluate`/filter/sort helpers and local form validation are permitted; they may not create eligibility, evidence, recommendation, verification, duplicate, residue, growth, or module truth.

## Evidence truth

Package/storage results expose `supported`, `partial`, `unavailable`, or `blocked` plus degradation reasons. Missing evidence must never be converted into an ordinary zero measurement.

## Validation boundary

AUD-FIX-03 is not a full Devtool gate. Its narrow validator runs source/reachability checks and Go packages that do not require uncached dependencies. The SQLite restart fixture is required source evidence and must execute on the next qualified Devtool/full-validation host with `modernc.org/sqlite` available. That environment condition does not authorize substitution with a CGO SQLite driver.
