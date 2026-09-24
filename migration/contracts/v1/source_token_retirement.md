# Source/token contract-test retirement plan

OV01 inventories source/token tests and keeps them as migration tripwires while their legacy implementation remains authoritative.

Retirement rules:

1. Add behavioral, failure-window, instrumentation, or Devtool evidence in the replacement overlay.
2. Retire the corresponding source/token guard in that same overlay.
3. OV14 is only a final sweep for leftovers, not the first mass retirement.
4. No finding closes merely because its legacy code was deleted.

The machine-readable map is `migration/ledger/source_token_contract_tests.json`.
