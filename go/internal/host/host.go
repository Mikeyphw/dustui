package host

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/diagnostics"
	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/readiness"
	"github.com/mikeyphw/dustui/go/internal/rootworker"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/state"
	"github.com/mikeyphw/dustui/go/internal/tui"
)

// Streams makes the host surface testable and keeps stdout machine-readable for
// CLI commands. The interactive TUI deliberately owns a human-readable stream.
type Streams struct {
	In  io.Reader
	Out io.Writer
	Err io.Writer
}

func normalizeStreams(s Streams) Streams {
	if s.In == nil {
		s.In = strings.NewReader("")
	}
	if s.Out == nil {
		s.Out = io.Discard
	}
	if s.Err == nil {
		s.Err = io.Discard
	}
	return s
}

var hostCommands = map[string]bool{
	"help": true, "readiness": true, "version": true, "protocol": true,
	"schema": true, "product": true, "product-contract": true, "cli-contract": true,
	"scan": true, "query": true, "search": true, "activity": true,
	"cleanup-review": true, "cleanup-execute": true, "diagnostics": true,
	"support-export": true, "inspect": true, "tui": true,
}

func Handles(command string) bool { return hostCommands[strings.TrimSpace(command)] }

func Commands() []string {
	return []string{
		"scan", "query", "search", "activity", "cleanup-review", "cleanup-execute",
		"diagnostics", "support-export", "readiness", "schema", "product", "inspect", "tui",
	}
}

func DefaultDatabasePath() string {
	if value := strings.TrimSpace(os.Getenv("DUSTUI_DB")); value != "" {
		return value
	}
	if value := strings.TrimSpace(os.Getenv("XDG_STATE_HOME")); value != "" {
		return filepath.Join(value, "dustui", "dustui.db")
	}
	home, err := os.UserHomeDir()
	if err != nil || strings.TrimSpace(home) == "" {
		return filepath.Join(".", ".dustui", "dustui.db")
	}
	return filepath.Join(home, ".local", "state", "dustui", "dustui.db")
}

func RunAlias(ctx context.Context, alias string, args []string, streams Streams) int {
	if len(args) > 0 && Handles(args[0]) {
		return Run(ctx, args, streams)
	}
	// Historical dust-tui/dust-space invocation treated the first positional
	// argument as the initial scan root. Preserve that UX while making the Go TUI
	// the implementation behind both compatibility commands.
	forwarded := []string{"tui"}
	if len(args) > 0 && !strings.HasPrefix(args[0], "-") {
		forwarded = append(forwarded, "--root", args[0])
		forwarded = append(forwarded, args[1:]...)
	} else {
		forwarded = append(forwarded, args...)
	}
	_ = alias // both aliases intentionally converge on one Go host.
	return Run(ctx, forwarded, streams)
}

func Run(ctx context.Context, args []string, streams Streams) int {
	streams = normalizeStreams(streams)
	if len(args) == 0 {
		args = []string{"tui"}
	}
	command := args[0]
	if !Handles(command) {
		writeError(streams.Err, command, fmt.Errorf("unknown host command %q", command))
		return 2
	}
	var err error
	switch command {
	case "help":
		err = writeJSON(streams.Out, map[string]any{
			"ok":                      true,
			"surface":                 "go-cli",
			"default":                 "tui",
			"commands":                Commands(),
			"machineReadable":         true,
			"pythonTextualProduction": false,
		})
	case "readiness", "version", "protocol":
		err = writeJSON(streams.Out, readiness.Current())
	case "schema":
		err = writeJSON(streams.Out, schemaPayload())
	case "product", "product-contract", "cli-contract":
		err = writeJSON(streams.Out, product.CurrentContract())
	case "scan":
		err = runScan(ctx, args[1:], streams)
	case "query":
		err = runQuery(ctx, args[1:], streams)
	case "search":
		err = runSearch(ctx, args[1:], streams)
	case "activity":
		err = runActivity(ctx, args[1:], streams)
	case "cleanup-review":
		err = runCleanupReview(ctx, args[1:], streams)
	case "cleanup-execute":
		err = runCleanupExecute(ctx, args[1:], streams)
	case "diagnostics":
		err = runDiagnostics(ctx, args[1:], streams)
	case "support-export":
		err = runSupportExport(ctx, args[1:], streams)
	case "inspect":
		err = runInspect(args[1:], streams)
	case "tui":
		err = runTUI(ctx, args[1:], streams)
	}
	if err != nil {
		writeError(streams.Err, command, err)
		return 1
	}
	return 0
}

