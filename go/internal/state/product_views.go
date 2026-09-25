package state

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

type ProductInsightsOptions struct {
	ProtectedPaths  []string          `json:"protectedPaths"`
	IgnoredPackages []string          `json:"ignoredPackages"`
	AppLabels       map[string]string `json:"appLabels"`
}

type productEntry struct {
	AndroidScanEntry
	Package string
	UserID  int
}

type packageAccum struct {
	Name                                                                           string
	Installed                                                                      bool
	System                                                                         bool
	Total, Private, Cache, Code, Database, Preferences, External, OBB, Media, Logs int64
	Entries                                                                        []productEntry
}

func (s *SQLiteStore) ProductInsights(ctx context.Context, scanID string, options ProductInsightsOptions) (map[string]any, error) {
	return s.productInsightsBounded(ctx, scanID, options)
}

func (s *SQLiteStore) packageInventoryComplete(ctx context.Context, snapshotID string) bool {
	if snapshotID == "" {
		return false
	}
	var raw string
	if err := s.db.QueryRowContext(ctx, `SELECT degradation_reasons_json FROM android_package_evidence_snapshots WHERE id=?`, snapshotID).Scan(&raw); err != nil {
		return false
	}
	var reasons []string
	_ = json.Unmarshal([]byte(raw), &reasons)
	for _, reason := range reasons {
		r := strings.ToLower(reason)
		if strings.Contains(r, "multi_user") || strings.Contains(r, "inventory_unavailable") || strings.Contains(r, "package_visibility") {
			return false
		}
	}
	return true
}

func annotateProductEntries(entries []AndroidScanEntry, snapshot *packageevidence.NormalizedSnapshot) []productEntry {
	installed := map[string]packageevidence.NormalizedRecord{}
	for _, r := range snapshot.Records {
		installed[r.Key] = r
	}
	out := make([]productEntry, 0, len(entries))
	for _, e := range entries {
		pkg, uid := packageIdentityFromPath(e.Path)
		if pkg == "" {
			// Fall back to exact package evidence roots, longest prefix first.
			best := 0
			for _, r := range snapshot.Records {
				for _, root := range r.Paths {
					clean := strings.TrimRight(root, "/")
					if clean != "" && (e.Path == clean || strings.HasPrefix(e.Path, clean+"/")) && len(clean) > best {
						pkg, uid, best = r.PackageName, r.UserID, len(clean)
					}
				}
			}
		}
		_ = installed
		out = append(out, productEntry{AndroidScanEntry: e, Package: pkg, UserID: uid})
	}
	return out
}

func packageIdentityFromPath(path string) (string, int) {
	clean := filepath.Clean(path)
	seg := strings.Split(strings.TrimPrefix(clean, "/"), "/")
	valid := func(s string) bool {
		return strings.Contains(s, ".") && !strings.ContainsAny(s, "\x00/") && len(s) <= 255
	}
	if len(seg) >= 4 && seg[0] == "data" && (seg[1] == "user" || seg[1] == "user_de") {
		uid, err := strconv.Atoi(seg[2])
		if err == nil && valid(seg[3]) {
			return seg[3], uid
		}
	}
	if len(seg) >= 3 && seg[0] == "data" && seg[1] == "data" && valid(seg[2]) {
		return seg[2], 0
	}
	if len(seg) >= 7 && seg[0] == "storage" && seg[1] == "emulated" && seg[3] == "Android" && (seg[4] == "data" || seg[4] == "media" || seg[4] == "obb") {
		uid, err := strconv.Atoi(seg[2])
		if err == nil && valid(seg[5]) {
			return seg[5], uid
		}
	}
	if len(seg) >= 5 && seg[0] == "data" && seg[1] == "app" {
		// Modern /data/app paths contain randomized parents; match package token conservatively.
		for _, token := range seg[2:] {
			name := strings.Split(token, "-")[0]
			if valid(name) {
				return name, 0
			}
		}
	}
	return "", 0
}

func buildPackageAccums(entries []productEntry, snapshot *packageevidence.NormalizedSnapshot) map[string]*packageAccum {
	installed := map[string]packageevidence.NormalizedRecord{}
	for _, r := range snapshot.Records {
		if r.UserID == 0 {
			installed[r.PackageName] = r
		}
	}
	out := map[string]*packageAccum{}
	for _, e := range entries {
		if e.Package == "" {
			continue
		}
		a := out[e.Package]
		if a == nil {
			r, ok := installed[e.Package]
			a = &packageAccum{Name: e.Package, Installed: ok && r.Installed, System: ok && r.SystemApp}
			out[e.Package] = a
		}
		a.Entries = append(a.Entries, e)
		a.Total += e.SizeBytes
		role := packageRole(e.Path)
		switch role {
		case "PRIVATE_DATA":
			a.Private += e.SizeBytes
		case "CACHE", "CODE_CACHE":
			a.Cache += e.SizeBytes
		case "CODE":
			a.Code += e.SizeBytes
		case "DATABASE":
			a.Database += e.SizeBytes
		case "PREFERENCES":
			a.Preferences += e.SizeBytes
		case "EXTERNAL_DATA":
			a.External += e.SizeBytes
		case "OBB":
			a.OBB += e.SizeBytes
		case "MEDIA":
			a.Media += e.SizeBytes
		case "LOG":
			a.Logs += e.SizeBytes
		}
	}
	return out
}

