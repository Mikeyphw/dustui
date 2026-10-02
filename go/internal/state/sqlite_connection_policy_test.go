package state

import (
	"context"
	"path/filepath"
	"testing"
)

func TestSQLiteAuthorityKeepsOneConnectionAndConfiguredPragmas(t *testing.T) {
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "authority.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	if got := store.db.Stats().MaxOpenConnections; got != sqliteAuthorityMaxOpenConnections {
		t.Fatalf("max open connections = %d, want %d", got, sqliteAuthorityMaxOpenConnections)
	}

	ctx := context.Background()
	var foreignKeys int
	if err := store.db.QueryRowContext(ctx, "PRAGMA foreign_keys").Scan(&foreignKeys); err != nil {
		t.Fatal(err)
	}
	if foreignKeys != 1 {
		t.Fatalf("foreign_keys = %d, want 1", foreignKeys)
	}

	var journalMode string
	if err := store.db.QueryRowContext(ctx, "PRAGMA journal_mode").Scan(&journalMode); err != nil {
		t.Fatal(err)
	}
	if journalMode != "wal" {
		t.Fatalf("journal_mode = %q, want wal", journalMode)
	}

	var synchronous int
	if err := store.db.QueryRowContext(ctx, "PRAGMA synchronous").Scan(&synchronous); err != nil {
		t.Fatal(err)
	}
	if synchronous != 1 { // SQLITE_SYNC_NORMAL
		t.Fatalf("synchronous = %d, want 1 (NORMAL)", synchronous)
	}
}
