package query

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/mikeyphw/dustui/go/internal/scan"
)

func TestPaginationIsGenerationBoundedAndStable(t *testing.T) {
	session := fixtureSession(260)
	seen := map[string]bool{}
	req := PageRequest{Generation: session.Generation, Limit: 37}
	for {
		page, err := Children(context.Background(), session, filepath.Join(session.CanonicalRoot, "dir"), req, Options{SortBy: SortByPath})
		if err != nil {
			t.Fatalf("children page: %v", err)
		}
		if page.BoundedBy != 37 {
			t.Fatalf("limit not preserved: %+v", page)
		}
		for _, item := range page.Items {
			if seen[item.Path] {
				t.Fatalf("pagination duplicated %s", item.Path)
			}
			seen[item.Path] = true
		}
		if page.NextToken == "" {
			break
		}
		req.Token = page.NextToken
	}
	if got, want := len(seen), 260; got != want {
		t.Fatalf("pagination skipped entries: got=%d want=%d", got, want)
	}

	_, err := Children(context.Background(), session, filepath.Join(session.CanonicalRoot, "dir"), PageRequest{Generation: session.Generation + 1}, Options{})
	if !errors.Is(err, ErrStaleGeneration) {
		t.Fatalf("stale generation should be explicit: %v", err)
	}
}

func TestSearchAndCurrentFolderAreDistinct(t *testing.T) {
	session := fixtureSession(0)
	root := session.CanonicalRoot
	session.Entries = append(session.Entries,
		entry(root, "music/song-cache.bin", false, 10, 20, 1),
		entry(root, "music/nested/song-cache.bin", false, 11, 22, 2),
		entry(root, "photos/song-cache.jpg", false, 12, 24, 3),
	)

	search, err := Search(context.Background(), session, "song-cache", PageRequest{Generation: session.Generation, Limit: 100}, Options{SortBy: SortByPath})
	if err != nil {
		t.Fatalf("search: %v", err)
	}
	if got, want := len(search.Items), 3; got != want {
		t.Fatalf("whole-scan search count got=%d want=%d items=%+v", got, want, search.Items)
	}

	local, err := CurrentFolder(context.Background(), session, filepath.Join(root, "music"), PageRequest{Generation: session.Generation, Limit: 100}, Options{SortBy: SortByPath})
	if err != nil {
		t.Fatalf("current folder: %v", err)
	}
	if got, want := len(local.Items), 1; got != want {
		t.Fatalf("current-folder filtering must not recurse or search globally: got=%d want=%d items=%+v", got, want, local.Items)
	}
}

func TestSummaryCompositionHistoryAndLargeFilesUseExplicitSizeSemantics(t *testing.T) {
	session := fixtureSession(0)
	root := session.CanonicalRoot
	session.Entries = []scan.Entry{
		entry(root, "a", true, 0, 0, 1),
		entry(root, "a/small.txt", false, 10, 4096, 2),
		entry(root, "a/big.bin", false, 9000, 12288, 3),
		entry(root, "b/allocated.bin", false, 100, 16384, 4),
	}

	summary := SummaryFor(session)
	if summary.SizeSemantics != scan.SizeLogicalAndAllocated {
		t.Fatalf("summary lost size semantics: %+v", summary)
	}
	if summary.LogicalSizeBytes != 9110 || summary.AllocatedSizeBytes != 32768 {
		t.Fatalf("bad summary totals: %+v", summary)
	}

	logicalLarge, err := LargeFiles(context.Background(), session, 1000, scan.SizeLogical, PageRequest{Generation: session.Generation, Limit: 10})
	if err != nil || len(logicalLarge.Items) != 1 || logicalLarge.Items[0].Name != "big.bin" {
		t.Fatalf("logical large files mismatch page=%+v err=%v", logicalLarge, err)
	}
	allocatedLarge, err := LargeFiles(context.Background(), session, 1000, scan.SizeAllocated, PageRequest{Generation: session.Generation, Limit: 10})
	if err != nil || len(allocatedLarge.Items) != 3 {
		t.Fatalf("allocated large files mismatch page=%+v err=%v", allocatedLarge, err)
	}

	composition := Composition(session)
	if len(composition) != 2 || composition[0].Key != "a" {
		t.Fatalf("composition should be top-level and sorted by logical size: %+v", composition)
	}

	previous := session
	previous.Generation = 6
	previous.CompletedUnixMs = 1
	current := session
	current.Generation = 7
	current.CompletedUnixMs = 2
	points := GrowthSeries([]scan.Session{previous, current})
	if len(points) != 2 || !points[1].ComparableWithPrev || points[1].LogicalSizeBytes != summary.LogicalSizeBytes {
		t.Fatalf("growth series should preserve compatible history: %+v", points)
	}

	changedRoot := current
	changedRoot.CanonicalRoot = filepath.Join(root, "other")
	points = GrowthSeries([]scan.Session{previous, changedRoot})
	if points[1].ComparableWithPrev || len(points[1].Incompatibilities) == 0 {
		t.Fatalf("history compatibility should reject changed semantic root: %+v", points)
	}
}