func packageRole(path string) string {
	lower := strings.ToLower(path)
	switch {
	case strings.HasPrefix(lower, "/data/app/"):
		return "CODE"
	case strings.Contains(lower, "/code_cache"):
		return "CODE_CACHE"
	case strings.Contains(lower, "/cache"):
		return "CACHE"
	case strings.Contains(lower, "/databases") || strings.HasSuffix(lower, ".db") || strings.HasSuffix(lower, ".sqlite") || strings.HasSuffix(lower, ".sqlite3"):
		return "DATABASE"
	case strings.Contains(lower, "/shared_prefs") || (strings.HasSuffix(lower, ".xml") && strings.HasPrefix(lower, "/data/")):
		return "PREFERENCES"
	case strings.Contains(lower, "/android/obb/"):
		return "OBB"
	case strings.Contains(lower, "/android/media/"):
		return "MEDIA"
	case strings.Contains(lower, "/android/data/"):
		return "EXTERNAL_DATA"
	case strings.HasSuffix(lower, ".log") || strings.Contains(lower, "/logs"):
		return "LOG"
	case strings.HasPrefix(lower, "/data/user/") || strings.HasPrefix(lower, "/data/user_de/") || strings.HasPrefix(lower, "/data/data/"):
		return "PRIVATE_DATA"
	default:
		return "OTHER"
	}
}

func buildSnapshotDelta(scanID string, previous *AndroidScanSession, current, old []AndroidScanEntry) map[string]any {
	prevMap := map[string]int64{}
	curMap := map[string]int64{}
	var curTotal, oldTotal int64
	for _, e := range current {
		curTotal += e.SizeBytes
		if !e.IsDirectory {
			curMap[e.Path] = e.SizeBytes
		}
	}
	for _, e := range old {
		oldTotal += e.SizeBytes
		if !e.IsDirectory {
			prevMap[e.Path] = e.SizeBytes
		}
	}
	var added, removed int64
	changed := 0
	for p, n := range curMap {
		if o, ok := prevMap[p]; !ok {
			added += n
		} else if o != n {
			changed++
		}
	}
	for p, o := range prevMap {
		if _, ok := curMap[p]; !ok {
			removed += o
		}
	}
	var previousID any = nil
	if previous != nil {
		previousID = previous.ID
	}
	return map[string]any{"currentScanId": scanID, "previousScanId": previousID, "totalDeltaBytes": curTotal - oldTotal, "addedBytes": added, "removedBytes": removed, "changedEntries": changed}
}