func schemaPayload() map[string]any {
	return map[string]any{
		"schemaVersion":    state.SchemaVersion,
		"requiredTables":   state.RequiredTables,
		"createStatements": state.CreateStatements(),
		"roomV3ImportPlan": state.RoomV3ImportPlan(),
		"ov05Authority":    state.OV05AuthorityMatrix(),
	}
}

func writeJSON(w io.Writer, value any) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(value)
}

func writeError(w io.Writer, command string, err error) {
	_ = writeJSON(w, map[string]any{"ok": false, "command": command, "error": err.Error()})
}

func newFlagSet(name string) *flag.FlagSet {
	fs := flag.NewFlagSet(name, flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	return fs
}

func openStore(path string) (*state.SQLiteStore, error) {
	if strings.TrimSpace(path) == "" {
		path = DefaultDatabasePath()
	}
	return state.OpenSQLiteStore(path)
}

func scanGeneration(now time.Time) int64 {
	g := now.UnixMilli()
	if g <= 0 {
		return 1
	}
	return g
}

func runScan(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("scan")
	db := fs.String("db", "", "SQLite authority path")
	generation := fs.Int64("generation", 0, "positive generation")
	maxEntries := fs.Int("max-entries", 0, "bounded entry limit; 0 is unbounded")
	maxDepth := fs.Int("max-depth", -1, "maximum traversal depth; -1 is unbounded")
	filesOnly := fs.Bool("files-only", false, "emit files only")
	dirsOnly := fs.Bool("dirs-only", false, "emit directories only")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *filesOnly && *dirsOnly {
		return errors.New("scan cannot combine --files-only and --dirs-only")
	}
	root := "."
	if fs.NArg() > 0 {
		root = fs.Arg(0)
	}
	gen := *generation
	if gen <= 0 {
		gen = scanGeneration(time.Now())
	}
	opts := scan.DefaultOptions()
	opts.MaxEntries = *maxEntries
	opts.MaxDepth = *maxDepth
	if *filesOnly {
		opts.EntryMode = "FILES"
	}
	if *dirsOnly {
		opts.EntryMode = "DIRECTORIES"
	}
	session, err := scan.ScanContext(ctx, root, gen, opts)
	if err != nil && !errors.Is(err, context.Canceled) {
		return err
	}
	persisted := false
	if strings.TrimSpace(*db) != "" {
		store, openErr := openStore(*db)
		if openErr != nil {
			return openErr
		}
		defer store.Close()
		if persistErr := persistScan(ctx, store, session, opts); persistErr != nil {
			return persistErr
		}
		persisted = true
	}
	return writeJSON(streams.Out, map[string]any{"ok": err == nil, "persisted": persisted, "session": session})
}

func persistScan(ctx context.Context, store *state.SQLiteStore, session scan.Session, opts scan.Options) error {
	optionsJSON, err := json.Marshal(opts.Normalized())
	if err != nil {
		return err
	}
	completed := session.CompletedUnixMs
	status := "COMPLETED"
	if session.Integrity == scan.IntegrityFailed || session.Integrity == scan.IntegrityMalformed {
		status = "FAILED"
	}
	androidSession := state.AndroidScanSession{
		ID: session.ID, RootPath: session.CanonicalRoot, OptionsJSON: string(optionsJSON),
		Status: status, Backend: session.BackendIdentity.Name, StartedAt: session.StartedUnixMs,
		CompletedAt: &completed, ScannedItems: int64(session.ObservedEntryCount),
		WarningCount: int64(len(session.Problems)), CurrentPath: session.CanonicalRoot,
		Generation: session.Generation, ObservedEntryCount: int64(session.ObservedEntryCount),
		PersistedEntryCount: int64(session.PersistedEntryCount), MalformedEventCount: int64(session.MalformedRecordCount),
		Truncated: session.Truncated, IntegrityStatus: strings.ToUpper(string(session.Integrity)),
	}
	entries := make([]state.AndroidScanEntry, 0, len(session.Entries))
	var totalBytes int64
	for _, entry := range session.Entries {
		path := entry.Path
		if !filepath.IsAbs(path) {
			path = filepath.Join(session.CanonicalRoot, filepath.FromSlash(path))
		}
		parent := entry.ParentPath
		if strings.TrimSpace(parent) == "" {
			parent = session.CanonicalRoot
		} else if !filepath.IsAbs(parent) {
			parent = filepath.Join(session.CanonicalRoot, filepath.FromSlash(parent))
		}
		if !entry.IsDirectory {
			totalBytes += entry.LogicalSizeBytes
		}
		entries = append(entries, state.AndroidScanEntry{
			ScanID: session.ID, Path: filepath.Clean(path), ParentPath: filepath.Clean(parent), Name: entry.Name,
			SizeBytes: entry.LogicalSizeBytes, AllocatedBytes: entry.AllocatedSizeBytes,
			IsDirectory: entry.IsDirectory, Depth: entry.Depth, ModifiedAt: entry.ModifiedUnixMs, FileCount: entry.FileCount,
		})
	}
	androidSession.ScannedBytes = totalBytes
	androidSession.TotalBytes = totalBytes
	if err := store.UpsertAndroidScanSession(ctx, androidSession); err != nil {
		return err
	}
	if err := store.DeleteAndroidScanEntries(ctx, session.ID); err != nil {
		return err
	}
	return store.UpsertAndroidScanEntries(ctx, entries)
}

func latestSession(ctx context.Context, store *state.SQLiteStore, requested string) (*state.AndroidScanSession, error) {
	if strings.TrimSpace(requested) != "" {
		return store.AndroidScanSessionByID(ctx, requested)
	}
	return store.LatestAndroidScanSession(ctx)
}

func runQuery(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("query")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	scanID := fs.String("scan", "", "scan id; latest when omitted")
	parent := fs.String("parent", "", "canonical parent path")
	term := fs.String("query", "", "bounded query term")
	entryType := fs.String("type", "ALL", "ALL|FILES|DIRECTORIES")
	minBytes := fs.Int64("min-bytes", 0, "minimum logical size")
	sortBy := fs.String("sort", "SIZE", "PATH|NAME|SIZE|ALLOCATED|MODIFIED")
	desc := fs.Bool("desc", true, "descending order")
	offset := fs.Int("offset", 0, "page offset")
	limit := fs.Int("limit", 100, "page limit (max 500)")
	if err := fs.Parse(args); err != nil {
		return err
	}
	store, err := openStore(*db)
	if err != nil {
		return err
	}
	defer store.Close()
	session, err := latestSession(ctx, store, *scanID)
	if err != nil {
		return err
	}
	if session == nil {
		return errors.New("query requires a persisted scan")
	}
	var parentPtr *string
	if value := strings.TrimSpace(*parent); value != "" {
		parentPtr = &value
	}
	page, err := store.AndroidBrowsePage(ctx, session.ID, session.Generation, state.AndroidBrowseRequest{
		ParentPath: parentPtr, Query: *term, EntryType: *entryType, MinBytes: *minBytes,
		Sort: *sortBy, Descending: *desc, Offset: *offset, Limit: *limit,
	})
	if err != nil {
		return err
	}
	return writeJSON(streams.Out, map[string]any{"ok": true, "page": page})
}

func runSearch(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("search")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	scanID := fs.String("scan", "", "scan id; latest when omitted")
	term := fs.String("query", "", "search expression")
	entryType := fs.String("type", "ALL", "ALL|FILES|DIRECTORIES")
	minBytes := fs.Int64("min-bytes", 0, "minimum logical size")
	category := fs.String("category", "", "optional category")
	packageName := fs.String("package", "", "optional Android package")
	extension := fs.String("extension", "", "optional extension")
	offset := fs.Int("offset", 0, "page offset")
	limit := fs.Int("limit", 100, "page limit")
	if err := fs.Parse(args); err != nil {
		return err
	}
	store, err := openStore(*db)
	if err != nil {
		return err
	}
	defer store.Close()
	session, err := latestSession(ctx, store, *scanID)
	if err != nil {
		return err
	}
	if session == nil {
		return errors.New("search requires a persisted scan")
	}
	request := state.AndroidSearchRequest{Query: *term, EntryType: *entryType, MinBytes: *minBytes, Offset: *offset, Limit: *limit}
	if value := strings.TrimSpace(*category); value != "" {
		request.Category = &value
	}
	if value := strings.TrimSpace(*packageName); value != "" {
		request.PackageName = &value
	}
	if value := strings.TrimSpace(*extension); value != "" {
		request.Extension = &value
	}
	page, err := store.SearchAndroidScanEntriesPage(ctx, session.ID, session.Generation, request)
	if err != nil {
		return err
	}
	return writeJSON(streams.Out, map[string]any{"ok": true, "page": page})
}

func runActivity(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("activity")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	operationID := fs.String("operation", "", "operation id")
	limit := fs.Int("limit", 100, "bounded activity count")
	if err := fs.Parse(args); err != nil {
		return err
	}
	store, err := openStore(*db)
	if err != nil {
		return err
	}
	defer store.Close()
	if strings.TrimSpace(*operationID) != "" {
		run, err := store.ActivityRunByID(ctx, *operationID)
		if err != nil {
			return err
		}
		return writeJSON(streams.Out, map[string]any{"ok": true, "run": run})
	}
	runs, err := store.ActivityRuns(ctx, *limit)
	if err != nil {
		return err
	}
	return writeJSON(streams.Out, map[string]any{"ok": true, "runs": runs})
}

func inputReader(path string, fallback io.Reader) (io.Reader, io.Closer, error) {
	if strings.TrimSpace(path) == "" || path == "-" {
		return fallback, nil, nil
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, nil, err
	}
	return f, f, nil
}

func runCleanupReview(_ context.Context, args []string, streams Streams) error {
	fs := newFlagSet("cleanup-review")
	input := fs.String("input", "", "review request JSON file; stdin when omitted")
	if err := fs.Parse(args); err != nil {
		return err
	}
	r, closer, err := inputReader(*input, streams.In)
	if err != nil {
		return err
	}
	if closer != nil {
		defer closer.Close()
	}
	return rootworker.ReviewCleanup(r, streams.Out)
}

func runCleanupExecute(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("cleanup-execute")
	input := fs.String("input", "", "sealed execution request JSON file; stdin when omitted")
	if err := fs.Parse(args); err != nil {
		return err
	}
	r, closer, err := inputReader(*input, streams.In)
	if err != nil {
		return err
	}
	if closer != nil {
		defer closer.Close()
	}
	return rootworker.ExecuteCleanup(ctx, r, streams.Out)
}

func runDiagnostics(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("diagnostics")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	limit := fs.Int("limit", 100, "bounded event count")
	if err := fs.Parse(args); err != nil {
		return err
	}
	store, err := openStore(*db)
	if err != nil {
		return err
	}
	defer store.Close()
	snapshot, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		return err
	}
	events, err := store.DiagnosticEvents(ctx, *limit)
	if err != nil {
		return err
	}
	return writeJSON(streams.Out, map[string]any{"ok": true, "snapshot": snapshot, "events": events})
}

