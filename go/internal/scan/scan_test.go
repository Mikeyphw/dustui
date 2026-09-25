package scan

import (
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"testing"
)

func TestScanCapturesSizeSemanticsSparseHardlinkAndSymlink(t *testing.T) {
	root := t.TempDir()
	sparse := filepath.Join(root, "sparse.bin")
	f, err := os.Create(sparse)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.Seek(8*1024*1024, 0); err != nil {
		t.Fatal(err)
	}
	if _, err := f.Write([]byte{1}); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
	hardlinkSupported := true
	if err := os.Link(sparse, filepath.Join(root, "sparse-hardlink.bin")); err != nil {
		if errors.Is(err, fs.ErrPermission) || errors.Is(err, fs.ErrInvalid) {
			hardlinkSupported = false
			t.Logf("hardlink fixture unsupported on this filesystem; keeping sparse-size assertions active: %v", err)
		} else {
			t.Fatal(err)
		}
	}
	symlinkSupported := true
	if err := os.Symlink("sparse.bin", filepath.Join(root, "sparse-link.bin")); err != nil {
		if errors.Is(err, fs.ErrPermission) || errors.Is(err, fs.ErrInvalid) {
			symlinkSupported = false
			t.Logf("symlink fixture unsupported on this filesystem; keeping sparse-size assertions active: %v", err)
		} else {
			t.Fatal(err)
		}
	}

	session, err := Scan(root, 7, DefaultOptions())
	if err != nil {
		t.Fatal(err)
	}
	if session.CanonicalRoot == "" || session.OptionsFingerprint == "" {
		t.Fatalf("identity fields missing: %+v", session)
	}
	if session.SizeSemanticsUsedForScan != SizeLogicalAndAllocated {
		t.Fatalf("unexpected size semantics: %s", session.SizeSemanticsUsedForScan)
	}
	if session.Integrity != IntegrityComplete {
		t.Fatalf("expected complete integrity: %+v", session)
	}
	var sparseEntry, hardlinkEntry, symlinkEntry *Entry
	for i := range session.Entries {
		e := &session.Entries[i]
		switch e.Name {
		case "sparse.bin":
			sparseEntry = e
		case "sparse-hardlink.bin":
			hardlinkEntry = e
		case "sparse-link.bin":
			symlinkEntry = e
		}
	}
	if sparseEntry == nil {
		t.Fatalf("missing sparse entry: %+v", session.Entries)
	}
	if hardlinkSupported && hardlinkEntry == nil {
		t.Fatalf("missing hardlink entry on filesystem that accepted hardlink fixture: %+v", session.Entries)
	}
	if symlinkSupported && symlinkEntry == nil {
		t.Fatalf("missing symlink entry on filesystem that accepted symlink fixture: %+v", session.Entries)
	}
	if sparseEntry.LogicalSizeBytes <= 8*1024*1024 {
		t.Fatalf("logical sparse size not captured: %+v", sparseEntry)
	}
	if sparseEntry.AllocatedSizeBytes <= 0 {
		t.Fatalf("allocated sparse size not captured: %+v", sparseEntry)
	}
	if hardlinkSupported {
		if hardlinkEntry.HardLinkGroup == "" || hardlinkEntry.HardLinkGroup != sparseEntry.HardLinkGroup {
			t.Fatalf("hard-link group not stable: sparse=%+v hard=%+v", sparseEntry, hardlinkEntry)
		}
	}
	if symlinkSupported {
		if !symlinkEntry.IsSymlink || symlinkEntry.LinkTarget == "" {
			t.Fatalf("symlink policy not captured: %+v", symlinkEntry)
		}
	}
	if !session.CanAnalyze() || !session.CanDriveDeletion() || !session.CanDriveDuplicateHashing() || !session.CanExportCompleteData() || !session.CanShowAsFresh() {
		t.Fatalf("complete scan should be authoritative: %+v", DecideAuthority(session, nil))
	}
}

func TestTruncatedScanCannotDriveDeletionOrExportCompleteData(t *testing.T) {
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "a.txt"), []byte("a"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "b.txt"), []byte("b"), 0o644); err != nil {
		t.Fatal(err)
	}
	opts := DefaultOptions()
	opts.MaxEntries = 1
	session, err := Scan(root, 9, opts)
	if err != nil {
		t.Fatal(err)
	}
	if !session.Truncated || session.Integrity != IntegrityTruncated {
		t.Fatalf("expected truncated integrity: %+v", session)
	}
	if session.CanDriveDeletion() || session.CanExportCompleteData() || session.CanShowAsFresh() {
		t.Fatalf("truncated scan gained authority: %+v", DecideAuthority(session, nil))
	}
}