func buildAppSummaries(apps map[string]*packageAccum) []map[string]any {
	out := []map[string]any{}
	for _, a := range apps {
		out = append(out, map[string]any{"packageName": a.Name, "totalBytes": a.Total, "privateBytes": a.Private, "cacheBytes": a.Cache, "externalBytes": a.External + a.OBB + a.Media, "entryCount": len(a.Entries), "orphaned": !a.Installed})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}

func buildResidueViews(entries []productEntry, snapshot *packageevidence.NormalizedSnapshot, inventoryComplete bool, protected map[string]bool, generation int64, scanID string, labels map[string]string) ([]map[string]any, []intelligence.ResidueEvidence) {
	installed := map[string]bool{}
	related := map[string][]string{}
	for _, r := range snapshot.Records {
		if r.Installed {
			installed[packageevidence.PackageKey(r.PackageName, r.UserID)] = true
		}
	}
	_ = related
	byPkg := map[string][]map[string]any{}
	evidenceOut := []intelligence.ResidueEvidence{}
	for _, e := range entries {
		if !e.IsDirectory || e.Package == "" {
			continue
		}
		// Only package-root directories are candidates; descendants are not independent residue.
		pkg, uid := packageIdentityFromPath(e.Path)
		if pkg == "" || pkg != e.Package || uid != e.UserID {
			continue
		}
		if !isPackageRootPath(e.Path, pkg, uid) {
			continue
		}
		key := packageevidence.PackageKey(pkg, uid)
		if installed[key] {
			continue
		}
		confidence := "UNCERTAIN"
		if inventoryComplete {
			confidence = "HIGH"
		}
		isProtected := protected[e.Path]
		kind := residueKind(e.Path)
		evs := []map[string]any{{"code": "PACKAGE_ABSENT", "label": "Package absent", "detail": "Go package/user evidence has no installed package for this storage root."}}
		if !inventoryComplete {
			evs = append(evs, map[string]any{"code": "INVENTORY_PARTIAL", "label": "Package inventory partial", "detail": "Deletion is blocked until package/user evidence is complete and rechecked."})
		}
		loc := map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "modifiedAt": e.ModifiedAt, "fileCount": e.FileCount, "userId": uid, "kind": kind, "confidence": confidence, "evidence": evs, "userProtected": isProtected}
		byPkg[pkg] = append(byPkg[pkg], loc)
		actionable := cleanup.ActionablePathEvidence{Path: e.Path, ScanID: scanID, ScanGeneration: generation, SourceSurface: "residue", SourceKind: "package_user_evidence", ExpectedHash: snapshot.EvidenceHash, ExpectedIdentity: key, PackageName: pkg, UserHandle: strconv.Itoa(uid), Bucket: strings.ToLower(kind), ProtectedState: map[bool]string{true: "protected", false: cleanup.ProtectedStateUnprotected}[isProtected], DeletionEligible: inventoryComplete && !isProtected, Reason: "package_absent_recheck_required", LogicalSizeBytes: e.SizeBytes, AllocatedSizeBytes: e.AllocatedBytes}
		if actionable.ProtectedState == "protected" {
			actionable.DeletionEligible = false
		}
		candidate := packageevidence.ResidueCandidate{PackageKey: key, Path: e.Path, EvidenceHash: snapshot.EvidenceHash, PackageName: pkg, UserID: uid, Eligibility: map[bool]string{true: "eligible_after_recheck", false: "unavailable"}[inventoryComplete], RequiresRecheck: true, RecheckReasons: []string{"package_state_must_match_at_action_time"}, BlocksDeletion: !inventoryComplete || isProtected, DerivedFromRegex: false, CollectionCompleteness: map[bool]string{true: packageevidence.CompletenessSupported, false: packageevidence.CompletenessPartial}[inventoryComplete]}
		if err := actionable.Validate(); err == nil {
			evidenceOut = append(evidenceOut, intelligence.ResidueEvidence{Candidate: candidate, Confidence: map[bool]string{true: intelligence.ConfidenceCandidate, false: intelligence.ConfidenceAmbiguous}[inventoryComplete], SpecialContext: kind, Evidence: actionable})
		}
	}
	groups := []map[string]any{}
	for pkg, locs := range byPkg {
		conf := "UNCERTAIN"
		if inventoryComplete {
			conf = "HIGH"
		}
		groups = append(groups, map[string]any{"packageName": pkg, "appLabel": labels[pkg], "confidence": conf, "locations": locs, "relatedInstalledPackages": []string{}})
	}
	sort.Slice(groups, func(i, j int) bool { return residueGroupBytes(groups[i]) > residueGroupBytes(groups[j]) })
	return groups, evidenceOut
}

func residueGroupBytes(g map[string]any) int64 {
	var n int64
	for _, x := range g["locations"].([]map[string]any) {
		n += x["sizeBytes"].(int64)
	}
	return n
}
func countProtectedResidues(groups []map[string]any) int {
	n := 0
	for _, g := range groups {
		for _, x := range g["locations"].([]map[string]any) {
			if x["userProtected"].(bool) {
				n++
			}
		}
	}
	return n
}
func isPackageRootPath(path, pkg string, uid int) bool {
	clean := filepath.Clean(path)
	roots := []string{fmt.Sprintf("/data/user/%d/%s", uid, pkg), fmt.Sprintf("/data/user_de/%d/%s", uid, pkg), fmt.Sprintf("/storage/emulated/%d/Android/data/%s", uid, pkg), fmt.Sprintf("/storage/emulated/%d/Android/media/%s", uid, pkg), fmt.Sprintf("/storage/emulated/%d/Android/obb/%s", uid, pkg)}
	if uid == 0 {
		roots = append(roots, "/data/data/"+pkg)
	}
	for _, r := range roots {
		if clean == r {
			return true
		}
	}
	return false
}
func residueKind(path string) string {
	lower := strings.ToLower(path)
	switch {
	case strings.Contains(lower, "/android/data/"):
		return "EXTERNAL_DATA"
	case strings.Contains(lower, "/android/media/"):
		return "MEDIA"
	case strings.Contains(lower, "/android/obb/"):
		return "OBB"
	default:
		return "PRIVATE_DATA"
	}
}
func snapshotUserIDs(s *packageevidence.NormalizedSnapshot) []int {
	m := map[int]bool{}
	for _, r := range s.Records {
		m[r.UserID] = true
	}
	out := []int{}
	for u := range m {
		out = append(out, u)
	}
	sort.Ints(out)
	return out
}

