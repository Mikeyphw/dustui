package packageevidence

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"sort"
	"strings"
)

const (
	Protocol = "dustui.package.storage.evidence.v1"

	CompletenessSupported   = "supported"
	CompletenessPartial     = "partial"
	CompletenessUnavailable = "unavailable"
	CompletenessBlocked     = "blocked"

	EnabledStateEnabled  = "enabled"
	EnabledStateDisabled = "disabled"
	EnabledStateUnknown  = "unknown"

	UIStateSupported   = "supported"
	UIStatePartial     = "partial"
	UIStateUnavailable = "unavailable"
	UIStateBlocked     = "blocked"
)

var (
	ErrInvalidPackageEvidence = errors.New("package_storage_evidence_invalid")
	ErrEvidenceRequired       = errors.New("package_storage_evidence_required")
	ErrRecheckRequired        = errors.New("package_storage_action_time_recheck_required")
)

type ByteEvidence struct {
	Known bool  `json:"known"`
	Bytes int64 `json:"bytes,omitempty"`
}

func KnownBytes(bytes int64) ByteEvidence { return ByteEvidence{Known: true, Bytes: bytes} }
func UnknownBytes() ByteEvidence          { return ByteEvidence{} }

type PackageRecord struct {
	PackageName            string       `json:"packageName"`
	UserID                 int          `json:"userId"`
	Installed              bool         `json:"installed"`
	Archived               bool         `json:"archived"`
	SystemApp              bool         `json:"systemApp"`
	EnabledState           string       `json:"enabledState"`
	SourceDirs             []string     `json:"sourceDirs,omitempty"`
	SplitDirs              []string     `json:"splitDirs,omitempty"`
	CEDataDirs             []string     `json:"ceDataDirs,omitempty"`
	DEDataDirs             []string     `json:"deDataDirs,omitempty"`
	ExternalDataDirs       []string     `json:"externalDataDirs,omitempty"`
	AdoptableDataDirs      []string     `json:"adoptableDataDirs,omitempty"`
	SecondaryStorageDirs   []string     `json:"secondaryStorageDirs,omitempty"`
	CacheDirs              []string     `json:"cacheDirs,omitempty"`
	NativeLibraryDirs      []string     `json:"nativeLibraryDirs,omitempty"`
	CodeBytes              ByteEvidence `json:"codeBytes"`
	DataBytes              ByteEvidence `json:"dataBytes"`
	CacheBytes             ByteEvidence `json:"cacheBytes"`
	NativeBytes            ByteEvidence `json:"nativeBytes"`
	CollectionCompleteness string       `json:"collectionCompleteness"`
	DegradationReasons     []string     `json:"degradationReasons,omitempty"`
}

type Snapshot struct {
	SnapshotID             string          `json:"snapshotId"`
	Generation             int64           `json:"generation"`
	Collector              string          `json:"collector"`
	AndroidUserSerialScope []int           `json:"androidUserSerialScope"`
	Records                []PackageRecord `json:"records"`
	Completeness           string          `json:"completeness"`
	DegradationReasons     []string        `json:"degradationReasons,omitempty"`
	EvidenceHash           string          `json:"evidenceHash"`
}

type NormalizedRecord struct {
	Key                    string       `json:"key"`
	PackageName            string       `json:"packageName"`
	UserID                 int          `json:"userId"`
	Installed              bool         `json:"installed"`
	Archived               bool         `json:"archived"`
	SystemApp              bool         `json:"systemApp"`
	EnabledState           string       `json:"enabledState"`
	UIState                string       `json:"uiState"`
	Paths                  []string     `json:"paths"`
	CollectionCompleteness string       `json:"collectionCompleteness"`
	DegradationReasons     []string     `json:"degradationReasons,omitempty"`
	CodeBytes              ByteEvidence `json:"codeBytes"`
	DataBytes              ByteEvidence `json:"dataBytes"`
	CacheBytes             ByteEvidence `json:"cacheBytes"`
	NativeBytes            ByteEvidence `json:"nativeBytes"`
}

