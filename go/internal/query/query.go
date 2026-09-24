package query

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"sort"
	"strings"

	"github.com/mikeyphw/dustui/go/internal/scan"
)

const (
	MaxPageLimit        = 500
	DefaultPageLimit    = 100
	CountTotal          = "total"
	CountPartial        = "partial"
	CountUnknown        = "unknown"
	SortByPath          = "path"
	SortByName          = "name"
	SortByLogicalSize   = "logical_size"
	SortByAllocatedSize = "allocated_size"
	SortByModified      = "modified"
)

var (
	ErrStaleGeneration  = errors.New("stale_generation")
	ErrInvalidPageToken = errors.New("invalid_page_token")
	ErrCancelled        = errors.New("query_cancelled")
)

type PageRequest struct {
	Generation int64  `json:"generation"`
	Token      string `json:"token"`
	Limit      int    `json:"limit"`
}

func (r PageRequest) BoundedLimit(max int) int {
	if max <= 0 || max > MaxPageLimit {
		max = MaxPageLimit
	}
	if r.Limit <= 0 {
		if DefaultPageLimit < max {
			return DefaultPageLimit
		}
		return max
	}
	if r.Limit > max {
		return max
	}
	return r.Limit
}

type Filter struct {
	DirectoriesOnly  bool  `json:"directoriesOnly"`
	FilesOnly        bool  `json:"filesOnly"`
	MinLogicalSize   int64 `json:"minLogicalSize"`
	MinAllocatedSize int64 `json:"minAllocatedSize"`
}

type Options struct {
	SortBy     string `json:"sortBy"`
	Descending bool   `json:"descending"`
	Filter     Filter `json:"filter"`
}

type EntryView struct {
	Path               string `json:"path"`
	ParentPath         string `json:"parentPath"`
	Name               string `json:"name"`
	Depth              int    `json:"depth"`
	IsDirectory        bool   `json:"isDirectory"`
	IsSymlink          bool   `json:"isSymlink"`
	LogicalSizeBytes   int64  `json:"logicalSizeBytes"`
	AllocatedSizeBytes int64  `json:"allocatedSizeBytes"`
	ModifiedUnixMs     int64  `json:"modifiedUnixMs"`
}

type Page struct {
	Generation int64       `json:"generation"`
	Items      []EntryView `json:"items"`
	NextToken  string      `json:"nextToken,omitempty"`
	CountKind  string      `json:"countKind"`
	TotalCount int         `json:"totalCount,omitempty"`
	Partial    bool        `json:"partial"`
	BoundedBy  int         `json:"boundedBy"`
}

type pageToken struct {
	Generation int64 `json:"generation"`
	Offset     int   `json:"offset"`
}

type Summary struct {
	Generation           int64                   `json:"generation"`
	Integrity            scan.IntegrityState     `json:"integrity"`
	SourceCompleteness   scan.SourceCompleteness `json:"sourceCompleteness"`
	SizeSemantics        scan.SizeSemantics      `json:"sizeSemantics"`
	CountKind            string                  `json:"countKind"`
	TotalEntries         int                     `json:"totalEntries"`
	FileCount            int                     `json:"fileCount"`
	DirectoryCount       int                     `json:"directoryCount"`
	LogicalSizeBytes     int64                   `json:"logicalSizeBytes"`
	AllocatedSizeBytes   int64                   `json:"allocatedSizeBytes"`
	LargestLogicalPath   string                  `json:"largestLogicalPath,omitempty"`
	LargestLogicalSize   int64                   `json:"largestLogicalSize,omitempty"`
	LargestAllocatedPath string                  `json:"largestAllocatedPath,omitempty"`
	LargestAllocatedSize int64                   `json:"largestAllocatedSize,omitempty"`
}

type CompositionBucket struct {
	Key                string `json:"key"`
	EntryCount         int    `json:"entryCount"`
	FileCount          int    `json:"fileCount"`
	DirectoryCount     int    `json:"directoryCount"`
	LogicalSizeBytes   int64  `json:"logicalSizeBytes"`
	AllocatedSizeBytes int64  `json:"allocatedSizeBytes"`
}

