# OV03 Termux hard-link fixture hotfix audit

- Finding: `TestScanCapturesSizeSemanticsSparseHardlinkAndSymlink` failed on
  Termux because `os.Link` returned permission denied for the temporary fixture.
- Classification: test fixture portability bug, not Go scanner semantic failure.
- Resolution: make hard-link and symlink fixture assertions capability-aware.
  Hard-link grouping is still asserted on filesystems that accept hard links.
- Regression evidence:
  - `python3 scripts/validate_ov03_contract.py --audit-loop`
  - `cd go && go test ./...`
