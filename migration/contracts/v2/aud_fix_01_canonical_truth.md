# AUD-FIX-01 — Canonical truth and successor-aware audit machinery

## Authority

Gate D qualification is computed only by `scripts/refresh_qualification_state.py` from
`migration/qualification/v1/gate_d_evidence.json`. Source-token absence, an OV14
retirement ledger, product metadata, or `.devtool.toml` cannot independently close it.

`migration/qualification/v1/current_state.json` is the generated canonical state consumed
by product/readiness contracts, migration ledgers, Devtool metadata pointers, and validators.
OV14 retirement is a **post-Gate-D** observation and never a prerequisite for Gate D.

## Requirement index

`migration/requirements/v2/requirements.json` indexes the original roadmap, Android
suspension amendment, authoritative Go/UI/UX audit, AUD-FIX roadmap, and all 33 converged
gap families with stable IDs, owners, supersession, evidence kinds, and closure criteria.

## Guard lifecycle

Predecessor acceptance guards must be either successor-aware invariant bridges or be
retired in the same overlay that installs their behavioral replacement. Historical tokens
must never become current acceptance authority.

## AUD-FIX-01 validation

Run `python3 scripts/validate_audfix01_contract.py --audit-loop --write-evidence`.

This overlay intentionally does **not** run full Devtool validation. Gate D remains OPEN.
