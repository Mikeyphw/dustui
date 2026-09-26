#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


HELPER = '''
func assumeHardlinkCapabilityForScanTest(t *testing.T) {
	t.Helper()
	dir := t.TempDir()
	src := filepath.Join(dir, "hardlink-probe-src")
	dst := filepath.Join(dir, "hardlink-probe-dst")
	if err := os.WriteFile(src, []byte("x"), 0o600); err != nil {
		t.Fatalf("hardlink capability probe setup failed: %v", err)
	}
	if err := os.Link(src, dst); err != nil {
		t.Skipf("hardlink capability unavailable on this filesystem/runtime: %v", err)
	}
}
'''.strip()


def patch_scan_test(go_root: Path) -> bool:
    path = go_root / "go" / "internal" / "scan" / "scan_test.go"
    if not path.exists():
        path = go_root / "internal" / "scan" / "scan_test.go"
    if not path.exists():
        raise SystemExit(f"scan_test.go not found under {go_root}")

    text = path.read_text()

    if "assumeHardlinkCapabilityForScanTest" not in text:
        marker = ")\n\n"
        if marker not in text:
            raise SystemExit(f"{path}: cannot find Go import block boundary")
        text = text.replace(marker, marker + HELPER + "\n\n", 1)

    needle = "func TestScanCapturesSizeSemanticsSparseHardlinkAndSymlink(t *testing.T) {\n"
    if needle not in text:
        raise SystemExit(f"{path}: cannot find hardlink semantics test")

    replacement = needle + "\tassumeHardlinkCapabilityForScanTest(t)\n"
    if replacement not in text:
        text = text.replace(needle, replacement, 1)

    path.write_text(text)
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".", help="Go module root or repo root")
    args = parser.parse_args()

    go_root = Path(args.root).resolve()
    changed = patch_scan_test(go_root)
    print(f"audfix15_termux_hardlink_compat: patched={changed} root={go_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
