package scan

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"
)

type IntegrityState string

const (
	IntegrityComplete          IntegrityState = "complete"
	IntegrityPartial           IntegrityState = "partial"
	IntegrityTruncated         IntegrityState = "truncated"
	IntegrityMalformed         IntegrityState = "malformed"
	IntegrityFailed            IntegrityState = "failed"
	IntegrityStaleAfterCleanup IntegrityState = "stale_after_cleanup"
)

type SourceCompleteness string

const (
	SourceComplete   SourceCompleteness = "complete"
	SourceIncomplete SourceCompleteness = "incomplete"
	SourceUnknown    SourceCompleteness = "unknown"
)

type SizeSemantics string

const (
	SizeLogical             SizeSemantics = "logical"
	SizeAllocated           SizeSemantics = "allocated"
	SizeLogicalAndAllocated SizeSemantics = "logical_and_allocated"
)

type BoundaryPolicy string

const (
	BoundaryStayOnDevice BoundaryPolicy = "stay_on_filesystem"
	BoundaryAllowCross   BoundaryPolicy = "allow_cross_filesystem"
)

type HiddenPolicy string

const (
	HiddenInclude HiddenPolicy = "include_hidden"
	HiddenExclude HiddenPolicy = "exclude_hidden"
)

type ExclusionPolicy string

const (
	ExclusionNone     ExclusionPolicy = "none"
	ExclusionPatterns ExclusionPolicy = "pattern_list"
)

type BackendIdentity struct {
	Name       string `json:"name"`
	Version    string `json:"version"`
	Privileged bool   `json:"privileged"`
}

type Options struct {
	Backend                  BackendIdentity `json:"backend"`
	IncludeHidden            bool            `json:"includeHidden"`
	Exclusions               []string        `json:"exclusions"`
	FollowSymlinks           bool            `json:"followSymlinks"`
	FilesystemBoundaryPolicy BoundaryPolicy  `json:"filesystemBoundaryPolicy"`
	SizeSemantics            SizeSemantics   `json:"sizeSemantics"`
	MaxEntries               int             `json:"maxEntries"`
	MaxDepth                 int             `json:"maxDepth"`
	MinSizeBytes             int64           `json:"minSizeBytes"`
	EntryMode                string          `json:"entryMode"`
}

func DefaultOptions() Options {
	return Options{
		Backend:                  BackendIdentity{Name: "dustui-go-scanner", Version: "ov03", Privileged: false},
		IncludeHidden:            true,
		Exclusions:               []string{},
		FollowSymlinks:           false,
		FilesystemBoundaryPolicy: BoundaryStayOnDevice,
		SizeSemantics:            SizeLogicalAndAllocated,
		MaxEntries:               0,
		MaxDepth:                 -1,
		MinSizeBytes:             0,
		EntryMode:                "ALL",
	}
}

func (o Options) Normalized() Options {
	zeroValueScanBounds := o.MaxDepth == 0 && o.MaxEntries == 0 && o.MinSizeBytes == 0 && strings.TrimSpace(o.EntryMode) == ""
	if zeroValueScanBounds {
		// Preserve the pre-filter zero-value contract: Options{} means an unbounded scan.
		o.MaxDepth = -1
	}
	if o.Backend.Name == "" {
		o.Backend.Name = "dustui-go-scanner"
	}
	if o.Backend.Version == "" {
		o.Backend.Version = "ov03"
	}
	if o.FilesystemBoundaryPolicy == "" {
		o.FilesystemBoundaryPolicy = BoundaryStayOnDevice
	}
	if o.SizeSemantics == "" {
		o.SizeSemantics = SizeLogicalAndAllocated
	}
	if o.MaxDepth < -1 {
		o.MaxDepth = -1
	}
	if o.MinSizeBytes < 0 {
		o.MinSizeBytes = 0
	}
	o.EntryMode = strings.ToUpper(strings.TrimSpace(o.EntryMode))
	if o.EntryMode != "FILES" && o.EntryMode != "DIRECTORIES" {
		o.EntryMode = "ALL"
	}
	o.Exclusions = append([]string{}, o.Exclusions...)
	sort.Strings(o.Exclusions)
	return o
}

