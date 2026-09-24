package state

import "github.com/mikeyphw/dustui/go/internal/protocol"

type GenerationRecord struct {
	ID         string
	Generation int64
	Value      string
}

func WriteExpected(record GenerationRecord, expectedGeneration int64, nextValue string) (GenerationRecord, error) {
	if record.Generation != expectedGeneration {
		return record, protocol.NewError(protocol.ReasonStaleGeneration, "expected-generation predicate rejected stale actor")
	}
	record.Generation++
	record.Value = nextValue
	return record, nil
}