func TestHistoryCompatibilityUsesSemanticsNotScanID(t *testing.T) {
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "a.txt"), []byte("a"), 0o644); err != nil {
		t.Fatal(err)
	}
	first, err := Scan(root, 1, DefaultOptions())
	if err != nil {
		t.Fatal(err)
	}
	second, err := Scan(root, 2, DefaultOptions())
	if err != nil {
		t.Fatal(err)
	}
	if first.ID == second.ID {
		t.Fatalf("test requires different scan ids")
	}
	if !first.CanCompareWith(second) {
		t.Fatalf("same semantics should compare despite different ids: %#v", first.CompatibilityWith(second))
	}
	changed := second
	changed.SizeSemanticsUsedForScan = SizeLogical
	if first.CanCompareWith(changed) {
		t.Fatalf("changed size semantics must not compare")
	}
}

func TestStaleAfterCleanupRevokesFreshAndDeletionAuthority(t *testing.T) {
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "a.txt"), []byte("a"), 0o644); err != nil {
		t.Fatal(err)
	}
	session, err := Scan(root, 3, DefaultOptions())
	if err != nil {
		t.Fatal(err)
	}
	stale := session.MarkStaleAfterCleanup()
	if stale.CanShowAsFresh() || stale.CanDriveDeletion() || stale.CanExportCompleteData() {
		t.Fatalf("stale scan retained authority: %+v", DecideAuthority(stale, &session))
	}
	if stale.Integrity != IntegrityStaleAfterCleanup {
		t.Fatalf("stale integrity not explicit: %+v", stale)
	}
}

func TestOptionsFingerprintCoversPolicies(t *testing.T) {
	a := DefaultOptions()
	b := DefaultOptions()
	b.IncludeHidden = false
	if a.Fingerprint() == b.Fingerprint() {
		t.Fatalf("hidden policy not in fingerprint")
	}
	c := DefaultOptions()
	c.Exclusions = []string{"cache", "tmp"}
	d := DefaultOptions()
	d.Exclusions = []string{"tmp", "cache"}
	if c.Fingerprint() != d.Fingerprint() {
		t.Fatalf("fingerprint should normalize exclusion order")
	}
}

func TestZeroValueOptionsRemainUnboundedAndDirectoryAggregationUsesRelativeParents(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "a", "b"), 0o755); err != nil {
		t.Fatal(err)
	}
	payload := []byte("nested payload")
	if err := os.WriteFile(filepath.Join(root, "a", "b", "file.txt"), payload, 0o644); err != nil {
		t.Fatal(err)
	}

	session, err := Scan(root, 11, Options{})
	if err != nil {
		t.Fatal(err)
	}
	var aDir, bDir, file *Entry
	for i := range session.Entries {
		e := &session.Entries[i]
		switch e.Path {
		case "a":
			aDir = e
		case "a/b":
			bDir = e
		case "a/b/file.txt":
			file = e
		}
	}
	if aDir == nil || bDir == nil || file == nil {
		t.Fatalf("zero-value options unexpectedly depth-limited scan: %+v", session.Entries)
	}
	if bDir.ParentPath != "a" || file.ParentPath != "a/b" {
		t.Fatalf("parent identity must be scan-relative: b=%+v file=%+v", bDir, file)
	}
	if aDir.FileCount != 1 || bDir.FileCount != 1 {
		t.Fatalf("directory file counts did not aggregate through relative parents: a=%+v b=%+v", aDir, bDir)
	}
	if aDir.LogicalSizeBytes < file.LogicalSizeBytes || bDir.LogicalSizeBytes < file.LogicalSizeBytes {
		t.Fatalf("directory totals did not include nested file: a=%+v b=%+v file=%+v", aDir, bDir, file)
	}
}

func TestExplicitMaxDepthZeroStillLimitsToTopLevel(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "a", "b"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "a", "b", "file.txt"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	opts := DefaultOptions()
	opts.MaxDepth = 0
	session, err := Scan(root, 12, opts)
	if err != nil {
		t.Fatal(err)
	}
	if len(session.Entries) != 0 {
		t.Fatalf("explicit maxDepth=0 should emit no children: %+v", session.Entries)
	}
}
