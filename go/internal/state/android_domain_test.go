package state

import (
	"context"
	"path/filepath"
	"testing"
)

func TestAndroidDomainRoundTripAndRecovery(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	if err := store.VerifyAndroidDomainSchema(ctx); err != nil {
		t.Fatal(err)
	}

	session := AndroidScanSession{ID: "scan-1", RootPath: "/storage", OptionsJSON: "{}", Status: "RUNNING", Backend: "root", StartedAt: 10, Generation: 2, IntegrityStatus: "UNKNOWN"}
	if err := store.UpsertAndroidScanSession(ctx, session); err != nil {
		t.Fatal(err)
	}
	if err := store.UpsertAndroidScanEntries(ctx, []AndroidScanEntry{{ScanID: "scan-1", Path: "/storage/a", ParentPath: "/storage", Name: "a", SizeBytes: 42, Depth: 1}}); err != nil {
		t.Fatal(err)
	}
	page, err := store.AndroidScanEntriesPage(ctx, "scan-1", 0, 128)
	if err != nil {
		t.Fatal(err)
	}
	if len(page) != 1 || page[0].SizeBytes != 42 {
		t.Fatalf("unexpected page: %#v", page)
	}
	count, err := store.InterruptActiveAndroidScans(ctx, 20)
	if err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("interrupt count=%d", count)
	}
	got, err := store.AndroidScanSessionByID(ctx, "scan-1")
	if err != nil {
		t.Fatal(err)
	}
	if got == nil || got.Status != "INTERRUPTED" || got.IntegrityStatus != "INTERRUPTED" {
		t.Fatalf("unexpected recovered session: %#v", got)
	}
}

func TestAndroidCleanupClaimIsSingleUse(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	op := AndroidCleanupOperation{ID: "op-1", ScanID: "scan-1", RootPath: "/storage", Status: "REVIEWED", SelectedCount: 1, PlannedBytes: 42, CreatedAt: 1, ReviewID: "op-1", ScanGeneration: 1, OutcomeCertainty: "CERTAIN"}
	inserted, err := store.InsertAndroidCleanupOperationIfAbsent(ctx, op)
	if err != nil {
		t.Fatal(err)
	}
	if !inserted {
		t.Fatal("initial operation not inserted")
	}
	inserted, err = store.InsertAndroidCleanupOperationIfAbsent(ctx, op)
	if err != nil {
		t.Fatal(err)
	}
	if inserted {
		t.Fatal("duplicate operation inserted")
	}
	claimed, err := store.ClaimAndroidCleanupOperation(ctx, "op-1")
	if err != nil {
		t.Fatal(err)
	}
	if claimed != 1 {
		t.Fatalf("first claim=%d", claimed)
	}
	claimed, err = store.ClaimAndroidCleanupOperation(ctx, "op-1")
	if err != nil {
		t.Fatal(err)
	}
	if claimed != 0 {
		t.Fatalf("second claim=%d", claimed)
	}
}

func TestAndroidQueryAuthorityAvoidsWholeSnapshotFallback(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	if err := store.UpsertAndroidScanSession(ctx, AndroidScanSession{ID: "scan-query", RootPath: "/storage", OptionsJSON: "{}", Status: "COMPLETED", Backend: "root", StartedAt: 1, Generation: 3, IntegrityStatus: "COMPLETE"}); err != nil {
		t.Fatal(err)
	}
	entries := []AndroidScanEntry{
		{ScanID: "scan-query", Path: "/storage/emulated/0/Android/data/com.example.app/cache/a.tmp", ParentPath: "/storage/emulated/0/Android/data/com.example.app/cache", Name: "a.tmp", SizeBytes: 200, ModifiedAt: 10},
		{ScanID: "scan-query", Path: "/storage/emulated/0/Android/data/com.example.app/files/b.bin", ParentPath: "/storage/emulated/0/Android/data/com.example.app/files", Name: "b.bin", SizeBytes: 200, ModifiedAt: 11},
		{ScanID: "scan-query", Path: "/storage/emulated/0/Download/app.apk", ParentPath: "/storage/emulated/0/Download", Name: "app.apk", SizeBytes: 900, ModifiedAt: 12},
		{ScanID: "scan-query", Path: "/storage/emulated/0/Download/archive.zip", ParentPath: "/storage/emulated/0/Download", Name: "archive.zip", SizeBytes: 700, ModifiedAt: 13},
	}
	if err := store.UpsertAndroidScanEntries(ctx, entries); err != nil {
		t.Fatal(err)
	}
	entry, err := store.AndroidScanEntryByPath(ctx, "scan-query", entries[2].Path)
	if err != nil || entry == nil || entry.Name != "app.apk" {
		t.Fatalf("entry lookup=%#v err=%v", entry, err)
	}
	buckets, err := store.AndroidDuplicateSizeBuckets(ctx, "scan-query", 1)
	if err != nil || len(buckets) != 1 || buckets[0].SizeBytes != 200 || buckets[0].CandidateCount != 2 {
		t.Fatalf("duplicate buckets=%#v err=%v", buckets, err)
	}
	archives, err := store.AndroidFilesByExtensions(ctx, "scan-query", []string{"zip", "7z"})
	if err != nil || len(archives) != 1 || archives[0].Name != "archive.zip" {
		t.Fatalf("archives=%#v err=%v", archives, err)
	}
	category := "CACHE"
	pkg := "com.example.app"
	results, err := store.SearchAndroidScanEntries(ctx, "scan-query", AndroidSearchRequest{Query: ">100", Category: &category, PackageName: &pkg, Limit: 20})
	if err != nil || len(results) != 1 || results[0].Name != "a.tmp" || results[0].Category != "CACHE" {
		t.Fatalf("search=%#v err=%v", results, err)
	}
	ext := "apk"
	results, err = store.SearchAndroidScanEntries(ctx, "scan-query", AndroidSearchRequest{Extension: &ext, Limit: 20})
	if err != nil || len(results) != 1 || results[0].Name != "app.apk" || results[0].Category != "APK" {
		t.Fatalf("extension search=%#v err=%v", results, err)
	}
}
