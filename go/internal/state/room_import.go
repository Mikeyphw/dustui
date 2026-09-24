package state

import "fmt"

type ImportMode string

const (
	ImportOneWay      ImportMode = "one_way"
	ImportReadOnly    ImportMode = "read_only_retention"
	ImportNoDualWrite ImportMode = "no_dual_write"
)

type RoomV3Mapping struct {
	SourceTable string     `json:"sourceTable"`
	TargetTable string     `json:"targetTable"`
	Mode        ImportMode `json:"mode"`
	NewWrites   bool       `json:"newWrites"`
	Note        string     `json:"note"`
}

func RoomV3ImportPlan() []RoomV3Mapping {
	return []RoomV3Mapping{
		{SourceTable: "scan_sessions", TargetTable: "scan_sessions", Mode: ImportOneWay, NewWrites: false, Note: "import retained session history with explicit scan generation"},
		{SourceTable: "scan_entries", TargetTable: "scan_entries", Mode: ImportOneWay, NewWrites: false, Note: "import retained entries with logical/allocated size fields"},
		{SourceTable: "cleanup_operations", TargetTable: "operations", Mode: ImportOneWay, NewWrites: false, Note: "legacy cleanup rows become operation history only"},
		{SourceTable: "cleanup_plan_items", TargetTable: "cleanup_manifests", Mode: ImportOneWay, NewWrites: false, Note: "legacy plan items become immutable manifest evidence"},
		{SourceTable: "quarantine_entries", TargetTable: "cleanup_results", Mode: ImportReadOnly, NewWrites: false, Note: "legacy quarantine entries stay readable/importable by retention policy; no new writes"},
	}
}

func ValidateRoomV3ImportPlan() error {
	seen := map[string]bool{}
	for _, mapping := range RoomV3ImportPlan() {
		if mapping.SourceTable == "" || mapping.TargetTable == "" {
			return fmt.Errorf("mapping contains empty table: %+v", mapping)
		}
		if mapping.NewWrites {
			return fmt.Errorf("room import mapping allows new writes: %+v", mapping)
		}
		seen[mapping.SourceTable] = true
	}
	for _, required := range []string{"scan_sessions", "scan_entries", "cleanup_operations", "cleanup_plan_items", "quarantine_entries"} {
		if !seen[required] {
			return fmt.Errorf("room v3 import mapping missing %s", required)
		}
	}
	return nil
}