type HistoryPoint struct {
	Generation         int64    `json:"generation"`
	CanonicalRoot      string   `json:"canonicalRoot"`
	CompletedUnixMs    int64    `json:"completedUnixMs"`
	ComparableWithPrev bool     `json:"comparableWithPrevious"`
	Incompatibilities  []string `json:"incompatibilities,omitempty"`
	LogicalSizeBytes   int64    `json:"logicalSizeBytes"`
	AllocatedSizeBytes int64    `json:"allocatedSizeBytes"`
	EntryCount         int      `json:"entryCount"`
}

type PackageLink struct {
	PackageName string   `json:"packageName"`
	Paths       []string `json:"paths"`
}

type ActivityItem struct {
	EntryView
	Reason string `json:"reason"`
}

func Children(ctx context.Context, session scan.Session, parentPath string, req PageRequest, options Options) (Page, error) {
	return selectPage(ctx, session, req, options, func(e scan.Entry) bool {
		return clean(e.ParentPath) == clean(parentPath)
	})
}

func CurrentFolder(ctx context.Context, session scan.Session, folderPath string, req PageRequest, options Options) (Page, error) {
	return Children(ctx, session, folderPath, req, options)
}

func Search(ctx context.Context, session scan.Session, term string, req PageRequest, options Options) (Page, error) {
	needle := strings.ToLower(strings.TrimSpace(term))
	return selectPage(ctx, session, req, options, func(e scan.Entry) bool {
		if needle == "" {
			return true
		}
		return strings.Contains(strings.ToLower(e.Path), needle) || strings.Contains(strings.ToLower(e.Name), needle)
	})
}

func LargeFiles(ctx context.Context, session scan.Session, minBytes int64, semantics scan.SizeSemantics, req PageRequest) (Page, error) {
	if semantics == "" {
		semantics = scan.SizeLogical
	}
	return selectPage(ctx, session, req, Options{SortBy: sortForSemantics(semantics), Descending: true}, func(e scan.Entry) bool {
		if e.IsDirectory {
			return false
		}
		switch semantics {
		case scan.SizeAllocated:
			return e.AllocatedSizeBytes >= minBytes
		default:
			return e.LogicalSizeBytes >= minBytes
		}
	})
}

func SummaryFor(session scan.Session) Summary {
	summary := Summary{
		Generation:         session.Generation,
		Integrity:          session.Integrity,
		SourceCompleteness: session.SourceCompleteness,
		SizeSemantics:      session.SizeSemanticsUsedForScan,
		CountKind:          countKind(session),
		TotalEntries:       len(session.Entries),
	}
	for _, e := range session.Entries {
		if e.IsDirectory {
			summary.DirectoryCount++
		} else {
			summary.FileCount++
		}
		summary.LogicalSizeBytes += e.LogicalSizeBytes
		summary.AllocatedSizeBytes += e.AllocatedSizeBytes
		if !e.IsDirectory && e.LogicalSizeBytes > summary.LargestLogicalSize {
			summary.LargestLogicalSize = e.LogicalSizeBytes
			summary.LargestLogicalPath = e.Path
		}
		if !e.IsDirectory && e.AllocatedSizeBytes > summary.LargestAllocatedSize {
			summary.LargestAllocatedSize = e.AllocatedSizeBytes
			summary.LargestAllocatedPath = e.Path
		}
	}
	return summary
}

func Composition(session scan.Session) []CompositionBucket {
	buckets := map[string]*CompositionBucket{}
	root := clean(session.CanonicalRoot)
	for _, e := range session.Entries {
		key := topLevelKey(root, e.Path)
		b, ok := buckets[key]
		if !ok {
			b = &CompositionBucket{Key: key}
			buckets[key] = b
		}
		b.EntryCount++
		if e.IsDirectory {
			b.DirectoryCount++
		} else {
			b.FileCount++
		}
		b.LogicalSizeBytes += e.LogicalSizeBytes
		b.AllocatedSizeBytes += e.AllocatedSizeBytes
	}
	out := make([]CompositionBucket, 0, len(buckets))
	for _, b := range buckets {
		out = append(out, *b)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].LogicalSizeBytes == out[j].LogicalSizeBytes {
			return out[i].Key < out[j].Key
		}
		return out[i].LogicalSizeBytes > out[j].LogicalSizeBytes
	})
	return out
}

