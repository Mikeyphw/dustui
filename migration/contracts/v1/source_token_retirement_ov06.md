# OV06 source-token retirement update

OV06 keeps source/token tests only as temporary migration guards. New privileged safety assertions live in Go behavior tests and `scripts/validate_ov06_contract.py`.

Tokens to retire by OV14 after behavioral replacement:

- raw shell/root command source scans;
- Kotlin root admission source-token assertions;
- legacy cleanup authorization token checks;
- Android-only root lifecycle assertions duplicated by the Go privileged runtime tests.
