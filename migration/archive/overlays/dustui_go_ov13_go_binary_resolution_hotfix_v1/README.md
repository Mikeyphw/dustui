# DustUI OV13 Go binary resolution hotfix v1

Fixes OV13 Gate D validator execution under Gradle/Termux by resolving the Go binary explicitly instead of relying on the Gradle daemon PATH.

Scope:
- `scripts/validate_ov13_contract.py`
- `migration/contracts/v1/ov13_go_binary_resolution_hotfix.md`

No production Android, Go engine, scanner, cleanup, or runtime behavior is changed.