func GrowthSeries(history []scan.Session) []HistoryPoint {
	points := make([]HistoryPoint, 0, len(history))
	var previous *scan.Session
	for i := range history {
		session := history[i]
		summary := SummaryFor(session)
		point := HistoryPoint{
			Generation:         session.Generation,
			CanonicalRoot:      session.CanonicalRoot,
			CompletedUnixMs:    session.CompletedUnixMs,
			ComparableWithPrev: true,
			LogicalSizeBytes:   summary.LogicalSizeBytes,
			AllocatedSizeBytes: summary.AllocatedSizeBytes,
			EntryCount:         summary.TotalEntries,
		}
		if previous != nil {
			compatibility := previous.CompatibilityWith(session)
			point.ComparableWithPrev = compatibility.Comparable
			point.Incompatibilities = compatibility.Reasons
		}
		points = append(points, point)
		previous = &history[i]
	}
	return points
}

func PackageLinkedPaths(ctx context.Context, session scan.Session, links []PackageLink, packageName string, req PageRequest) (Page, error) {
	allowed := map[string]bool{}
	for _, link := range links {
		if link.PackageName != packageName {
			continue
		}
		for _, p := range link.Paths {
			allowed[clean(p)] = true
		}
	}
	return selectPage(ctx, session, req, Options{SortBy: SortByPath}, func(e scan.Entry) bool {
		path := clean(e.Path)
		for prefix := range allowed {
			if path == prefix || strings.HasPrefix(path, prefix+string(filepath.Separator)) {
				return true
			}
		}
		return false
	})
}

func ActivityPage(ctx context.Context, session scan.Session, req PageRequest) (Page, error) {
	return selectPage(ctx, session, req, Options{SortBy: SortByModified, Descending: true}, func(e scan.Entry) bool {
		return true
	})
}

func selectPage(ctx context.Context, session scan.Session, req PageRequest, options Options, include func(scan.Entry) bool) (Page, error) {
	if err := validateGeneration(session, req); err != nil {
		return Page{}, err
	}
	offset, err := decodeOffset(req.Token, session.Generation)
	if err != nil {
		return Page{}, err
	}
	limit := req.BoundedLimit(MaxPageLimit)
	selected := make([]scan.Entry, 0, min(len(session.Entries), limit+1))
	for i, e := range session.Entries {
		if i%128 == 0 {
			if err := ctx.Err(); err != nil {
				return Page{}, ErrCancelled
			}
		}
		if include(e) && matchesFilter(e, options.Filter) {
			selected = append(selected, e)
		}
	}
	sortEntries(selected, options)
	if offset > len(selected) {
		offset = len(selected)
	}
	end := offset + limit
	if end > len(selected) {
		end = len(selected)
	}
	items := make([]EntryView, 0, end-offset)
	for _, e := range selected[offset:end] {
		items = append(items, viewOf(e))
	}
	page := Page{
		Generation: session.Generation,
		Items:      items,
		CountKind:  countKind(session),
		TotalCount: len(selected),
		Partial:    session.SourceCompleteness != scan.SourceComplete || session.Truncated,
		BoundedBy:  limit,
	}
	if end < len(selected) {
		page.NextToken = encodeOffset(session.Generation, end)
	}
	return page, nil
}

func validateGeneration(session scan.Session, req PageRequest) error {
	if req.Generation != session.Generation {
		return fmt.Errorf("%w: requested=%d current=%d", ErrStaleGeneration, req.Generation, session.Generation)
	}
	return nil
}