type NormalizedSnapshot struct {
	Protocol     string             `json:"protocol"`
	SnapshotID   string             `json:"snapshotId"`
	Generation   int64              `json:"generation"`
	Records      []NormalizedRecord `json:"records"`
	EvidenceHash string             `json:"evidenceHash"`
	Completeness string             `json:"completeness"`
}

type ResidueCandidate struct {
	PackageKey             string   `json:"packageKey"`
	Path                   string   `json:"path"`
	EvidenceHash           string   `json:"evidenceHash"`
	PackageName            string   `json:"packageName"`
	UserID                 int      `json:"userId"`
	Eligibility            string   `json:"eligibility"`
	RequiresRecheck        bool     `json:"requiresRecheck"`
	RecheckReasons         []string `json:"recheckReasons"`
	BlocksDeletion         bool     `json:"blocksDeletion"`
	DerivedFromRegex       bool     `json:"derivedFromRegex"`
	CollectionCompleteness string   `json:"collectionCompleteness"`
}

type ActionTimeContext struct {
	EvidenceHash string `json:"evidenceHash"`
	PackageKey   string `json:"packageKey"`
	Path         string `json:"path"`
}

func NormalizeSnapshot(in Snapshot) (NormalizedSnapshot, error) {
	if strings.TrimSpace(in.SnapshotID) == "" || in.Generation <= 0 || strings.TrimSpace(in.Collector) == "" {
		return NormalizedSnapshot{}, fmt.Errorf("%w:snapshot_identity_required", ErrInvalidPackageEvidence)
	}
	if len(in.Records) == 0 {
		return NormalizedSnapshot{}, fmt.Errorf("%w:records_required", ErrInvalidPackageEvidence)
	}
	records := make([]NormalizedRecord, 0, len(in.Records))
	snapshotCompleteness := CompletenessSupported
	degradation := append([]string{}, in.DegradationReasons...)
	for _, record := range in.Records {
		normalized, err := NormalizeRecord(record)
		if err != nil {
			return NormalizedSnapshot{}, err
		}
		records = append(records, normalized)
		if normalized.CollectionCompleteness != CompletenessSupported {
			snapshotCompleteness = mergeCompleteness(snapshotCompleteness, normalized.CollectionCompleteness)
		}
		degradation = append(degradation, normalized.DegradationReasons...)
	}
	sort.Slice(records, func(i, j int) bool { return records[i].Key < records[j].Key })
	if in.Completeness != "" {
		snapshotCompleteness = mergeCompleteness(snapshotCompleteness, in.Completeness)
	}
	out := NormalizedSnapshot{Protocol: Protocol, SnapshotID: in.SnapshotID, Generation: in.Generation, Records: records, Completeness: snapshotCompleteness}
	out.EvidenceHash = hashStable(out)
	return out, nil
}