func buildAdvancedView(entries []productEntry, apps map[string]*packageAccum) map[string]any {
	files := []productEntry{}
	for _, e := range entries {
		if !e.IsDirectory {
			files = append(files, e)
		}
	}
	dupBuckets := map[string][]productEntry{}
	for _, e := range files {
		if e.SizeBytes >= 1024*1024 {
			ext := strings.ToLower(filepath.Ext(e.Name))
			key := fmt.Sprintf("%d|%s", e.SizeBytes, ext)
			dupBuckets[key] = append(dupBuckets[key], e)
		}
	}
	dups := []map[string]any{}
	for _, g := range dupBuckets {
		if len(g) > 1 {
			paths := []string{}
			for _, e := range g {
				paths = append(paths, e.Path)
			}
			sort.Strings(paths)
			dups = append(dups, map[string]any{"sizeBytes": g[0].SizeBytes, "paths": paths, "estimatedSavingsBytes": g[0].SizeBytes * int64(len(g)-1)})
		}
	}
	sort.Slice(dups, func(i, j int) bool {
		return dups[i]["estimatedSavingsBytes"].(int64) > dups[j]["estimatedSavingsBytes"].(int64)
	})
	if len(dups) > 50 {
		dups = dups[:50]
	}
	now := time.Now().UnixMilli()
	old := []map[string]any{}
	for _, e := range files {
		m := normalizedEpochMillis(e.ModifiedAt)
		if e.SizeBytes >= 100*1024*1024 && m > 0 && now-m >= 180*24*time.Hour.Milliseconds() {
			old = append(old, map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "modifiedAt": m, "ageDays": (now - m) / (24 * time.Hour.Milliseconds())})
		}
	}
	sort.Slice(old, func(i, j int) bool { return old[i]["sizeBytes"].(int64) > old[j]["sizeBytes"].(int64) })
	if len(old) > 100 {
		old = old[:100]
	}
	modules := moduleFootprints(entries)
	typeBuckets := storageBucketsByLabel(files, func(e productEntry) string { return fileCategory(e.Name) })
	ageBuckets := storageBucketsByLabel(files, func(e productEntry) string {
		m := normalizedEpochMillis(e.ModifiedAt)
		if m <= 0 {
			return "Unknown"
		}
		d := (now - m) / (24 * time.Hour.Milliseconds())
		switch {
		case d < 30:
			return "< 30 days"
		case d < 180:
			return "30–179 days"
		case d < 365:
			return "180–364 days"
		default:
			return "≥ 1 year"
		}
	})
	mounts := readMountTopology()
	orphan := []map[string]any{}
	for _, a := range buildAppSummaries(apps) {
		if a["orphaned"].(bool) {
			orphan = append(orphan, a)
		}
	}
	return map[string]any{"duplicateCandidates": dups, "largeOldFiles": old, "moduleFootprints": modules, "typeBuckets": typeBuckets, "ageBuckets": ageBuckets, "mountTopology": mounts, "orphanedPackages": orphan}
}

func moduleFootprints(entries []productEntry) []map[string]any {
	by := map[string][]productEntry{}
	for _, e := range entries {
		if id := moduleID(e.Path); id != "" {
			by[id] = append(by[id], e)
		}
	}
	out := []map[string]any{}
	for id, g := range by {
		var bytes int64
		for _, e := range g {
			bytes += e.SizeBytes
		}
		out = append(out, map[string]any{"moduleId": id, "totalBytes": bytes, "entryCount": len(g)})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}
func storageBucketsByLabel(entries []productEntry, label func(productEntry) string) []map[string]any {
	type a struct {
		b int64
		n int
	}
	m := map[string]a{}
	for _, e := range entries {
		x := m[label(e)]
		x.b += e.SizeBytes
		x.n++
		m[label(e)] = x
	}
	out := []map[string]any{}
	for k, v := range m {
		out = append(out, map[string]any{"label": k, "bytes": v.b, "count": v.n})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["bytes"].(int64) > out[j]["bytes"].(int64) })
	return out
}
func fileCategory(name string) string {
	e := strings.ToLower(filepath.Ext(name))
	switch e {
	case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic":
		return "Images"
	case ".mp4", ".mkv", ".webm", ".mov", ".avi":
		return "Video"
	case ".mp3", ".flac", ".m4a", ".wav", ".ogg":
		return "Audio"
	case ".zip", ".7z", ".rar", ".tar", ".gz", ".xz", ".zst":
		return "Archives"
	case ".apk", ".apks", ".xapk":
		return "Installers"
	case ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt":
		return "Documents"
	default:
		return "Other"
	}
}
func normalizedEpochMillis(v int64) int64 {
	if v <= 0 {
		return 0
	}
	if v < 100000000000 {
		return v * 1000
	}
	return v
}
func readMountTopology() []map[string]any {
	raw, err := os.ReadFile("/proc/mounts")
	if err != nil {
		return []map[string]any{}
	}
	out := []map[string]any{}
	for _, line := range strings.Split(string(raw), "\n") {
		f := strings.Fields(line)
		if len(f) < 4 {
			continue
		}
		opts := "," + f[3] + ","
		out = append(out, map[string]any{"source": f[0], "target": f[1], "filesystem": f[2], "bind": strings.Contains(opts, ",bind,"), "overlay": f[2] == "overlay", "readOnly": strings.Contains(opts, ",ro,")})
	}
	return out
}

