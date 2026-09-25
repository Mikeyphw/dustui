package tui

import (
	"bytes"
	"context"
	"strings"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/shell"
)

func TestGoTUISmokePreservesRoutesAndResponsiveLayout(t *testing.T) {
	smoke := Smoke()
	if smoke.Phase != "OV13" || !smoke.SharedEngineSemantics || !smoke.RunLoop {
		t.Fatalf("bad smoke: %+v", smoke)
	}
	if len(smoke.Routes) != 6 || len(smoke.InteractiveWorkflows) < 7 || len(smoke.ForwardedCommands) < 9 {
		t.Fatalf("incomplete smoke: %+v", smoke)
	}
	compact, err := Render(shell.RouteClean, 80, shell.StateBlocked)
	if err != nil {
		t.Fatal(err)
	}
	wide, err := Render(shell.RouteClean, 120, shell.StateBlocked)
	if err != nil {
		t.Fatal(err)
	}
	if compact.Route != wide.Route || compact.Layout != LayoutCompact || wide.Layout != LayoutWide {
		t.Fatalf("route identity must survive layout changes: compact=%+v wide=%+v", compact, wide)
	}
}

func TestAUD_FIX07InteractiveRunLoopRoutesAndDelegatesHostActions(t *testing.T) {
	var calls [][]string
	exec := func(_ context.Context, args []string) (string, error) {
		calls = append(calls, append([]string{}, args...))
		return `{"ok":true,"command":"` + args[0] + `"}`, nil
	}
	input := strings.NewReader("files\nsearch --query cache\nroute system\ndiagnostics\nquit\n")
	var output bytes.Buffer
	result, err := Run(context.Background(), input, &output, Options{Width: 120, InitialRoot: "/storage", Executor: exec})
	if err != nil {
		t.Fatal(err)
	}
	if !result.Quit || result.FinalRoute != shell.RouteSystem || result.ForwardedCount != 2 {
		t.Fatalf("unexpected result: %+v", result)
	}
	if len(calls) != 2 || calls[0][0] != "search" || calls[1][0] != "diagnostics" {
		t.Fatalf("host delegation mismatch: %#v", calls)
	}
	text := output.String()
	for _, want := range []string{"DustUI Go TUI", "[files · wide]", "[system · wide]", `"command":"search"`, `"command":"diagnostics"`} {
		if !strings.Contains(text, want) {
			t.Fatalf("output missing %q:\n%s", want, text)
		}
	}
}

func TestAUD_FIX07DefaultScanUsesInitialRoot(t *testing.T) {
	var got []string
	exec := func(_ context.Context, args []string) (string, error) {
		got = append([]string{}, args...)
		return `{"ok":true}`, nil
	}
	var output bytes.Buffer
	_, err := Run(context.Background(), strings.NewReader("scan\nquit\n"), &output, Options{InitialRoot: "/sdcard", Executor: exec})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Join(got, " ") != "scan /sdcard" {
		t.Fatalf("scan did not use initial root: %#v", got)
	}
}
