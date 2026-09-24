package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/mikeyphw/dustui/go/internal/readiness"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/state"
)

func main() {
	command := "readiness"
	if len(os.Args) > 1 {
		command = os.Args[1]
	}

	var payload any
	switch command {
	case "readiness", "version", "protocol":
		payload = readiness.Current()
	case "schema":
		payload = map[string]any{
			"schemaVersion":    state.SchemaVersion,
			"requiredTables":   state.RequiredTables,
			"createStatements": state.CreateStatements(),
			"roomV3ImportPlan": state.RoomV3ImportPlan(),
		}
	case "scan-contract":
		payload = map[string]any{
			"integrityStates":       []scan.IntegrityState{scan.IntegrityComplete, scan.IntegrityPartial, scan.IntegrityTruncated, scan.IntegrityMalformed, scan.IntegrityFailed, scan.IntegrityStaleAfterCleanup},
			"sourceCompleteness":    []scan.SourceCompleteness{scan.SourceComplete, scan.SourceIncomplete, scan.SourceUnknown},
			"sizeSemantics":         []scan.SizeSemantics{scan.SizeLogical, scan.SizeAllocated, scan.SizeLogicalAndAllocated},
			"authorityCapabilities": []string{"canAnalyze", "canCompareWith", "canDriveDeletion", "canDriveDuplicateHashing", "canDriveResidue", "canExportCompleteData", "canShowAsFresh"},
		}
	case "scan":
		root := "."
		if len(os.Args) > 2 {
			root = os.Args[2]
		}
		session, err := scan.Scan(root, 1, scan.DefaultOptions())
		if err != nil {
			fmt.Fprintf(os.Stderr, "scan %s: %v\n", root, err)
			os.Exit(1)
		}
		payload = session
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q; supported: readiness, version, protocol, schema, scan-contract, scan [root]\n", command)
		os.Exit(2)
	}
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "encode %s: %v\n", command, err)
		os.Exit(1)
	}
	fmt.Println(string(encoded))
}
