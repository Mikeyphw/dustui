package scan

import (
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
	if err := os.Link(sparse, filepath.Join(root, "sparse-hardlink.bin")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("sparse.bin", filepath.Join(root, "sparse-link.bin")); err != nil {
		t.Fatal(err)
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
	if sparseEntry == nil || hardlinkEntry == nil || symlinkEntry == nil {
		t.Fatalf("missing expected entries: %+v", session.Entries)
	}
	if sparseEntry.LogicalSizeBytes <= 8*1024*1024 {
		t.Fatalf("logical sparse size not captured: %+v", sparseEntry)
	}
	if sparseEntry.AllocatedSizeBytes <= 0 {
		t.Fatalf("allocated sparse size not captured: %+v", sparseEntry)
	}
	if hardlinkEntry.HardLinkGroup == "" || hardlinkEntry.HardLinkGroup != sparseEntry.HardLinkGroup {
		t.Fatalf("hard-link group not stable: sparse=%+v hard=%+v", sparseEntry, hardlinkEntry)
	}
	if !symlinkEntry.IsSymlink || symlinkEntry.LinkTarget == "" {
		t.Fatalf("symlink policy not captured: %+v", symlinkEntry)
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
