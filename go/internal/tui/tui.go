package tui

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"strings"

	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/shell"
)

type LayoutMode string

const (
	LayoutCompact LayoutMode = "compact"
	LayoutWide    LayoutMode = "wide"
)

type Screen struct {
	Route      shell.RouteID      `json:"route"`
	Layout     LayoutMode         `json:"layout"`
	State      shell.ContentState `json:"state"`
	Actions    []shell.ActionGate `json:"actions"`
	StatusLine string             `json:"statusLine"`
}

type SmokeResult struct {
	Phase                 string             `json:"phase"`
	Surface               product.Surface    `json:"surface"`
	Layouts               []LayoutMode       `json:"layouts"`
	Routes                []shell.RouteID    `json:"routes"`
	SharedEngineSemantics bool               `json:"sharedEngineSemantics"`
	InteractiveWorkflows  []product.Workflow `json:"interactiveWorkflows"`
	RunLoop               bool               `json:"runLoop"`
	ForwardedCommands     []string           `json:"forwardedCommands"`
}

type Executor func(context.Context, []string) (string, error)

type Options struct {
	Width        int
	InitialRoute shell.RouteID
	InitialRoot  string
	Executor     Executor
}

type RunResult struct {
	FinalRoute       shell.RouteID `json:"finalRoute"`
	InteractionCount int           `json:"interactionCount"`
	ForwardedCount   int           `json:"forwardedCount"`
	Quit             bool          `json:"quit"`
}

func LayoutFor(width int) LayoutMode {
	if width >= 95 {
		return LayoutWide
	}
	return LayoutCompact
}

func Render(route shell.RouteID, width int, state shell.ContentState) (Screen, error) {
	if _, err := shell.Restore(route, "", 1); err != nil {
		return Screen{}, err
	}
	if state == "" {
		state = shell.StateContent
	}
	if state != shell.StateLoading && state != shell.StateEmpty && state != shell.StateBlocked && state != shell.StateError && state != shell.StateContent {
		return Screen{}, fmt.Errorf("unknown_state:%s", state)
	}
	return Screen{
		Route: route, Layout: LayoutFor(width), State: state,
		Actions:    []shell.ActionGate{shell.GateAllowed, shell.GateNeedsFreshScan, shell.GateNeedsCapability},
		StatusLine: "Go TUI · shared Go domain/runtime authority",
	}, nil
}

func Smoke() SmokeResult {
	contract := product.CurrentContract()
	routes := append([]shell.RouteID{}, contract.Routes...)
	workflows := []product.Workflow{}
	for _, surface := range contract.Surfaces {
		if surface.Name == product.SurfaceTUI {
			workflows = append(workflows, surface.SupportedWorkflows...)
			break
		}
	}
	return SmokeResult{
		Phase: product.Phase, Surface: product.SurfaceTUI,
		Layouts: []LayoutMode{LayoutCompact, LayoutWide}, Routes: routes,
		SharedEngineSemantics: true, InteractiveWorkflows: workflows, RunLoop: true,
		ForwardedCommands: []string{"scan", "query", "search", "activity", "cleanup-review", "cleanup-execute", "diagnostics", "support-export", "inspect"},
	}
}

func validRoute(raw string) (shell.RouteID, bool) {
	id := shell.RouteID(strings.ToLower(strings.TrimSpace(raw)))
	for _, route := range shell.CurrentContract().Routes {
		if route == id {
			return id, true
		}
	}
	return "", false
}

func routeForShortcut(command string) (shell.RouteID, bool) {
	switch command {
	case "overview", "home":
		return shell.RouteOverview, true
	case "files":
		return shell.RouteFiles, true
	case "clean":
		return shell.RouteClean, true
	case "apps":
		return shell.RouteApps, true
	case "activity":
		return shell.RouteActivity, true
	case "system":
		return shell.RouteSystem, true
	default:
		return "", false
	}
}

func hostCommand(command string) bool {
	switch command {
	case "scan", "query", "search", "activity", "cleanup-review", "cleanup-execute", "diagnostics", "support-export", "inspect":
		return true
	default:
		return false
	}
}

