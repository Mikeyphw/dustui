# OV14 Audit Loop

- Android buildability remains mandatory after Gate D.
- Devtool wrapper + target-local EXO jobs are preserved and treated as supported.
- The stale OV13 performance validator rule that rejected jobs/workflows was removed.
- Production retirement facts are represented in `go/internal/retirement` and validated by `go test ./...`.
- Static retirement proof is captured by `scripts/validate_ov14_contract.py --audit-loop`.
- This overlay intentionally does not introduce Gate-E adversarial qualification; that remains OV15.
