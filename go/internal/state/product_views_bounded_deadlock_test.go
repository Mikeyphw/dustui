package state

import (
	"context"
	"path/filepath"
	"testing"
	"time"
)

func TestBoundedDuplicateCandidatesSingleConnectionDoesNotDeadlock(t *testing.T) {
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	setup := context.Background()
	const scanID = "scan-single-connection-duplicates"
	if err := store.UpsertAndroidScanSession(setup, AndroidScanSession{
		ID:              scanID,
		RootPath:        "/storage/emulated/0",
		OptionsJSON:     "{}",
		Status:          "COMPLETED",
		Backend:         "root",
		StartedAt:       1,
		Generation:      1,
		IntegrityStatus: "VERIFIED",
	}); err != nil {
		t.Fatal(err)
	}

	entries := []AndroidScanEntry{
		{ScanID: scanID, Path: "/a.bin", ParentPath: "/", Name: "a.bin", SizeBytes: 2 << 20, AllocatedBytes: 2 << 20},
		{ScanID: scanID, Path: "/b.bin", ParentPath: "/", Name: "b.bin", SizeBytes: 2 << 20, AllocatedBytes: 2 << 20},
		{ScanID: scanID, Path: "/c.bin", ParentPath: "/", Name: "c.bin", SizeBytes: 2 << 20, AllocatedBytes: 2 << 20},
		{ScanID: scanID, Path: "/d.bin", ParentPath: "/", Name: "d.bin", SizeBytes: 4 << 20, AllocatedBytes: 4 << 20},
		{ScanID: scanID, Path: "/e.bin", ParentPath: "/", Name: "e.bin", SizeBytes: 4 << 20, AllocatedBytes: 4 << 20},
		{ScanID: scanID, Path: "/unique.bin", ParentPath: "/", Name: "unique.bin", SizeBytes: 8 << 20, AllocatedBytes: 8 << 20},
	}
	if err := store.UpsertAndroidScanEntries(setup, entries); err != nil {
		t.Fatal(err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 750*time.Millisecond)
	defer cancel()
	got, err := store.boundedDuplicateCandidates(ctx, scanID)
	if err != nil {
		t.Fatalf("bounded duplicate query must not re-enter the single SQLite connection: %v", err)
	}
	if len(got) != 2 {
		t.Fatalf("expected two duplicate-size buckets, got %d: %#v", len(got), got)
	}
	for _, candidate := range got {
		paths, ok := candidate["paths"].([]string)
		if !ok || len(paths) < 2 {
			t.Fatalf("expected bounded duplicate paths, got %#v", candidate)
		}
	}
}
