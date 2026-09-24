#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
schema=(root/"go/internal/state/schema.go").read_text()
gomod=(root/"go/go.mod").read_text()
gosum=(root/"go/go.sum").read_text()
assert "scan_sessions(root_path, completed_at_ms, generation)" in schema
assert "scan_sessions(canonical_root" not in schema
assert "modernc.org/sqlite v1.36.3" in gomod
assert "modernc.org/sqlite v1.36.3 h1:qYMYlFR+rtLDUzuXoST1SDIdEPbX8xzuhdF90WsX1ss=" in gosum
assert "modernc.org/libc v1.61.13" in gomod
print("REM-02 SQLite integrity hotfix PASS")
