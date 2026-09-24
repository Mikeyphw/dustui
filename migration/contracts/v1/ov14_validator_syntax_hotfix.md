# OV14 Validator Syntax Hotfix

This hotfix repairs invalid Python string quoting introduced in the OV14 legacy retirement overlay validators.

Scope is intentionally narrow:

- `scripts/validate_ov14_contract.py`
- `scripts/validate_ov13_cleaning_performance_termux_hotfix.py`
- `scripts/validate_ov14_validator_syntax_hotfix.py`

It does not change product code, Go authority, Android runtime behavior, Devtool wrapper jobs, or Gate D/E criteria.
