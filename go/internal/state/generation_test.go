package state

import (
	"github.com/mikeyphw/dustui/go/internal/protocol"
	"testing"
)

func TestExpectedGenerationWriteRejectsStaleActor(t *testing.T) {
	record := GenerationRecord{ID: "scan-1", Generation: 4, Value: "old"}
	_, err := WriteExpected(record, 3, "new")
	if protocol.ReasonOf(err) != protocol.ReasonStaleGeneration {
		t.Fatalf("reason=%q err=%v", protocol.ReasonOf(err), err)
	}
	updated, err := WriteExpected(record, 4, "new")
	if err != nil {
		t.Fatal(err)
	}
	if updated.Generation != 5 || updated.Value != "new" {
		t.Fatalf("bad update: %+v", updated)
	}
}
