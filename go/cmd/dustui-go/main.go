package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/privileged"
	"github.com/mikeyphw/dustui/go/internal/query"
	"github.com/mikeyphw/dustui/go/internal/readiness"
	runtimecontract "github.com/mikeyphw/dustui/go/internal/runtime"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/shell"
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
			"ov05Authority":    state.OV05AuthorityMatrix(),
		}

	case "runtime-contract":
		payload = runtimecontract.CurrentSpec()
	case "cleanup-contract", "manifest-contract", "capability-contract":
		payload = cleanup.Contract()
	case "privileged-contract", "root-contract":
		controller, err := privileged.NewController(privileged.DefaultConfig())
		if err != nil {
			fmt.Fprintf(os.Stderr, "privileged contract: %v\n", err)
			os.Exit(1)
		}
		payload = controller.Diagnostics()
	case "shell-contract":
		payload = shell.CurrentContract()
	case "query-contract":
		payload = map[string]any{
			"phase":        "OV07",
			"pagination":   "deterministic_offset_token_bound_to_generation",
			"maxPageLimit": query.MaxPageLimit,
			"countKinds":   []string{query.CountTotal, query.CountPartial, query.CountUnknown},
			"sorts":        []string{query.SortByPath, query.SortByName, query.SortByLogicalSize, query.SortByAllocatedSize, query.SortByModified},
			"features": []string{
				"paged_explorer_children",
				"whole_scan_search",
				"current_folder_filtering_distinct_from_search",
				"large_files",
				"storage_summary",
				"composition_treemap_aggregates",
				"scan_history_growth_series",
				"package_linked_path_lookup",
				"activity_page_preparation",
				"query_cancellation",
				"bounded_response_size",
			},
		}
	case "scan-contract":
		payload = map[string]any{
			"phase":                 "OV07",
			"integrityStates":       []scan.IntegrityState{scan.IntegrityComplete, scan.IntegrityPartial, scan.IntegrityTruncated, scan.IntegrityMalformed, scan.IntegrityFailed, scan.IntegrityStaleAfterCleanup},
			"sourceCompleteness":    []scan.SourceCompleteness{scan.SourceComplete, scan.SourceIncomplete, scan.SourceUnknown},
			"sizeSemantics":         []scan.SizeSemantics{scan.SizeLogical, scan.SizeAllocated, scan.SizeLogicalAndAllocated},
			"authorityCapabilities": []string{"canAnalyze", "canCompareWith", "canDriveDeletion", "canDriveDuplicateHashing", "canDriveResidue", "canExportCompleteData", "canShowAsFresh"},
			"readAuthority":         "go-sqlite",
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
		fmt.Fprintf(os.Stderr, "unknown command %q; supported: readiness, version, protocol, schema, runtime-contract, cleanup-contract, privileged-contract, shell-contract, query-contract, scan-contract, scan [root]\n", command)
		os.Exit(2)
	}
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "encode %s: %v\n", command, err)
		os.Exit(1)
	}
	fmt.Println(string(encoded))
}