func renderPrompt(out io.Writer, route shell.RouteID, width int) error {
	screen, err := Render(route, width, shell.StateContent)
	if err != nil {
		return err
	}
	_, err = fmt.Fprintf(out, "\n[%s · %s] %s\ndustui> ", screen.Route, screen.Layout, screen.StatusLine)
	return err
}

func printHelp(out io.Writer) {
	fmt.Fprintln(out, "Routes: overview files clean apps activity system")
	fmt.Fprintln(out, "Host actions: scan query search activity cleanup-review cleanup-execute diagnostics support-export inspect")
	fmt.Fprintln(out, "Use `route <name>` to change semantic route, `run <host-command ...>` for explicit dispatch, and `quit` to exit.")
}

// Run is the production Go TUI interaction loop. It deliberately delegates all
// data-bearing actions to the supplied Go host executor; the TUI owns navigation
// and presentation only, so it cannot grow a second domain implementation.
func Run(ctx context.Context, in io.Reader, out io.Writer, options Options) (RunResult, error) {
	if in == nil || out == nil {
		return RunResult{}, fmt.Errorf("tui streams required")
	}
	if options.Executor == nil {
		return RunResult{}, fmt.Errorf("tui host executor required")
	}
	width := options.Width
	if width <= 0 {
		width = 100
	}
	route := options.InitialRoute
	if route == "" {
		route = shell.RouteOverview
	}
	if _, ok := validRoute(string(route)); !ok {
		return RunResult{}, fmt.Errorf("unknown initial route %q", route)
	}
	root := strings.TrimSpace(options.InitialRoot)
	if root == "" {
		root = "."
	}
	result := RunResult{FinalRoute: route}
	fmt.Fprintln(out, "DustUI Go TUI")
	fmt.Fprintln(out, "Type `help` for routes and host actions.")
	if err := renderPrompt(out, route, width); err != nil {
		return result, err
	}

	scanner := bufio.NewScanner(in)
	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return result, ctx.Err()
		default:
		}
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			if err := renderPrompt(out, route, width); err != nil {
				return result, err
			}
			continue
		}
		fields := strings.Fields(line)
		command := strings.ToLower(fields[0])
		result.InteractionCount++
		switch command {
		case "q", "quit", "exit":
			result.FinalRoute = route
			result.Quit = true
			fmt.Fprintln(out, "bye")
			return result, nil
		case "help", "?":
			printHelp(out)
		case "route":
			if len(fields) != 2 {
				fmt.Fprintln(out, "route requires one of: overview files clean apps activity system")
				break
			}
			next, ok := validRoute(fields[1])
			if !ok {
				fmt.Fprintf(out, "unknown route: %s\n", fields[1])
				break
			}
			route = next
			result.FinalRoute = route
		case "run":
			if len(fields) < 2 || !hostCommand(fields[1]) || fields[1] == "tui" {
				fmt.Fprintln(out, "run requires a supported non-TUI host command")
				break
			}
			text, err := options.Executor(ctx, fields[1:])
			result.ForwardedCount++
			if err != nil {
				fmt.Fprintf(out, "error: %v\n", err)
			} else if strings.TrimSpace(text) != "" {
				fmt.Fprintln(out, text)
			}
		default:
			if next, ok := routeForShortcut(command); ok && len(fields) == 1 {
				route = next
				result.FinalRoute = route
				break
			}
			if hostCommand(command) {
				forwarded := append([]string{}, fields...)
				if command == "scan" && len(fields) == 1 {
					forwarded = append(forwarded, root)
				}
				text, err := options.Executor(ctx, forwarded)
				result.ForwardedCount++
				if err != nil {
					fmt.Fprintf(out, "error: %v\n", err)
				} else if strings.TrimSpace(text) != "" {
					fmt.Fprintln(out, text)
				}
				break
			}
			fmt.Fprintf(out, "unknown command: %s\n", command)
		}
		if err := renderPrompt(out, route, width); err != nil {
			return result, err
		}
	}
	if err := scanner.Err(); err != nil {
		return result, err
	}
	result.FinalRoute = route
	return result, nil
}