func buildPackageProfiles(apps map[string]*packageAccum, labels map[string]string) []map[string]any {
	out := []map[string]any{}
	for _, a := range apps {
		largest := append([]productEntry{}, a.Entries...)
		sort.Slice(largest, func(i, j int) bool { return largest[i].SizeBytes > largest[j].SizeBytes })
		if len(largest) > 8 {
			largest = largest[:8]
		}
		paths := []map[string]any{}
		for _, e := range largest {
			paths = append(paths, map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "category": packageRoleLabel(packageRole(e.Path))})
		}
		out = append(out, map[string]any{"packageName": a.Name, "appLabel": labels[a.Name], "installed": a.Installed, "totalBytes": a.Total, "privateBytes": a.Private, "cacheBytes": a.Cache, "codeBytes": a.Code, "databaseBytes": a.Database, "preferencesBytes": a.Preferences, "externalBytes": a.External, "obbBytes": a.OBB, "mediaBytes": a.Media, "logBytes": a.Logs, "duplicateCandidateBytes": int64(0), "largestPaths": paths})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}
func packageRoleLabel(role string) string {
	switch role {
	case "PRIVATE_DATA":
		return "Private data"
	case "CACHE":
		return "Cache"
	case "CODE_CACHE":
		return "Code cache"
	case "CODE":
		return "APK and native code"
	case "DATABASE":
		return "Databases"
	case "PREFERENCES":
		return "Preferences"
	case "EXTERNAL_DATA":
		return "External data"
	case "OBB":
		return "OBB"
	case "MEDIA":
		return "Media"
	case "LOG":
		return "Logs"
	default:
		return "Other"
	}
}

func buildAppStorageView(scanID string, previous *AndroidScanSession, apps, prev map[string]*packageAccum, labels map[string]string, ignored, protected map[string]bool, s *SQLiteStore) map[string]any {
	pkgs := []map[string]any{}
	for _, a := range apps {
		bucketMap := map[string][]map[string]any{}
		for _, e := range a.Entries {
			kind := appStorageBucket(e.Path)
			prot := protected[e.Path]
			eligible, reason := appStorageEligibility(a.Installed, kind, prot)
			item := map[string]any{"path": e.Path, "name": e.Name, "sizeBytes": e.SizeBytes, "modifiedAt": e.ModifiedAt, "bucket": kind, "protected": prot, "deletionEligible": eligible, "deletionReason": reason}
			bucketMap[kind] = append(bucketMap[kind], item)
		}
		buckets := []map[string]any{}
		for kind, items := range bucketMap {
			var bytes int64
			for _, x := range items {
				bytes += x["sizeBytes"].(int64)
			}
			buckets = append(buckets, map[string]any{"kind": kind, "bytes": bytes, "fileCount": len(items), "paths": items})
		}
		sort.Slice(buckets, func(i, j int) bool { return buckets[i]["bytes"].(int64) > buckets[j]["bytes"].(int64) })
		prevTotal := int64(0)
		if p := prev[a.Name]; p != nil {
			prevTotal = p.Total
		}
		pkgs = append(pkgs, map[string]any{"packageName": a.Name, "appLabel": map[bool]string{true: labels[a.Name], false: a.Name}[labels[a.Name] != ""], "installed": a.Installed, "systemApp": a.System, "versionName": nil, "versionCode": nil, "totalBytes": a.Total, "recentGrowthBytes": a.Total - prevTotal, "buckets": buckets, "duplicatePaths": []any{}, "residuePaths": []any{}, "recommendationsIgnored": ignored[a.Name]})
	}
	sort.Slice(pkgs, func(i, j int) bool { return pkgs[i]["totalBytes"].(int64) > pkgs[j]["totalBytes"].(int64) })
	var pid any = nil
	if previous != nil {
		pid = previous.ID
	}
	return map[string]any{"scanId": scanID, "previousScanId": pid, "packages": pkgs, "generatedAt": time.Now().UnixMilli()}
}
func appStorageBucket(path string) string {
	switch packageRole(path) {
	case "CODE":
		return "APP_CODE"
	case "PRIVATE_DATA":
		return "PRIVATE_DATA"
	case "DATABASE":
		return "DATABASES"
	case "PREFERENCES":
		return "PREFERENCES"
	case "CACHE":
		return "CACHE"
	case "CODE_CACHE":
		return "CODE_CACHE"
	case "EXTERNAL_DATA":
		return "EXTERNAL_DATA"
	case "OBB":
		return "OBB"
	case "MEDIA":
		return "SHARED_MEDIA"
	case "LOG":
		return "LOGS"
	default:
		return "OTHER"
	}
}
func appStorageEligibility(installed bool, kind string, protected bool) (bool, string) {
	if protected {
		return false, "Protected by the user"
	}
	if kind == "APP_CODE" {
		return false, "Installed application code is not a direct cleanup target"
	}
	if installed && (kind == "PRIVATE_DATA" || kind == "DATABASES" || kind == "PREFERENCES" || kind == "OTHER") {
		return false, "Installed app state requires app-managed cleanup"
	}
	return true, "Go package/storage evidence permits review"
}

