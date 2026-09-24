# OV13 wrapper validator hotfix audit

Scope: validator-only compatibility fix.

Non-goals:
- no `.devtool.toml` changes
- no wrapper launcher changes
- no Android code changes
- no Go runtime/product behavior changes
- no Gate D relaxation

Evidence expected after apply:

```bash
python3 scripts/validate_ov13_devtool_wrapper_exo_jobs.py --static-only
python3 scripts/validate_ov13_devtool_wrapper_exo_jobs.py
```