func TestPackageLookupActivityAndCancellation(t *testing.T) {
	session := fixtureSession(0)
	root := session.CanonicalRoot
	session.Entries = []scan.Entry{
		entry(root, "Android/data/app.one/cache/a.tmp", false, 10, 10, 10),
		entry(root, "Android/data/app.two/cache/b.tmp", false, 20, 20, 30),
		entry(root, "Download/c.tmp", false, 30, 30, 20),
	}

	linked, err := PackageLinkedPaths(context.Background(), session, []PackageLink{{PackageName: "app.one", Paths: []string{filepath.Join(root, "Android/data/app.one")}}}, "app.one", PageRequest{Generation: session.Generation, Limit: 10})
	if err != nil || len(linked.Items) != 1 || linked.Items[0].Name != "a.tmp" {
		t.Fatalf("package lookup mismatch page=%+v err=%v", linked, err)
	}

	activity, err := ActivityPage(context.Background(), session, PageRequest{Generation: session.Generation, Limit: 2})
	if err != nil {
		t.Fatalf("activity page: %v", err)
	}
	if len(activity.Items) != 2 || activity.Items[0].Name != "b.tmp" || activity.NextToken == "" {
		t.Fatalf("activity page should sort by modified desc and remain bounded: %+v", activity)
	}

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err = Search(ctx, fixtureSession(1000), "file", PageRequest{Generation: 7, Limit: 50}, Options{})
	if !errors.Is(err, ErrCancelled) {
		t.Fatalf("cancelled query should fail clearly: %v", err)
	}
}

func fixtureSession(children int) scan.Session {
	root := filepath.Clean("/tmp/dustui-query-fixture")
	entries := make([]scan.Entry, 0, children)
	for i := 0; i < children; i++ {
		entries = append(entries, entry(root, fmt.Sprintf("dir/file-%04d.bin", i), false, int64(i+1), int64((i+1)*2), int64(i)))
	}
	now := time.Now().UnixMilli()
	return scan.Session{
		ID:                       "scan-query-fixture",
		Generation:               7,
		Root:                     root,
		CanonicalRoot:            root,
		BackendIdentity:          scan.BackendIdentity{Name: "dustui-go-scanner", Version: "ov04"},
		OptionsFingerprint:       "query-fixture",
		HiddenPolicy:             scan.HiddenInclude,
		ExclusionPolicy:          scan.ExclusionNone,
		FilesystemBoundaryPolicy: scan.BoundaryStayOnDevice,
		SizeSemanticsUsedForScan: scan.SizeLogicalAndAllocated,
		ObservedEntryCount:       len(entries),
		PersistedEntryCount:      len(entries),
		Integrity:                scan.IntegrityComplete,
		SourceCompleteness:       scan.SourceComplete,
		StartedUnixMs:            now,
		CompletedUnixMs:          now,
		Entries:                  entries,
	}
}

func entry(root, rel string, dir bool, logical, allocated, modified int64) scan.Entry {
	path := filepath.Join(root, filepath.FromSlash(rel))
	return scan.Entry{
		Path:               path,
		ParentPath:         filepath.Dir(path),
		Name:               filepath.Base(path),
		Depth:              len(stringsSplit(filepath.ToSlash(rel), "/")),
		IsDirectory:        dir,
		LogicalSizeBytes:   logical,
		AllocatedSizeBytes: allocated,
		ModifiedUnixMs:     modified,
	}
}

func stringsSplit(value, sep string) []string {
	if value == "" {
		return nil
	}
	parts := []string{}
	for _, part := range strings.Split(value, sep) {
		if part != "" {
			parts = append(parts, part)
		}
	}
	return parts
}