func (o Options) Fingerprint() string {
	normalized := o.Normalized()
	payload, _ := json.Marshal(normalized)
	sum := sha256.Sum256(payload)
	return hex.EncodeToString(sum[:])
}

func (o Options) HiddenPolicy() HiddenPolicy {
	if o.IncludeHidden {
		return HiddenInclude
	}
	return HiddenExclude
}

func (o Options) ExclusionPolicy() ExclusionPolicy {
	if len(o.Exclusions) == 0 {
		return ExclusionNone
	}
	return ExclusionPatterns
}

type ProblemKind string

const (
	ProblemUnreadable      ProblemKind = "unreadable_entry"
	ProblemMalformedRecord ProblemKind = "malformed_record"
	ProblemBoundarySkipped ProblemKind = "filesystem_boundary_skipped"
	ProblemTruncated       ProblemKind = "scan_truncated"
	ProblemSymlinkSkipped  ProblemKind = "symlink_not_followed"
)

type Problem struct {
	Kind    ProblemKind `json:"kind"`
	Path    string      `json:"path"`
	Message string      `json:"message"`
}

type Entry struct {
	Path               string `json:"path"`
	ParentPath         string `json:"parentPath"`
	Name               string `json:"name"`
	Depth              int    `json:"depth"`
	IsDirectory        bool   `json:"isDirectory"`
	IsSymlink          bool   `json:"isSymlink"`
	LinkTarget         string `json:"linkTarget,omitempty"`
	LogicalSizeBytes   int64  `json:"logicalSizeBytes"`
	AllocatedSizeBytes int64  `json:"allocatedSizeBytes"`
	Mode               string `json:"mode"`
	Device             uint64 `json:"device"`
	Inode              uint64 `json:"inode"`
	HardLinkCount      uint64 `json:"hardLinkCount"`
	HardLinkGroup      string `json:"hardLinkGroup,omitempty"`
	ModifiedUnixMs     int64  `json:"modifiedUnixMs"`
	BoundarySkipped    bool   `json:"boundarySkipped"`
	Unreadable         bool   `json:"unreadable"`
	FileCount          int64  `json:"fileCount"`
}

type Session struct {
	ID                       string             `json:"id"`
	Generation               int64              `json:"generation"`
	Root                     string             `json:"root"`
	CanonicalRoot            string             `json:"canonicalRoot"`
	BackendIdentity          BackendIdentity    `json:"backendIdentity"`
	OptionsFingerprint       string             `json:"optionsFingerprint"`
	HiddenPolicy             HiddenPolicy       `json:"hiddenPolicy"`
	ExclusionPolicy          ExclusionPolicy    `json:"exclusionPolicy"`
	FilesystemBoundaryPolicy BoundaryPolicy     `json:"filesystemBoundaryPolicy"`
	SizeSemanticsUsedForScan SizeSemantics      `json:"sizeSemanticsUsedForScan"`
	ObservedEntryCount       int                `json:"observedEntryCount"`
	PersistedEntryCount      int                `json:"persistedEntryCount"`
	MalformedRecordCount     int                `json:"malformedRecordCount"`
	Truncated                bool               `json:"truncated"`
	Integrity                IntegrityState     `json:"integrity"`
	SourceCompleteness       SourceCompleteness `json:"sourceCompleteness"`
	StaleAfterCleanup        bool               `json:"staleAfterCleanup"`
	StartedUnixMs            int64              `json:"startedUnixMs"`
	CompletedUnixMs          int64              `json:"completedUnixMs"`
	Entries                  []Entry            `json:"entries"`
	Problems                 []Problem          `json:"problems"`
}