func encodeOffset(generation int64, offset int) string {
	payload, _ := json.Marshal(pageToken{Generation: generation, Offset: offset})
	return base64.RawURLEncoding.EncodeToString(payload)
}

func decodeOffset(token string, generation int64) (int, error) {
	if token == "" {
		return 0, nil
	}
	payload, err := base64.RawURLEncoding.DecodeString(token)
	if err != nil {
		return 0, fmt.Errorf("%w: %v", ErrInvalidPageToken, err)
	}
	var decoded pageToken
	if err := json.Unmarshal(payload, &decoded); err != nil {
		return 0, fmt.Errorf("%w: %v", ErrInvalidPageToken, err)
	}
	if decoded.Generation != generation || decoded.Offset < 0 {
		return 0, ErrInvalidPageToken
	}
	return decoded.Offset, nil
}

func matchesFilter(e scan.Entry, f Filter) bool {
	if f.DirectoriesOnly && !e.IsDirectory {
		return false
	}
	if f.FilesOnly && e.IsDirectory {
		return false
	}
	if f.MinLogicalSize > 0 && e.LogicalSizeBytes < f.MinLogicalSize {
		return false
	}
	if f.MinAllocatedSize > 0 && e.AllocatedSizeBytes < f.MinAllocatedSize {
		return false
	}
	return true
}

func sortEntries(entries []scan.Entry, options Options) {
	sortBy := options.SortBy
	if sortBy == "" {
		sortBy = SortByPath
	}
	sort.SliceStable(entries, func(i, j int) bool {
		less := false
		switch sortBy {
		case SortByName:
			less = entries[i].Name < entries[j].Name
		case SortByLogicalSize:
			if entries[i].LogicalSizeBytes == entries[j].LogicalSizeBytes {
				less = entries[i].Path < entries[j].Path
			} else {
				less = entries[i].LogicalSizeBytes < entries[j].LogicalSizeBytes
			}
		case SortByAllocatedSize:
			if entries[i].AllocatedSizeBytes == entries[j].AllocatedSizeBytes {
				less = entries[i].Path < entries[j].Path
			} else {
				less = entries[i].AllocatedSizeBytes < entries[j].AllocatedSizeBytes
			}
		case SortByModified:
			if entries[i].ModifiedUnixMs == entries[j].ModifiedUnixMs {
				less = entries[i].Path < entries[j].Path
			} else {
				less = entries[i].ModifiedUnixMs < entries[j].ModifiedUnixMs
			}
		default:
			less = entries[i].Path < entries[j].Path
		}
		if options.Descending {
			return !less && entries[i].Path != entries[j].Path
		}
		return less
	})
}

func viewOf(e scan.Entry) EntryView {
	return EntryView{
		Path:               e.Path,
		ParentPath:         e.ParentPath,
		Name:               e.Name,
		Depth:              e.Depth,
		IsDirectory:        e.IsDirectory,
		IsSymlink:          e.IsSymlink,
		LogicalSizeBytes:   e.LogicalSizeBytes,
		AllocatedSizeBytes: e.AllocatedSizeBytes,
		ModifiedUnixMs:     e.ModifiedUnixMs,
	}
}

func sortForSemantics(semantics scan.SizeSemantics) string {
	if semantics == scan.SizeAllocated {
		return SortByAllocatedSize
	}
	return SortByLogicalSize
}

func countKind(session scan.Session) string {
	if session.Truncated {
		return CountPartial
	}
	switch session.SourceCompleteness {
	case scan.SourceComplete:
		return CountTotal
	case scan.SourceIncomplete:
		return CountPartial
	default:
		return CountUnknown
	}
}

func topLevelKey(root, path string) string {
	path = clean(path)
	root = clean(root)
	trimmed := strings.TrimPrefix(path, root)
	trimmed = strings.TrimPrefix(trimmed, string(filepath.Separator))
	if trimmed == "" {
		return "."
	}
	parts := strings.Split(trimmed, string(filepath.Separator))
	return parts[0]
}

func clean(path string) string {
	if path == "" {
		return "."
	}
	return filepath.Clean(path)
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