func NormalizeRecord(record PackageRecord) (NormalizedRecord, error) {
	record.PackageName = strings.TrimSpace(record.PackageName)
	if record.PackageName == "" || strings.Contains(record.PackageName, "\x00") {
		return NormalizedRecord{}, fmt.Errorf("%w:package_name_required", ErrInvalidPackageEvidence)
	}
	if record.UserID < 0 {
		return NormalizedRecord{}, fmt.Errorf("%w:user_id_required", ErrInvalidPackageEvidence)
	}
	if record.EnabledState == "" {
		record.EnabledState = EnabledStateUnknown
	}
	if record.EnabledState != EnabledStateEnabled && record.EnabledState != EnabledStateDisabled && record.EnabledState != EnabledStateUnknown {
		return NormalizedRecord{}, fmt.Errorf("%w:enabled_state_invalid", ErrInvalidPackageEvidence)
	}
	if record.CollectionCompleteness == "" {
		record.CollectionCompleteness = CompletenessSupported
	}
	if !knownCompleteness(record.CollectionCompleteness) {
		return NormalizedRecord{}, fmt.Errorf("%w:collection_completeness_invalid", ErrInvalidPackageEvidence)
	}
	for name, bytes := range map[string]ByteEvidence{"code": record.CodeBytes, "data": record.DataBytes, "cache": record.CacheBytes, "native": record.NativeBytes} {
		if bytes.Known && bytes.Bytes < 0 {
			return NormalizedRecord{}, fmt.Errorf("%w:%s_bytes_negative", ErrInvalidPackageEvidence, name)
		}
		if record.CollectionCompleteness != CompletenessSupported && bytes.Known && bytes.Bytes == 0 {
			return NormalizedRecord{}, fmt.Errorf("%w:partial_evidence_must_not_be_rendered_as_zero", ErrInvalidPackageEvidence)
		}
	}
	paths, err := normalizePaths(record.allPaths())
	if err != nil {
		return NormalizedRecord{}, err
	}
	uiState := UIStateSupported
	switch record.CollectionCompleteness {
	case CompletenessPartial:
		uiState = UIStatePartial
	case CompletenessUnavailable:
		uiState = UIStateUnavailable
	case CompletenessBlocked:
		uiState = UIStateBlocked
	}
	reasons := append([]string{}, record.DegradationReasons...)
	sort.Strings(reasons)
	return NormalizedRecord{Key: PackageKey(record.PackageName, record.UserID), PackageName: record.PackageName, UserID: record.UserID, Installed: record.Installed, Archived: record.Archived, SystemApp: record.SystemApp, EnabledState: record.EnabledState, UIState: uiState, Paths: paths, CollectionCompleteness: record.CollectionCompleteness, DegradationReasons: reasons, CodeBytes: record.CodeBytes, DataBytes: record.DataBytes, CacheBytes: record.CacheBytes, NativeBytes: record.NativeBytes}, nil
}

func PackageKey(name string, userID int) string {
	return fmt.Sprintf("u%d:%s", userID, strings.TrimSpace(name))
}

func BuildResidueCandidate(snapshot NormalizedSnapshot, packageName string, userID int, path string) (ResidueCandidate, error) {
	path = strings.TrimSpace(path)
	if path == "" || !filepath.IsAbs(path) || filepath.Clean(path) != path || strings.Contains(path, "\x00") {
		return ResidueCandidate{}, fmt.Errorf("%w:path_required", ErrInvalidPackageEvidence)
	}
	if snapshot.EvidenceHash == "" || snapshot.Generation <= 0 {
		return ResidueCandidate{}, fmt.Errorf("%w:snapshot_required", ErrEvidenceRequired)
	}
	key := PackageKey(packageName, userID)
	record, ok := snapshot.find(key)
	if !ok {
		return ResidueCandidate{PackageKey: key, Path: path, EvidenceHash: snapshot.EvidenceHash, PackageName: packageName, UserID: userID, Eligibility: "unavailable", RequiresRecheck: true, RecheckReasons: []string{"package_evidence_missing"}, BlocksDeletion: true, CollectionCompleteness: CompletenessUnavailable}, nil
	}
	candidate := ResidueCandidate{PackageKey: key, Path: path, EvidenceHash: snapshot.EvidenceHash, PackageName: record.PackageName, UserID: record.UserID, RequiresRecheck: true, CollectionCompleteness: record.CollectionCompleteness}
	if record.CollectionCompleteness != CompletenessSupported {
		candidate.Eligibility = record.UIState
		candidate.BlocksDeletion = true
		candidate.RecheckReasons = []string{"package_evidence_" + record.CollectionCompleteness}
		return candidate, nil
	}
	if record.SystemApp || record.EnabledState == EnabledStateEnabled || record.Installed {
		candidate.Eligibility = "blocked_active_or_system_package"
		candidate.BlocksDeletion = true
		candidate.RecheckReasons = []string{"package_active_or_system_at_review"}
		return candidate, nil
	}
	if record.Archived || !record.Installed {
		candidate.Eligibility = "eligible_after_recheck"
		candidate.BlocksDeletion = false
		candidate.RecheckReasons = []string{"package_state_must_match_at_action_time"}
		return candidate, nil
	}
	candidate.Eligibility = "blocked_unknown_state"
	candidate.BlocksDeletion = true
	candidate.RecheckReasons = []string{"package_state_unknown"}
	return candidate, nil
}