func (s Session) CanAnalyze() bool { return s.authoritativeFreshEnough() }
func (s Session) CanDriveDeletion() bool {
	return s.authoritativeFreshEnough() && s.SourceCompleteness == SourceComplete && !s.Truncated
}
func (s Session) CanDriveDuplicateHashing() bool {
	return s.authoritativeFreshEnough() && s.SourceCompleteness == SourceComplete
}
func (s Session) CanDriveResidue() bool {
	return s.authoritativeFreshEnough() && s.SourceCompleteness != SourceUnknown
}
func (s Session) CanExportCompleteData() bool {
	return s.authoritativeFreshEnough() && s.SourceCompleteness == SourceComplete && s.PersistedEntryCount == s.ObservedEntryCount
}
func (s Session) CanShowAsFresh() bool {
	return s.Integrity == IntegrityComplete && !s.StaleAfterCleanup
}

func (s Session) authoritativeFreshEnough() bool {
	return s.Integrity == IntegrityComplete && !s.StaleAfterCleanup && s.Generation > 0 && s.OptionsFingerprint != ""
}

func (s Session) CanCompareWith(other Session) bool {
	return s.CompatibilityWith(other).Comparable
}

type Compatibility struct {
	Comparable bool     `json:"comparable"`
	Reasons    []string `json:"reasons"`
}

func (s Session) CompatibilityWith(other Session) Compatibility {
	reasons := []string{}
	if s.CanonicalRoot != other.CanonicalRoot {
		reasons = append(reasons, "canonical_root_mismatch")
	}
	if s.BackendIdentity.Name != other.BackendIdentity.Name {
		reasons = append(reasons, "backend_name_mismatch")
	}
	if s.HiddenPolicy != other.HiddenPolicy {
		reasons = append(reasons, "hidden_policy_mismatch")
	}
	if s.ExclusionPolicy != other.ExclusionPolicy {
		reasons = append(reasons, "exclusion_policy_mismatch")
	}
	if s.FilesystemBoundaryPolicy != other.FilesystemBoundaryPolicy {
		reasons = append(reasons, "filesystem_boundary_policy_mismatch")
	}
	if s.SizeSemanticsUsedForScan != other.SizeSemanticsUsedForScan {
		reasons = append(reasons, "size_semantics_mismatch")
	}
	if s.SourceCompleteness != SourceComplete || other.SourceCompleteness != SourceComplete {
		reasons = append(reasons, "source_incomplete")
	}
	if s.StaleAfterCleanup || other.StaleAfterCleanup {
		reasons = append(reasons, "stale_after_cleanup")
	}
	return Compatibility{Comparable: len(reasons) == 0, Reasons: reasons}
}

func (s Session) MarkStaleAfterCleanup() Session {
	s.StaleAfterCleanup = true
	s.Integrity = IntegrityStaleAfterCleanup
	s.SourceCompleteness = SourceIncomplete
	return s
}

func Scan(root string, generation int64, options Options) (Session, error) {
	return ScanContext(context.Background(), root, generation, options)
}

