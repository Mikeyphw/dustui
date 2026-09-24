package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/mikeyphw/dustui/go/internal/readiness"
)

func main() {
	command := "readiness"
	if len(os.Args) > 1 {
		command = os.Args[1]
	}
	if command != "readiness" && command != "version" {
		fmt.Fprintf(os.Stderr, "unknown command %q; supported: readiness, version\n", command)
		os.Exit(2)
	}
	payload, err := json.MarshalIndent(readiness.Current(), "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "encode readiness: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(string(payload))
}
