# OV13 OV01 readiness transition hotfix audit

The latest Gate D log shows Android compile/package progressed and 234 tests
passed, with one Android unit failure:

`Ov01MigrationContractTest.go readiness target is present but not authoritative`

The failure is caused by a stale OV01 assertion that still expects the Go
readiness target to be non-authoritative. OV13 explicitly changes that contract:
Go becomes product authority, Android buildability is mandatory again, and the
legacy engines are no longer product truth.

The fix updates the Android unit contract to accept OV01's old non-authoritative
state only before Gate D and to require authoritative Go readiness once OV13
cutover metadata/readiness is present.
