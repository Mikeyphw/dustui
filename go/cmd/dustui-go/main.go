package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/mikeyphw/dustui/go/internal/readiness"
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
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q; supported: readiness, version, protocol, schema\n", command)
		os.Exit(2)
	}
	encoded, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "encode %s: %v\n", command, err)
		os.Exit(1)
	}
	fmt.Println(string(encoded))
}