func VerifyActionTime(candidate ResidueCandidate, ctx ActionTimeContext) error {
	if candidate.DerivedFromRegex || candidate.EvidenceHash == "" {
		return fmt.Errorf("%w:regex_only_evidence_forbidden", ErrEvidenceRequired)
	}
	if candidate.RequiresRecheck && (ctx.EvidenceHash == "" || ctx.EvidenceHash != candidate.EvidenceHash || ctx.PackageKey != candidate.PackageKey || ctx.Path != candidate.Path) {
		return fmt.Errorf("%w:evidence_hash_package_path_mismatch", ErrRecheckRequired)
	}
	return nil
}

func Contract() map[string]any {
	return map[string]any{
		"protocol":         Protocol,
		"phase":            "OV09",
		"androidCollector": "thin-platform-api-collector",
		"goAuthority":      true,
		"evidenceStates":   []string{CompletenessSupported, CompletenessPartial, CompletenessUnavailable, CompletenessBlocked},
		"uiStates":         []string{UIStateSupported, UIStatePartial, UIStateUnavailable, UIStateBlocked},
		"packageFacts":     []string{"package/user inventory", "installed/archived", "system app", "enabled state", "source/split dirs", "CE/DE data", "external/adoptable/secondary storage", "code/data/cache/native paths", "collection completeness/degradation reasons"},
		"residueRules":     []string{"package_user_evidence_required", "path_regex_never_sufficient", "partial_evidence_not_zero", "action_time_recheck_required", "multi_user_keys_are_distinct", "system_or_enabled_packages_block_deletion"},
		"schemaTables":     []string{"android_package_evidence_snapshots", "android_package_records", "android_storage_evidence", "package_residue_candidates", "package_action_rechecks"},
	}
}

func (s NormalizedSnapshot) find(key string) (NormalizedRecord, bool) {
	for _, record := range s.Records {
		if record.Key == key {
			return record, true
		}
	}
	return NormalizedRecord{}, false
}

func (r PackageRecord) allPaths() []string {
	out := []string{}
	for _, group := range [][]string{r.SourceDirs, r.SplitDirs, r.CEDataDirs, r.DEDataDirs, r.ExternalDataDirs, r.AdoptableDataDirs, r.SecondaryStorageDirs, r.CacheDirs, r.NativeLibraryDirs} {
		out = append(out, group...)
	}
	return out
}

func normalizePaths(paths []string) ([]string, error) {
	seen := map[string]struct{}{}
	out := []string{}
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		if strings.Contains(path, "\x00") || !filepath.IsAbs(path) || filepath.Clean(path) != path {
			return nil, fmt.Errorf("%w:package_path_must_be_canonical_absolute", ErrInvalidPackageEvidence)
		}
		if _, ok := seen[path]; ok {
			continue
		}
		seen[path] = struct{}{}
		out = append(out, path)
	}
	sort.Strings(out)
	return out, nil
}

func hashStable(value any) string {
	encoded, _ := json.Marshal(value)
	sum := sha256.Sum256(encoded)
	return hex.EncodeToString(sum[:])
}

func knownCompleteness(v string) bool {
	return v == CompletenessSupported || v == CompletenessPartial || v == CompletenessUnavailable || v == CompletenessBlocked
}

func mergeCompleteness(current, next string) string {
	rank := map[string]int{CompletenessSupported: 0, CompletenessPartial: 1, CompletenessUnavailable: 2, CompletenessBlocked: 3}
	if rank[next] > rank[current] {
		return next
	}
	return current
}