func buildRecommendationView(scanID string, generation int64, entries []productEntry, residue []map[string]any, s *SQLiteStore) map[string]any {
	items := []map[string]any{}
	now := time.Now().UnixMilli()
	rows, err := s.db.Query(`SELECT g.id,g.logical_size_bytes,g.canonical_keep_path,c.path,c.content_hash FROM cleanup_duplicate_groups g JOIN cleanup_duplicate_candidates c ON c.group_id=g.id WHERE g.scan_id=? AND c.keep=0 AND c.verification_state='verified' ORDER BY g.logical_size_bytes DESC LIMIT 100`, scanID)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var id, keep, path, hash string
			var size int64
			if rows.Scan(&id, &size, &keep, &path, &hash) == nil {
				items = append(items, map[string]any{"id": "dup:" + id + ":" + path, "kind": "EXACT_DUPLICATES", "title": "Verified duplicate", "summary": "A verified retained copy exists at " + keep, "paths": []string{path}, "reclaimableBytes": size, "confidence": "VERIFIED", "risk": "LOW", "score": 100, "ageDays": nil, "evidence": []map[string]any{{"code": "RETAINED_COPY_VERIFIED", "label": "Retained copy verified", "detail": keep}}, "expectedSha256ByPath": map[string]string{path: hash}})
			}
		}
	}
	for _, g := range residue {
		if g["confidence"] != "HIGH" {
			continue
		}
		for _, x := range g["locations"].([]map[string]any) {
			if x["userProtected"].(bool) {
				continue
			}
			path := x["path"].(string)
			items = append(items, map[string]any{"id": "residue:" + g["packageName"].(string) + ":" + path, "kind": "APP_RESIDUE", "title": "App residue", "summary": "Package/user evidence reports this storage root as unowned; action-time recheck is still required.", "paths": []string{path}, "reclaimableBytes": x["sizeBytes"].(int64), "confidence": "HIGH", "risk": "MEDIUM", "score": 80, "ageDays": nil, "evidence": []map[string]any{{"code": "PACKAGE_ABSENT", "label": "Package absent", "detail": "Package/user evidence is Go-owned and rechecked before execution."}}, "expectedSha256ByPath": map[string]string{}})
		}
	}
	for _, e := range entries {
		if e.IsDirectory {
			continue
		}
		m := normalizedEpochMillis(e.ModifiedAt)
		age := int64(0)
		if m > 0 {
			age = (now - m) / (24 * time.Hour.Milliseconds())
		}
		kind := ""
		title := ""
		score := 0
		risk := "MEDIUM"
		confidence := "MEDIUM"
		if e.SizeBytes >= 500*1024*1024 && age >= 180 {
			kind = "LARGE_OLD_FILE"
			title = "Large old file"
			score = 55
			risk = "HIGH"
		} else if isInstaller(e.Name) && age >= 30 {
			kind = "STALE_INSTALLER"
			title = "Stale installer"
			score = 65
		} else if isArchive(e.Name) && age >= 180 {
			kind = "OLD_ARCHIVE"
			title = "Old archive"
			score = 50
		} else if (strings.HasSuffix(strings.ToLower(e.Name), ".log") || strings.Contains(strings.ToLower(e.Path), "/logs/")) && e.SizeBytes >= 100*1024*1024 {
			kind = "OVERSIZED_LOG"
			title = "Oversized log"
			score = 60
		}
		if kind != "" {
			items = append(items, map[string]any{"id": "scan:" + stableProductID(scanID, e.Path, kind), "kind": kind, "title": title, "summary": "Go scan evidence identifies a review candidate; no recommendation authorizes deletion.", "paths": []string{e.Path}, "reclaimableBytes": e.SizeBytes, "confidence": confidence, "risk": risk, "score": score, "ageDays": age, "evidence": []map[string]any{{"code": "SCAN_EVIDENCE", "label": "Scan evidence", "detail": "Candidate derived from generation-bound Go scan metadata."}}, "expectedSha256ByPath": map[string]string{}})
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i]["score"].(int) > items[j]["score"].(int) })
	if len(items) > 250 {
		items = items[:250]
	}
	return map[string]any{"scanId": scanID, "items": items, "generatedAt": now, "scanGeneration": generation}
}
func isInstaller(name string) bool {
	e := strings.ToLower(filepath.Ext(name))
	return e == ".apk" || e == ".apks" || e == ".xapk"
}
func isArchive(name string) bool {
	e := strings.ToLower(filepath.Ext(name))
	return e == ".zip" || e == ".7z" || e == ".rar" || e == ".tar" || e == ".gz" || e == ".xz" || e == ".zst"
}