func runSupportExport(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("support-export")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	privacy := fs.String("privacy", string(diagnostics.PrivacyMinimal), "minimal|detailed")
	requestID := fs.String("request-id", "", "durable request identity")
	if err := fs.Parse(args); err != nil {
		return err
	}
	mode := diagnostics.PrivacyMode(strings.TrimSpace(*privacy))
	if mode != diagnostics.PrivacyMinimal && mode != diagnostics.PrivacyDetailed {
		return errors.New("support-export --privacy must be minimal or detailed")
	}
	store, err := openStore(*db)
	if err != nil {
		return err
	}
	defer store.Close()
	snapshot, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		return err
	}
	rid := strings.TrimSpace(*requestID)
	if rid == "" {
		rid = "host-support-export:" + strconv.FormatInt(time.Now().UnixNano(), 10)
	}
	admission, err := store.AdmitAutomation(ctx, rid, "ui", "support-export", 1, string(mode)+"|"+strconv.FormatInt(snapshot.GeneratedAtMS, 10))
	if err != nil {
		return err
	}
	if _, err = store.AdvanceAutomation(ctx, admission.OperationID, "running", "support_export_started", "Go host support evidence export started"); err != nil {
		return err
	}
	events, err := store.DiagnosticEvents(ctx, diagnostics.DefaultDiagnosticPolicy().MaxEventsPerSession)
	if err != nil {
		_, _ = store.AdvanceAutomation(ctx, admission.OperationID, "failure", "diagnostic_read_failed", err.Error())
		return err
	}
	bundle := diagnostics.NewSupportBundle(mode, snapshot.EvidenceGenerations, snapshot.Problems, events)
	bundle.OperationID = admission.OperationID
	if err = store.PersistSupportBundle(ctx, admission.OperationID, bundle); err != nil {
		_, _ = store.AdvanceAutomation(ctx, admission.OperationID, "failure", "support_bundle_persist_failed", err.Error())
		return err
	}
	terminal, err := store.AdvanceAutomation(ctx, admission.OperationID, "success", "support_export_sealed", "Go host support evidence export sealed")
	if err != nil {
		return err
	}
	return writeJSON(streams.Out, map[string]any{"ok": true, "bundle": bundle, "operation": terminal})
}

