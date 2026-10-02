#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
product = (root / "go/internal/state/product_views_bounded.go").read_text()
store = (root / "go/internal/state/sqlite_store.go").read_text()
functional_test = root / "go/internal/state/product_views_bounded_deadlock_test.go"
contract_test = root / "go/internal/deadlockcontract/single_connection_test.go"

errors = []
for token in (
    "const sqliteAuthorityMaxOpenConnections = 1",
    "db.SetMaxOpenConns(sqliteAuthorityMaxOpenConnections)",
    "db.SetMaxIdleConns(sqliteAuthorityMaxOpenConnections)",
):
    if token not in store:
        errors.append(f"SQLiteStore single-connection production invariant missing: {token}")

signature = "func (s *SQLiteStore) boundedDuplicateCandidates"
start = product.find(signature)
if start < 0:
    errors.append("boundedDuplicateCandidates missing")
else:
    tail = product[start:]
    end = tail.find("\nfunc ", len(signature))
    body = tail if end < 0 else tail[:end]
    first = body.find("rows, err := s.db.QueryContext")
    close = body.find("if err := rows.Close(); err != nil")
    second = body.find("pathRows, err := s.db.QueryContext")
    if min(first, close, second) < 0 or not (first < close < second):
        errors.append("duplicate-candidate path query is not sequenced after bucket Rows close")
    if "all goroutines are asleep" not in body:
        errors.append("root-cause comment documenting unrecoverable Go deadlock is missing")

if not functional_test.is_file():
    errors.append("functional single-connection regression test missing")
if not contract_test.is_file():
    errors.append("stdlib-only deadlock contract test missing")

if errors:
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)
print("go-sqlite-deadlock-rootfix contract: PASS")