func buildGrowthView(scanID string, currentSession *AndroidScanSession, previous *AndroidScanSession, current, old []AndroidScanEntry, history []AndroidScanSession) map[string]any {
	now := time.Now().UnixMilli()
	timeline := []map[string]any{}
	ordered := append([]AndroidScanSession{}, history...)
	sort.Slice(ordered, func(i, j int) bool { return ordered[i].StartedAt < ordered[j].StartedAt })
	var priorTotal *int64
	for _, s := range ordered {
		total := s.TotalBytes
		var delta any = nil
		if priorTotal != nil {
			delta = total - *priorTotal
		}
		completed := s.StartedAt
		if s.CompletedAt != nil {
			completed = *s.CompletedAt
		}
		timeline = append(timeline, map[string]any{"sessionId": s.ID, "completedAt": completed, "totalBytes": total, "deltaFromPreviousBytes": delta})
		priorTotal = &total
	}
	var previousID any = nil
	if previous != nil {
		previousID = previous.ID
	}
	if currentSession == nil || previous == nil || currentSession.RootPath != previous.RootPath || !growthComparable(currentSession.IntegrityStatus) || !growthComparable(previous.IntegrityStatus) {
		return map[string]any{"scanId": scanID, "previousScanId": previousID, "currentSession": currentSession, "previousSession": previous, "items": []any{}, "timeline": timeline, "insights": []any{}, "generatedAt": now}
	}
	oldMap := map[string]AndroidScanEntry{}
	curMap := map[string]AndroidScanEntry{}
	for _, e := range old {
		if !e.IsDirectory {
			oldMap[e.Path] = e
		}
	}
	for _, e := range current {
		if !e.IsDirectory {
			curMap[e.Path] = e
		}
	}
	items := []map[string]any{}
	for p, e := range curMap {
		o, ok := oldMap[p]
		if !ok {
			items = append(items, growthItem(p, e.SizeBytes, 0, "ADDED", nil, &e))
		} else if e.SizeBytes != o.SizeBytes {
			kind := "GROWN"
			if e.SizeBytes < o.SizeBytes {
				kind = "SHRUNK"
			}
			oCopy, eCopy := o, e
			items = append(items, growthItem(p, e.SizeBytes, o.SizeBytes, kind, &oCopy, &eCopy))
		}
	}
	for p, e := range oldMap {
		if _, ok := curMap[p]; !ok {
			eCopy := e
			items = append(items, growthItem(p, 0, e.SizeBytes, "REMOVED", &eCopy, nil))
		}
	}
	sort.Slice(items, func(i, j int) bool {
		return abs64(items[i]["deltaBytes"].(int64)) > abs64(items[j]["deltaBytes"].(int64))
	})
	insights := buildGrowthInsights(items)
	return map[string]any{"scanId": scanID, "previousScanId": previousID, "currentSession": currentSession, "previousSession": previous, "items": items, "timeline": timeline, "insights": insights, "generatedAt": now}
}
func growthComparable(status string) bool {
	return status == "COMPLETE" || status == "COMPLETE_WITH_WARNINGS"
}
func buildGrowthInsights(items []map[string]any) []map[string]any {
	out := []map[string]any{}
	for _, item := range items {
		delta := item["deltaBytes"].(int64)
		path := item["path"].(string)
		kind := item["kind"].(string)
		if delta >= 100*1024*1024 {
			ik := "NEW_LARGE_FILE"
			title := "New large file"
			if kind == "GROWN" {
				ik = "CROSSED_LARGE_THRESHOLD"
				title = "Large storage growth"
			}
			out = append(out, map[string]any{"id": stableProductID("growth", path, kind), "kind": ik, "title": title, "detail": "Go scan history recorded significant growth at " + path, "deltaBytes": delta, "currentPaths": []string{path}})
		}
		if len(out) >= 20 {
			break
		}
	}
	return out
}

func growthItem(path string, current, previous int64, kind string, previousEntry, currentEntry *AndroidScanEntry) map[string]any {
	return map[string]any{"path": path, "name": filepath.Base(path), "kind": kind, "group": "FILE", "currentBytes": current, "previousBytes": previous, "deltaBytes": current - previous, "previousEntry": previousEntry, "currentEntry": currentEntry}
}
func abs64(v int64) int64 {
	if v < 0 {
		return -v
	}
	return v
}

