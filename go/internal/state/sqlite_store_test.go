package state

import (
	"context"
	"path/filepath"
	"testing"
)

func TestSQLiteStoreDurabilityAndSchema(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "dustui.db")
	store, err := OpenSQLiteStore(path)
	if err != nil { t.Fatal(err) }

	version, err := store.SchemaVersion(ctx)
	if err != nil { t.Fatal(err) }
	if version != SchemaVersion { t.Fatalf("schema=%d want=%d", version, SchemaVersion) }

	done := int64(200)
	session := PersistedScanSession{
		ID: "scan-1", Generation: 7, RootPath: "/storage/emulated/0",
		OptionsFingerprint: "opts", Backend: "root", Status: "complete",
		IntegrityStatus: "complete", StartedAtMS: 100, CompletedAtMS: &done,
		ExpectedGeneration: 7,
	}
	entries := []PersistedScanEntry{
		{ScanID:"scan-1", Path:"/storage/emulated/0/A", ParentPath:"/storage/emulated/0", Name:"A", LogicalSizeBytes:10, AllocatedSizeBytes:4096, IsDirectory:false, Depth:1, ModifiedAtMS:99, FileCount:1},
	}
	if err := store.ReplaceScanGeneration(ctx, session, entries); err != nil { t.Fatal(err) }
	if err := store.Close(); err != nil { t.Fatal(err) }

	store, err = OpenSQLiteStore(path)
	if err != nil { t.Fatal(err) }
	defer store.Close()
	gotSession, gotEntries, err := store.LoadScanGeneration(ctx, "scan-1")
	if err != nil { t.Fatal(err) }
	if gotSession.Generation != 7 || len(gotEntries) != 1 || gotEntries[0].Path != entries[0].Path {
		t.Fatalf("unexpected reopened state: %#v %#v", gotSession, gotEntries)
	}
}

func TestReplaceScanGenerationRollsBackOnFailure(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil { t.Fatal(err) }
	defer store.Close()

	session := PersistedScanSession{
		ID:"scan-rb", Generation:1, RootPath:"/x", OptionsFingerprint:"a",
		Backend:"root", Status:"complete", IntegrityStatus:"complete",
		StartedAtMS:1, ExpectedGeneration:1,
	}
	good := []PersistedScanEntry{{ScanID:"scan-rb", Path:"/x/a", ParentPath:"/x", Name:"a", Depth:1}}
	if err := store.ReplaceScanGeneration(ctx, session, good); err != nil { t.Fatal(err) }

	session.Generation = 2
	dup := []PersistedScanEntry{
		{ScanID:"scan-rb", Path:"/x/b", ParentPath:"/x", Name:"b", Depth:1},
		{ScanID:"scan-rb", Path:"/x/b", ParentPath:"/x", Name:"b2", Depth:1},
	}
	if err := store.ReplaceScanGeneration(ctx, session, dup); err == nil {
		t.Fatal("expected duplicate-path transaction failure")
	}
	got, entries, err := store.LoadScanGeneration(ctx, "scan-rb")
	if err != nil { t.Fatal(err) }
	if got.Generation != 1 || len(entries) != 1 || entries[0].Path != "/x/a" {
		t.Fatalf("rollback lost previous authority: %#v %#v", got, entries)
	}
}

func TestCleanupJournalPersistsAcrossReopen(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "dustui.db")
	store, err := OpenSQLiteStore(path)
	if err != nil { t.Fatal(err) }
	rec := CleanupJournalRecord{
		OperationID:"op-1", Sequence:1, State:"running",
		Reason:"admitted", Path:"/tmp/a", MutationProtocol:"dustui.cleanup.v1", CreatedAtMS:10,
	}
	if err := store.RecordCleanupMutation(ctx, rec, 3); err != nil { t.Fatal(err) }
	store.Close()

	store, err = OpenSQLiteStore(path)
	if err != nil { t.Fatal(err) }
	defer store.Close()
	got, err := store.CleanupJournal(ctx, "op-1")
	if err != nil { t.Fatal(err) }
	if len(got) != 1 || got[0].MutationProtocol != rec.MutationProtocol {
		t.Fatalf("journal not durable: %#v", got)
	}
}
