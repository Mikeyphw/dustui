package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/mikeyphw/dustui/go/internal/automation"
	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/diagnostics"
	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
	"github.com/mikeyphw/dustui/go/internal/privileged"
	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/query"
	"github.com/mikeyphw/dustui/go/internal/readiness"
	runtimecontract "github.com/mikeyphw/dustui/go/internal/runtime"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/shell"
	"github.com/mikeyphw/dustui/go/internal/state"
	"github.com/mikeyphw/dustui/go/internal/tui"
)

func main() {
	command := "readiness"
	if len(os.Args) > 1 {
		command = os.Args[1]
	}

	if command == "serve" {
		if err := serve(); err != nil {
			fmt.Fprintf(os.Stderr, "serve: %v\n", err)
			os.Exit(1)
		}
		return
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
	case "product-contract", "cli-contract", "android-cutover-contract", "gate-d-contract":
		payload = product.CurrentContract()
	case "tui-contract", "tui-smoke":
		payload = tui.Smoke()
	case "diagnostics-contract", "evidence-contract", "problem-contract", "privacy-contract", "support-bundle-contract":
		payload = diagnostics.Contract()
	case "cleanup-contract", "manifest-contract", "capability-contract", "mutation-contract":
		payload = cleanup.Contract()
	case "cleanup-intelligence-contract", "intelligence-contract", "duplicates-contract", "residue-contract", "rules-contract":
		payload = intelligence.Contract()
	case "automation-contract", "activity-contract", "workmanager-contract", "tasker-contract", "notification-contract":
		payload = automation.Contract()
	case "package-storage-contract", "package-contract", "storage-contract":
		payload = packageevidence.Contract()
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
			"phase":        "OV13",
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
			"phase":                 "OV13",
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
		fmt.Fprintf(os.Stderr, "unknown command %q; supported: readiness, version, protocol, schema, runtime-contract, product-contract, cli-contract, tui-contract, android-cutover-contract, diagnostics-contract, cleanup-contract, mutation-contract, cleanup-intelligence-contract, package-storage-contract, privileged-contract, shell-contract, query-contract, scan-contract, scan [root]\n", command)
		os.Exit(2)
	}
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "encode %s: %v\n", command, err)
		os.Exit(1)
	}
	fmt.Println(string(encoded))
}

type runtimeFrame struct {
	Protocol   string          `json:"protocol"`
	Type       string          `json:"type"`
	RequestID  string          `json:"requestId"`
	Sequence   int64           `json:"sequence"`
	Generation int64           `json:"generation"`
	Payload    json.RawMessage `json:"payload"`
}

func serve() error {
	writer := bufio.NewWriter(os.Stdout)
	ready, err := json.Marshal(readiness.Current())
	if err != nil {
		return err
	}
	if _, err := writer.Write(append(ready, '\n')); err != nil {
		return err
	}
	if err := writer.Flush(); err != nil {
		return err
	}

	scanner := bufio.NewScanner(os.Stdin)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, runtimecontract.MaxFrameBytes)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		var frame runtimeFrame
		if err := json.Unmarshal([]byte(line), &frame); err != nil {
			return fmt.Errorf("decode frame: %w", err)
		}
		if frame.Protocol != runtimecontract.SessionProtocolName {
			return fmt.Errorf("unsupported session protocol %q", frame.Protocol)
		}
		if frame.RequestID == "" || frame.Type == "" || frame.Sequence <= 0 || frame.Generation <= 0 {
			return fmt.Errorf("frame identity required")
		}
		encoded, err := json.Marshal(frame)
		if err != nil {
			return err
		}
		if len(encoded) > runtimecontract.MaxFrameBytes {
			return fmt.Errorf("response exceeds bounded stream")
		}
		if _, err := writer.Write(append(encoded, '\n')); err != nil {
			return err
		}
		if err := writer.Flush(); err != nil {
			return err
		}
	}
	return scanner.Err()
}