func buildModuleView(session *AndroidScanSession, entries []productEntry) map[string]any {
	by := map[string][]productEntry{}
	for _, e := range entries {
		if id := moduleID(e.Path); id != "" {
			by[id] = append(by[id], e)
		}
	}
	profiles := []map[string]any{}
	targetSources := map[string][]map[string]any{}
	for id, g := range by {
		paths := map[string]bool{}
		var bytes int64
		targets := []string{}
		largest := append([]productEntry{}, g...)
		sort.Slice(largest, func(i, j int) bool { return largest[i].SizeBytes > largest[j].SizeBytes })
		if len(largest) > 6 {
			largest = largest[:6]
		}
		lp := []map[string]any{}
		for _, e := range g {
			paths[e.Path] = true
			bytes += e.SizeBytes
			if t := moduleOverlayTarget(e.Path, id); t != "" {
				targets = append(targets, t)
				targetSources[t] = append(targetSources[t], map[string]any{"moduleId": id, "path": e.Path, "sizeBytes": e.SizeBytes, "category": moduleEntryKind(e.Path, id)})
			}
		}
		for _, e := range largest {
			lp = append(lp, map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "category": moduleEntryKind(e.Path, id)})
		}
		targets = uniqueStrings(targets)
		sort.Strings(targets)
		profiles = append(profiles, map[string]any{"moduleId": id, "totalBytes": bytes, "entryCount": len(g), "hasModuleProp": hasSuffixPath(paths, "/module.prop"), "hasServiceScript": hasSuffixPath(paths, "/service.sh"), "hasPostFsDataScript": hasSuffixPath(paths, "/post-fs-data.sh"), "hasWebUi": hasContainsPath(paths, "/webroot/") || hasContainsPath(paths, "/webui/"), "disabled": hasSuffixPath(paths, "/disable"), "updatePending": hasSuffixPath(paths, "/update") || hasSuffixPath(paths, "/remove"), "overlayTargets": targets, "largestPaths": lp})
	}
	sort.Slice(profiles, func(i, j int) bool { return profiles[i]["totalBytes"].(int64) > profiles[j]["totalBytes"].(int64) })
	conflicts := []map[string]any{}
	for t, sources := range targetSources {
		mods := map[string]bool{}
		src := []map[string]any{}
		for _, x := range sources {
			mods[x["moduleId"].(string)] = true
			src = append(src, map[string]any{"path": x["path"], "sizeBytes": x["sizeBytes"], "category": x["category"]})
		}
		if len(mods) > 1 {
			ids := []string{}
			for id := range mods {
				ids = append(ids, id)
			}
			sort.Strings(ids)
			conflicts = append(conflicts, map[string]any{"targetPath": t, "moduleIds": ids, "sourcePaths": src})
		}
	}
	sort.Slice(conflicts, func(i, j int) bool { return conflicts[i]["targetPath"].(string) < conflicts[j]["targetPath"].(string) })
	status := "READY"
	message := "Module analysis is ready"
	includes := session.RootPath == "/data/adb/modules" || strings.HasPrefix("/data/adb/modules/", strings.TrimRight(session.RootPath, "/")+"/")
	if !includes {
		status = "WRONG_SCAN_ROOT"
		message = "Current scan does not include /data/adb/modules"
	} else if len(profiles) == 0 {
		status = "NO_MODULES_FOUND"
		message = "No root modules were found in this scan"
	}
	return map[string]any{"modules": profiles, "overlayConflicts": conflicts, "diagnostic": map[string]any{"status": status, "message": message, "currentScanRoot": session.RootPath, "expectedRoot": "/data/adb/modules", "scanIncludesModules": includes, "modulePathAccessible": nil, "rootCode": nil}}
}
func moduleID(path string) string {
	const prefix = "/data/adb/modules/"
	if !strings.HasPrefix(path, prefix) {
		return ""
	}
	return strings.Split(strings.TrimPrefix(path, prefix), "/")[0]
}
func moduleOverlayTarget(path, id string) string {
	prefix := "/data/adb/modules/" + id + "/"
	if !strings.HasPrefix(path, prefix) {
		return ""
	}
	rel := strings.TrimPrefix(path, prefix)
	first := strings.Split(rel, "/")[0]
	switch first {
	case "system", "system_ext", "vendor", "product", "odm":
		return "/" + rel
	}
	return ""
}
func moduleEntryKind(path, id string) string {
	switch {
	case strings.HasSuffix(path, "/module.prop"):
		return "Metadata"
	case strings.HasSuffix(path, "/service.sh") || strings.HasSuffix(path, "/post-fs-data.sh"):
		return "Boot script"
	case strings.Contains(path, "/webroot/") || strings.Contains(path, "/webui/"):
		return "WebUI"
	case moduleOverlayTarget(path, id) != "":
		return "System overlay"
	default:
		return "Module file"
	}
}
func hasSuffixPath(m map[string]bool, s string) bool {
	for p := range m {
		if strings.HasSuffix(p, s) {
			return true
		}
	}
	return false
}
func hasContainsPath(m map[string]bool, s string) bool {
	for p := range m {
		if strings.Contains(p, s) {
			return true
		}
	}
	return false
}
func uniqueStrings(in []string) []string {
	m := map[string]bool{}
	out := []string{}
	for _, v := range in {
		if !m[v] {
			m[v] = true
			out = append(out, v)
		}
	}
	return out
}
func stringSet(in []string) map[string]bool {
	m := map[string]bool{}
	for _, v := range in {
		m[v] = true
	}
	return m
}

// Ensure imported database/sql remains part of the product-view contract as queries
// evolve; product views intentionally run at the durable authority boundary.
var _ = sql.ErrNoRows
