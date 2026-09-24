package state

import (
	"strings"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/protocol"
)

func TestSQLiteSchemaContainsRequiredAuthorityTables(t *testing.T) {
	if err := ValidateSchemaContract(); err != nil {
		t.Fatal(err)
	}
	joined := strings.Join(CreateStatements(), "\n")
	for _, table := range RequiredTables {
		if !strings.Contains(joined, "CREATE TABLE IF NOT EXISTS "+table) {
			t.Fatalf("missing table %s", table)
		}
	}
}

func TestRoomV3ImportPlanIsOneWayAndCoversRetentionTables(t *testing.T) {
	if err := ValidateRoomV3ImportPlan(); err != nil {
		t.Fatal(err)
	}
	var sawQuarantine bool
	for _, mapping := range RoomV3ImportPlan() {
		if mapping.NewWrites {
			t.Fatalf("mapping allows dual/new Room write: %+v", mapping)
		}
		if mapping.SourceTable == "quarantine_entries" {
			sawQuarantine = true
			if mapping.Mode != ImportReadOnly || !strings.Contains(mapping.Note, "no new writes") {
				t.Fatalf("quarantine retention policy missing: %+v", mapping)
			}
		}
	}
	if !sawQuarantine {
		t.Fatalf("quarantine_entries import contract missing")
	}
}

func TestMigrationRollbackIsSafe(t *testing.T) {
	runner := MigrationRunner{Version: 0}
	failed, err := runner.Apply(OV02Migration(), 3)
	if protocol.ReasonOf(err) != protocol.ReasonSchemaMigrationFailed {
		t.Fatalf("expected schema migration failure, got %v", err)
	}
	if failed.Version != 0 || failed.Journal[len(failed.Journal)-1] != "ROLLBACK" {
		t.Fatalf("migration did not roll back safely: %+v", failed)
	}
	committed, err := runner.Apply(OV02Migration(), -1)
	if err != nil {
		t.Fatal(err)
	}
	if committed.Version != SchemaVersion || committed.Journal[len(committed.Journal)-1] != "COMMIT" {
		t.Fatalf("migration did not commit safely: %+v", committed)
	}
}