// ScanContext is the single production filesystem traversal implementation used
// by both app-owned and privileged/root execution. Platform adapters may change
// process identity, never scan semantics.
func ScanContext(ctx context.Context, root string, generation int64, options Options) (Session, error) {
	started := time.Now().UnixMilli()
	opts := options.Normalized()
	if generation <= 0 {
		return Session{}, fmt.Errorf("generation must be positive")
	}
	canonical, err := filepath.Abs(root)
	if err != nil {
		return Session{}, err
	}
	if evaluated, evalErr := filepath.EvalSymlinks(canonical); evalErr == nil {
		canonical = evaluated
	}
	rootInfo, err := os.Lstat(canonical)
	if err != nil {
		return Session{}, err
	}
	rootDev := device(rootInfo)
	session := Session{
		ID:                       stableSessionID(canonical, generation, opts.Fingerprint()),
		Generation:               generation,
		Root:                     root,
		CanonicalRoot:            canonical,
		BackendIdentity:          opts.Backend,
		OptionsFingerprint:       opts.Fingerprint(),
		HiddenPolicy:             opts.HiddenPolicy(),
		ExclusionPolicy:          opts.ExclusionPolicy(),
		FilesystemBoundaryPolicy: opts.FilesystemBoundaryPolicy,
		SizeSemanticsUsedForScan: opts.SizeSemantics,
		StartedUnixMs:            started,
		Integrity:                IntegrityComplete,
		SourceCompleteness:       SourceComplete,
		Entries:                  []Entry{},
		Problems:                 []Problem{},
	}
	walkErr := filepath.WalkDir(canonical, func(path string, d fs.DirEntry, walkErr error) error {
		if err := ctx.Err(); err != nil {
			return err
		}
		if walkErr != nil {
			session.Problems = append(session.Problems, Problem{Kind: ProblemUnreadable, Path: path, Message: walkErr.Error()})
			session.MalformedRecordCount++
			session.SourceCompleteness = SourceIncomplete
			session.Integrity = IntegrityPartial
			if d != nil && d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		rel, _ := filepath.Rel(canonical, path)
		if rel == "." {
			rel = ""
		}
		name := d.Name()
		entryDepth := depth(rel)
		if rel != "" && opts.MaxDepth >= 0 && entryDepth > opts.MaxDepth {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		if rel != "" && !opts.IncludeHidden && strings.HasPrefix(name, ".") {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		if rel != "" && excluded(rel, opts.Exclusions) {
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		info, infoErr := d.Info()
		if infoErr != nil {
			session.Problems = append(session.Problems, Problem{Kind: ProblemUnreadable, Path: path, Message: infoErr.Error()})
			session.MalformedRecordCount++
			session.SourceCompleteness = SourceIncomplete
			session.Integrity = IntegrityPartial
			if d.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		entry := entryFromInfo(canonical, path, info)
		if opts.FilesystemBoundaryPolicy == BoundaryStayOnDevice && entry.Device != 0 && rootDev != 0 && entry.Device != rootDev {
			entry.BoundarySkipped = true
			session.Problems = append(session.Problems, Problem{Kind: ProblemBoundarySkipped, Path: path, Message: "entry is on another filesystem"})
			if info.IsDir() {
				session.Entries = append(session.Entries, entry)
				return filepath.SkipDir
			}
		}
		if entry.IsSymlink {
			target, _ := os.Readlink(path)
			entry.LinkTarget = target
			if !opts.FollowSymlinks {
				session.Problems = append(session.Problems, Problem{Kind: ProblemSymlinkSkipped, Path: path, Message: "symlink not followed"})
			}
		}
		// Root is context, not an indexed child. Filtering controls emitted entries
		// without changing traversal/size semantics.
		if rel != "" {
			logical := entry.LogicalSizeBytes
			allocated := entry.AllocatedSizeBytes
			sizeForFilter := logical
			if opts.SizeSemantics == SizeAllocated {
				sizeForFilter = allocated
			}
			modeAllows := opts.EntryMode == "ALL" || (opts.EntryMode == "FILES" && !entry.IsDirectory) || (opts.EntryMode == "DIRECTORIES" && entry.IsDirectory)
			if modeAllows && sizeForFilter >= opts.MinSizeBytes {
				session.ObservedEntryCount++
				session.Entries = append(session.Entries, entry)
				if opts.MaxEntries > 0 && len(session.Entries) >= opts.MaxEntries {
					session.Truncated = true
					session.Integrity = IntegrityTruncated
					session.SourceCompleteness = SourceIncomplete
					session.Problems = append(session.Problems, Problem{Kind: ProblemTruncated, Path: canonical, Message: "max entries reached"})
					return errors.New("dustui_scan_truncated")
				}
			}
		}
		return nil
	})
	if walkErr != nil && !errors.Is(walkErr, context.Canceled) && !errors.Is(walkErr, context.DeadlineExceeded) && walkErr.Error() != "dustui_scan_truncated" {
		session.Problems = append(session.Problems, Problem{Kind: ProblemMalformedRecord, Path: canonical, Message: walkErr.Error()})
		session.MalformedRecordCount++
		session.Integrity = IntegrityMalformed
		session.SourceCompleteness = SourceIncomplete
	}
	// Directory totals are derived from the same indexed entries so root and
	// non-root execution cannot disagree about size/file-count semantics.
	for i := range session.Entries {
		if !session.Entries[i].IsDirectory {
			session.Entries[i].FileCount = 1
		}
	}
	for i := len(session.Entries) - 1; i >= 0; i-- {
		child := session.Entries[i]
		if child.ParentPath == "" {
			continue
		}
		for j := range session.Entries {
			if session.Entries[j].Path == child.ParentPath && session.Entries[j].IsDirectory {
				session.Entries[j].LogicalSizeBytes += child.LogicalSizeBytes
				session.Entries[j].AllocatedSizeBytes += child.AllocatedSizeBytes
				session.Entries[j].FileCount += child.FileCount
				break
			}
		}
	}
	session.PersistedEntryCount = len(session.Entries)
	if session.MalformedRecordCount > 0 && session.Integrity == IntegrityComplete {
		session.Integrity = IntegrityPartial
		session.SourceCompleteness = SourceIncomplete
	}
	session.CompletedUnixMs = time.Now().UnixMilli()
	sort.Slice(session.Entries, func(i, j int) bool { return session.Entries[i].Path < session.Entries[j].Path })
	if err := ctx.Err(); err != nil {
		session.SourceCompleteness = SourceIncomplete
		if session.Integrity == IntegrityComplete {
			session.Integrity = IntegrityPartial
		}
		return session, err
	}
	return session, nil
}

func stableSessionID(root string, generation int64, fingerprint string) string {
	sum := sha256.Sum256([]byte(fmt.Sprintf("%s\x00%d\x00%s", root, generation, fingerprint)))
	return hex.EncodeToString(sum[:16])
}

func excluded(rel string, patterns []string) bool {
	rel = filepath.ToSlash(rel)
	for _, pattern := range patterns {
		pattern = filepath.ToSlash(pattern)
		if pattern == "" {
			continue
		}
		if ok, _ := filepath.Match(pattern, rel); ok {
			return true
		}
		if strings.Contains(rel, pattern) {
			return true
		}
	}
	return false
}

func entryFromInfo(root, path string, info os.FileInfo) Entry {
	rel, _ := filepath.Rel(root, path)
	if rel == "." {
		rel = ""
	}
	parent := ""
	if rel != "" {
		parent = filepath.Dir(rel)
		if parent == "." {
			parent = ""
		}
	}
	e := Entry{
		Path:               filepath.ToSlash(rel),
		ParentPath:         filepath.ToSlash(parent),
		Name:               info.Name(),
		Depth:              depth(rel),
		IsDirectory:        info.IsDir(),
		IsSymlink:          info.Mode()&os.ModeSymlink != 0,
		LogicalSizeBytes:   info.Size(),
		AllocatedSizeBytes: allocatedSize(info),
		Mode:               info.Mode().String(),
		ModifiedUnixMs:     info.ModTime().UnixMilli(),
	}
	if st, ok := info.Sys().(*syscall.Stat_t); ok {
		e.Device = uint64(st.Dev)
		e.Inode = uint64(st.Ino)
		e.HardLinkCount = uint64(st.Nlink)
		if st.Nlink > 1 && !info.IsDir() {
			e.HardLinkGroup = fmt.Sprintf("%d:%d", uint64(st.Dev), uint64(st.Ino))
		}
	}
	return e
}

func depth(rel string) int {
	if rel == "" || rel == "." {
		return 0
	}
	return strings.Count(filepath.ToSlash(rel), "/") + 1
}

func allocatedSize(info os.FileInfo) int64 {
	if st, ok := info.Sys().(*syscall.Stat_t); ok {
		if st.Blocks > 0 {
			return int64(st.Blocks) * 512
		}
	}
	return info.Size()
}

func device(info os.FileInfo) uint64 {
	if st, ok := info.Sys().(*syscall.Stat_t); ok {
		return uint64(st.Dev)
	}
	return 0
}