func runInspect(args []string, streams Streams) error {
	if len(args) != 1 {
		return errors.New("inspect requires exactly one of readiness|schema|product|cli|tui")
	}
	switch args[0] {
	case "readiness":
		return writeJSON(streams.Out, readiness.Current())
	case "schema":
		return writeJSON(streams.Out, schemaPayload())
	case "product", "cli":
		return writeJSON(streams.Out, product.CurrentContract())
	case "tui":
		return writeJSON(streams.Out, tui.Smoke())
	default:
		return fmt.Errorf("unknown inspection %q", args[0])
	}
}

func injectDatabase(args []string, db string) []string {
	if len(args) == 0 || strings.TrimSpace(db) == "" {
		return args
	}
	switch args[0] {
	case "query", "search", "activity", "diagnostics", "support-export":
		for _, arg := range args[1:] {
			if arg == "--db" || strings.HasPrefix(arg, "--db=") {
				return args
			}
		}
		out := make([]string, 0, len(args)+2)
		out = append(out, args[0], "--db", db)
		out = append(out, args[1:]...)
		return out
	case "scan":
		for _, arg := range args[1:] {
			if arg == "--db" || strings.HasPrefix(arg, "--db=") {
				return args
			}
		}
		out := []string{"scan", "--db", db}
		out = append(out, args[1:]...)
		return out
	default:
		return args
	}
}

func runTUI(ctx context.Context, args []string, streams Streams) error {
	fs := newFlagSet("tui")
	db := fs.String("db", DefaultDatabasePath(), "SQLite authority path")
	root := fs.String("root", ".", "initial scan root")
	width := fs.Int("width", 100, "terminal width used for layout")
	if err := fs.Parse(args); err != nil {
		return err
	}
	executor := func(execCtx context.Context, commandArgs []string) (string, error) {
		if len(commandArgs) == 0 || commandArgs[0] == "tui" {
			return "", errors.New("nested tui execution is not allowed")
		}
		commandArgs = injectDatabase(commandArgs, *db)
		var out bytes.Buffer
		var errOut bytes.Buffer
		code := Run(execCtx, commandArgs, Streams{In: strings.NewReader(""), Out: &out, Err: &errOut})
		if code != 0 {
			message := strings.TrimSpace(errOut.String())
			if message == "" {
				message = fmt.Sprintf("host command exited %d", code)
			}
			return message, errors.New(message)
		}
		return strings.TrimSpace(out.String()), nil
	}
	_, err := tui.Run(ctx, streams.In, streams.Out, tui.Options{Width: *width, InitialRoot: *root, Executor: executor})
	return err
}
