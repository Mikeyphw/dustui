package state

import (
	"context"
	"database/sql"
	"fmt"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

const (
	productLargestPathsPerPackage = 8
	productPathsPerBucket         = 24
	productResidueLimit           = 500
	productOldFileLimit           = 100
	productGrowthLimit            = 500
	productRecommendationLimit    = 250
	productModuleLimit            = 512
)

type boundedBucketAccum struct {
	Bytes int64
	Count int
	Paths []productEntry
}

type boundedPackageAccum struct {
	Name                                                                           string
	Installed                                                                      bool
	System                                                                         bool
	EntryCount                                                                     int
	Total, Private, Cache, Code, Database, Preferences, External, OBB, Media, Logs int64
	Largest                                                                        []productEntry
	Buckets                                                                        map[string]*boundedBucketAccum
}

type boundedModuleAccum struct {
	ID          string
	Total       int64
	Count       int
	Paths       map[string]bool
	Targets     map[string]bool
	Largest     []productEntry
	TargetPaths map[string][]productEntry
}

type boundedProductAccumulator struct {
	Apps      map[string]*boundedPackageAccum
	TypeBytes map[string]int64
	TypeCount map[string]int
	AgeBytes  map[string]int64
	AgeCount  map[string]int
	OldFiles  []map[string]any
	Modules   map[string]*boundedModuleAccum
	Residue   map[string][]map[string]any
	ResidueEv []intelligence.ResidueEvidence
	ResidueN  int
}

func (s *SQLiteStore) productInsightsBounded(ctx context.Context, scanID string, options ProductInsightsOptions) (map[string]any, error) {
	session, err := s.AndroidScanSessionByID(ctx, scanID)
	if err != nil || session == nil {
		return nil, fmt.Errorf("scan_not_found:%s", scanID)
	}
	completed, err := s.CompletedAndroidScanSessions(ctx, 100)
	if err != nil {
		return nil, err
	}
	var previous *AndroidScanSession
	for i := range completed {
		if completed[i].ID != scanID {
			previous = &completed[i]
			break
		}
	}
	snapshot, err := s.LatestPackageEvidence(ctx)
	if err != nil {
		return nil, err
	}
	if snapshot == nil {
		snapshot = &packageevidence.NormalizedSnapshot{}
	}
	inventoryComplete := s.packageInventoryComplete(ctx, snapshot.SnapshotID)
	protected := stringSet(options.ProtectedPaths)
	ignored := stringSet(options.IgnoredPackages)

	acc := newBoundedProductAccumulator()
	if err := s.streamAnnotatedEntries(ctx, scanID, snapshot, func(e productEntry) error {
		acc.add(e, snapshot, inventoryComplete, protected, session.Generation, scanID, options.AppLabels)
		return nil
	}); err != nil {
		return nil, err
	}
	if len(acc.ResidueEv) > 0 {
		if err := s.PersistResidueEvidence(ctx, acc.ResidueEv); err != nil {
			return nil, err
		}
	}

	previousTotals := map[string]int64{}
	if previous != nil {
		if err := s.streamAnnotatedEntries(ctx, previous.ID, snapshot, func(e productEntry) error {
			if e.Package != "" {
				previousTotals[e.Package] += e.SizeBytes
			}
			return nil
		}); err != nil {
			return nil, err
		}
	}

	residueGroups := boundedResidueGroups(acc.Residue, inventoryComplete, options.AppLabels)
	recommendations, err := s.boundedRecommendationView(ctx, scanID, session.Generation, residueGroups)
	if err != nil {
		return nil, err
	}
	growth, err := s.boundedGrowthView(ctx, scanID, session, previous, completed)
	if err != nil {
		return nil, err
	}
	snapshotDelta, err := s.boundedSnapshotDelta(ctx, scanID, previous)
	if err != nil {
		return nil, err
	}
	duplicateCandidates, err := s.boundedDuplicateCandidates(ctx, scanID)
	if err != nil {
		return nil, err
	}

	appSummaries := boundedAppSummaries(acc.Apps)
	advanced := map[string]any{
		"duplicateCandidates": duplicateCandidates,
		"largeOldFiles":       acc.OldFiles,
		"moduleFootprints":    boundedModuleFootprints(acc.Modules),
		"typeBuckets":         boundedBuckets(acc.TypeBytes, acc.TypeCount),
		"ageBuckets":          boundedBuckets(acc.AgeBytes, acc.AgeCount),
		"mountTopology":       readMountTopology(),
		"orphanedPackages":    filterOrphanedSummaries(appSummaries),
	}

	return map[string]any{
		"scanId":          scanID,
		"generation":      session.Generation,
		"snapshotDelta":   snapshotDelta,
		"appSummaries":    appSummaries,
		"advanced":        advanced,
		"packageProfiles": boundedPackageProfiles(acc.Apps, options.AppLabels),
		"residue": map[string]any{
			"scanId": scanID, "inventoryComplete": inventoryComplete, "checkedUsers": snapshotUserIDs(snapshot),
			"groups": residueGroups, "protectedLocationCount": countProtectedResidues(residueGroups), "generatedAt": time.Now().UnixMilli(),
			"scanGeneration": session.Generation,
			"message":        map[bool]string{true: "", false: "Package/user inventory is partial; ambiguous residue is non-actionable."}[inventoryComplete],
			"bounded":        true, "candidateLimit": productResidueLimit,
		},
		"appStorage":      boundedAppStorageView(scanID, previous, acc.Apps, previousTotals, options.AppLabels, ignored, protected),
		"recommendations": recommendations,
		"growth":          growth,
		"modules":         boundedModuleView(session, acc.Modules),
	}, nil
}

func newBoundedProductAccumulator() *boundedProductAccumulator {
	return &boundedProductAccumulator{
		Apps: map[string]*boundedPackageAccum{}, TypeBytes: map[string]int64{}, TypeCount: map[string]int{},
		AgeBytes: map[string]int64{}, AgeCount: map[string]int{}, Modules: map[string]*boundedModuleAccum{}, Residue: map[string][]map[string]any{},
	}
}

func (s *SQLiteStore) streamAnnotatedEntries(ctx context.Context, scanID string, snapshot *packageevidence.NormalizedSnapshot, fn func(productEntry) error) error {
	rows, err := s.db.QueryContext(ctx, "SELECT "+androidScanEntryColumns+" FROM scan_entries WHERE scan_id=? ORDER BY path COLLATE NOCASE", scanID)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		if err := ctx.Err(); err != nil {
			return err
		}
		e, err := scanAndroidEntry(rows)
		if err != nil {
			return err
		}
		p := annotateProductEntry(e, snapshot)
		if err := fn(p); err != nil {
			return err
		}
	}
	return rows.Err()
}

func annotateProductEntry(e AndroidScanEntry, snapshot *packageevidence.NormalizedSnapshot) productEntry {
	pkg, uid := packageIdentityFromPath(e.Path)
	if pkg == "" {
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
	return productEntry{AndroidScanEntry: e, Package: pkg, UserID: uid}
}

func (a *boundedProductAccumulator) add(e productEntry, snapshot *packageevidence.NormalizedSnapshot, inventoryComplete bool, protected map[string]bool, generation int64, scanID string, labels map[string]string) {
	if e.Package != "" {
		p := a.Apps[e.Package]
		if p == nil {
			installed, system := packageInstallState(snapshot, e.Package)
			p = &boundedPackageAccum{Name: e.Package, Installed: installed, System: system, Buckets: map[string]*boundedBucketAccum{}}
			a.Apps[e.Package] = p
		}
		p.EntryCount++
		p.Total += e.SizeBytes
		role := packageRole(e.Path)
		switch role {
		case "PRIVATE_DATA":
			p.Private += e.SizeBytes
		case "CACHE", "CODE_CACHE":
			p.Cache += e.SizeBytes
		case "CODE":
			p.Code += e.SizeBytes
		case "DATABASE":
			p.Database += e.SizeBytes
		case "PREFERENCES":
			p.Preferences += e.SizeBytes
		case "EXTERNAL_DATA":
			p.External += e.SizeBytes
		case "OBB":
			p.OBB += e.SizeBytes
		case "MEDIA":
			p.Media += e.SizeBytes
		case "LOG":
			p.Logs += e.SizeBytes
		}
		p.Largest = boundedTopEntries(p.Largest, e, productLargestPathsPerPackage)
		kind := appStorageBucket(e.Path)
		b := p.Buckets[kind]
		if b == nil {
			b = &boundedBucketAccum{}
			p.Buckets[kind] = b
		}
		b.Bytes += e.SizeBytes
		b.Count++
		b.Paths = boundedTopEntries(b.Paths, e, productPathsPerBucket)
	}

	if !e.IsDirectory {
		fc := fileCategory(e.Name)
		a.TypeBytes[fc] += e.SizeBytes
		a.TypeCount[fc]++
		age := boundedAgeLabel(e.ModifiedAt)
		a.AgeBytes[age] += e.SizeBytes
		a.AgeCount[age]++
		m := normalizedEpochMillis(e.ModifiedAt)
		now := time.Now().UnixMilli()
		if e.SizeBytes >= 100*1024*1024 && m > 0 && now-m >= 180*24*time.Hour.Milliseconds() {
			candidate := map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "modifiedAt": m, "ageDays": (now - m) / (24 * time.Hour.Milliseconds())}
			a.OldFiles = boundedTopMaps(a.OldFiles, candidate, productOldFileLimit, "sizeBytes")
		}
	}

	if id := moduleID(e.Path); id != "" {
		if m := a.Modules[id]; m != nil || len(a.Modules) < productModuleLimit {
			if m == nil {
				m = &boundedModuleAccum{ID: id, Paths: map[string]bool{}, Targets: map[string]bool{}, TargetPaths: map[string][]productEntry{}}
				a.Modules[id] = m
			}
			m.Total += e.SizeBytes
			m.Count++
			m.Paths[e.Path] = true
			m.Largest = boundedTopEntries(m.Largest, e, 6)
			if t := moduleOverlayTarget(e.Path, id); t != "" {
				m.Targets[t] = true
				m.TargetPaths[t] = boundedTopEntries(m.TargetPaths[t], e, 8)
			}
		}
	}

	a.addResidue(e, snapshot, inventoryComplete, protected, generation, scanID, labels)
}

func (a *boundedProductAccumulator) addResidue(e productEntry, snapshot *packageevidence.NormalizedSnapshot, inventoryComplete bool, protected map[string]bool, generation int64, scanID string, labels map[string]string) {
	if !e.IsDirectory || e.Package == "" || a.ResidueN >= productResidueLimit {
		return
	}
	pkg, uid := packageIdentityFromPath(e.Path)
	if pkg == "" || pkg != e.Package || uid != e.UserID || !isPackageRootPath(e.Path, pkg, uid) {
		return
	}
	key := packageevidence.PackageKey(pkg, uid)
	for _, r := range snapshot.Records {
		if r.Key == key && r.Installed {
			return
		}
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
	a.Residue[pkg] = append(a.Residue[pkg], loc)
	a.ResidueN++
	actionable := cleanup.ActionablePathEvidence{Path: e.Path, ScanID: scanID, ScanGeneration: generation, SourceSurface: "residue", SourceKind: "package_user_evidence", ExpectedHash: snapshot.EvidenceHash, ExpectedIdentity: key, PackageName: pkg, UserHandle: fmt.Sprint(uid), Bucket: strings.ToLower(kind), ProtectedState: map[bool]string{true: "protected", false: cleanup.ProtectedStateUnprotected}[isProtected], DeletionEligible: inventoryComplete && !isProtected, Reason: "package_absent_recheck_required", LogicalSizeBytes: e.SizeBytes, AllocatedSizeBytes: e.AllocatedBytes}
	candidate := packageevidence.ResidueCandidate{PackageKey: key, Path: e.Path, EvidenceHash: snapshot.EvidenceHash, PackageName: pkg, UserID: uid, Eligibility: map[bool]string{true: "eligible_after_recheck", false: "unavailable"}[inventoryComplete], RequiresRecheck: true, RecheckReasons: []string{"package_state_must_match_at_action_time"}, BlocksDeletion: !inventoryComplete || isProtected, DerivedFromRegex: false, CollectionCompleteness: map[bool]string{true: packageevidence.CompletenessSupported, false: packageevidence.CompletenessPartial}[inventoryComplete]}
	if err := actionable.Validate(); err == nil {
		a.ResidueEv = append(a.ResidueEv, intelligence.ResidueEvidence{Candidate: candidate, Confidence: map[bool]string{true: intelligence.ConfidenceCandidate, false: intelligence.ConfidenceAmbiguous}[inventoryComplete], SpecialContext: kind, Evidence: actionable})
	}
	_ = labels
}

func packageInstallState(snapshot *packageevidence.NormalizedSnapshot, pkg string) (bool, bool) {
	for _, r := range snapshot.Records {
		if r.PackageName == pkg && r.UserID == 0 {
			return r.Installed, r.SystemApp
		}
	}
	return false, false
}

func boundedTopEntries(in []productEntry, e productEntry, limit int) []productEntry {
	in = append(in, e)
	sort.Slice(in, func(i, j int) bool {
		if in[i].SizeBytes != in[j].SizeBytes {
			return in[i].SizeBytes > in[j].SizeBytes
		}
		return in[i].Path < in[j].Path
	})
	if len(in) > limit {
		in = in[:limit]
	}
	return in
}

func boundedTopMaps(in []map[string]any, value map[string]any, limit int, key string) []map[string]any {
	in = append(in, value)
	sort.Slice(in, func(i, j int) bool { return in[i][key].(int64) > in[j][key].(int64) })
	if len(in) > limit {
		in = in[:limit]
	}
	return in
}

func boundedAgeLabel(modified int64) string {
	m := normalizedEpochMillis(modified)
	if m <= 0 {
		return "Unknown"
	}
	days := (time.Now().UnixMilli() - m) / (24 * time.Hour.Milliseconds())
	switch {
	case days < 30:
		return "< 30 days"
	case days < 180:
		return "30–179 days"
	case days < 365:
		return "180–364 days"
	default:
		return "≥ 1 year"
	}
}

func boundedBuckets(bytes map[string]int64, counts map[string]int) []map[string]any {
	out := make([]map[string]any, 0, len(bytes))
	for k, b := range bytes {
		out = append(out, map[string]any{"label": k, "bytes": b, "count": counts[k]})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["bytes"].(int64) > out[j]["bytes"].(int64) })
	return out
}

func boundedAppSummaries(apps map[string]*boundedPackageAccum) []map[string]any {
	out := []map[string]any{}
	for _, a := range apps {
		out = append(out, map[string]any{"packageName": a.Name, "totalBytes": a.Total, "privateBytes": a.Private, "cacheBytes": a.Cache, "externalBytes": a.External + a.OBB + a.Media, "entryCount": a.EntryCount, "orphaned": !a.Installed})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}

func filterOrphanedSummaries(all []map[string]any) []map[string]any {
	out := []map[string]any{}
	for _, v := range all {
		if v["orphaned"].(bool) {
			out = append(out, v)
		}
	}
	return out
}

func boundedPackageProfiles(apps map[string]*boundedPackageAccum, labels map[string]string) []map[string]any {
	out := []map[string]any{}
	for _, a := range apps {
		paths := []map[string]any{}
		for _, e := range a.Largest {
			paths = append(paths, map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "category": packageRoleLabel(packageRole(e.Path))})
		}
		out = append(out, map[string]any{"packageName": a.Name, "appLabel": labels[a.Name], "installed": a.Installed, "totalBytes": a.Total, "privateBytes": a.Private, "cacheBytes": a.Cache, "codeBytes": a.Code, "databaseBytes": a.Database, "preferencesBytes": a.Preferences, "externalBytes": a.External, "obbBytes": a.OBB, "mediaBytes": a.Media, "logBytes": a.Logs, "duplicateCandidateBytes": int64(0), "largestPaths": paths})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}

func boundedAppStorageView(scanID string, previous *AndroidScanSession, apps map[string]*boundedPackageAccum, previousTotals map[string]int64, labels map[string]string, ignored, protected map[string]bool) map[string]any {
	pkgs := []map[string]any{}
	for _, a := range apps {
		buckets := []map[string]any{}
		for kind, b := range a.Buckets {
			items := []map[string]any{}
			for _, e := range b.Paths {
				prot := protected[e.Path]
				eligible, reason := appStorageEligibility(a.Installed, kind, prot)
				items = append(items, map[string]any{"path": e.Path, "name": e.Name, "sizeBytes": e.SizeBytes, "modifiedAt": e.ModifiedAt, "bucket": kind, "protected": prot, "deletionEligible": eligible && b.Count <= productPathsPerBucket, "deletionReason": map[bool]string{true: reason, false: "Bucket is larger than the bounded review window; refine before deletion"}[b.Count <= productPathsPerBucket]})
			}
			buckets = append(buckets, map[string]any{"kind": kind, "bytes": b.Bytes, "fileCount": b.Count, "paths": items, "pathsTruncated": b.Count > len(b.Paths)})
		}
		sort.Slice(buckets, func(i, j int) bool { return buckets[i]["bytes"].(int64) > buckets[j]["bytes"].(int64) })
		label := labels[a.Name]
		if label == "" {
			label = a.Name
		}
		pkgs = append(pkgs, map[string]any{"packageName": a.Name, "appLabel": label, "installed": a.Installed, "systemApp": a.System, "versionName": nil, "versionCode": nil, "totalBytes": a.Total, "recentGrowthBytes": a.Total - previousTotals[a.Name], "buckets": buckets, "duplicatePaths": []any{}, "residuePaths": []any{}, "recommendationsIgnored": ignored[a.Name]})
	}
	sort.Slice(pkgs, func(i, j int) bool { return pkgs[i]["totalBytes"].(int64) > pkgs[j]["totalBytes"].(int64) })
	var pid any = nil
	if previous != nil {
		pid = previous.ID
	}
	return map[string]any{"scanId": scanID, "previousScanId": pid, "packages": pkgs, "generatedAt": time.Now().UnixMilli(), "boundedPathsPerBucket": productPathsPerBucket}
}

func boundedResidueGroups(byPkg map[string][]map[string]any, complete bool, labels map[string]string) []map[string]any {
	groups := []map[string]any{}
	for pkg, locs := range byPkg {
		conf := "UNCERTAIN"
		if complete {
			conf = "HIGH"
		}
		groups = append(groups, map[string]any{"packageName": pkg, "appLabel": labels[pkg], "confidence": conf, "locations": locs, "relatedInstalledPackages": []string{}})
	}
	sort.Slice(groups, func(i, j int) bool { return residueGroupBytes(groups[i]) > residueGroupBytes(groups[j]) })
	return groups
}

func boundedModuleFootprints(mods map[string]*boundedModuleAccum) []map[string]any {
	out := []map[string]any{}
	for _, m := range mods {
		out = append(out, map[string]any{"moduleId": m.ID, "totalBytes": m.Total, "entryCount": m.Count})
	}
	sort.Slice(out, func(i, j int) bool { return out[i]["totalBytes"].(int64) > out[j]["totalBytes"].(int64) })
	return out
}

func boundedModuleView(session *AndroidScanSession, mods map[string]*boundedModuleAccum) map[string]any {
	profiles := []map[string]any{}
	targetSources := map[string][]map[string]any{}
	for _, m := range mods {
		targets := []string{}
		for t := range m.Targets {
			targets = append(targets, t)
			for _, e := range m.TargetPaths[t] {
				targetSources[t] = append(targetSources[t], map[string]any{"moduleId": m.ID, "path": e.Path, "sizeBytes": e.SizeBytes, "category": moduleEntryKind(e.Path, m.ID)})
			}
		}
		sort.Strings(targets)
		lp := []map[string]any{}
		for _, e := range m.Largest {
			lp = append(lp, map[string]any{"path": e.Path, "sizeBytes": e.SizeBytes, "category": moduleEntryKind(e.Path, m.ID)})
		}
		profiles = append(profiles, map[string]any{"moduleId": m.ID, "totalBytes": m.Total, "entryCount": m.Count, "hasModuleProp": hasSuffixPath(m.Paths, "/module.prop"), "hasServiceScript": hasSuffixPath(m.Paths, "/service.sh"), "hasPostFsDataScript": hasSuffixPath(m.Paths, "/post-fs-data.sh"), "hasWebUi": hasContainsPath(m.Paths, "/webroot/") || hasContainsPath(m.Paths, "/webui/"), "disabled": hasSuffixPath(m.Paths, "/disable"), "updatePending": hasSuffixPath(m.Paths, "/update") || hasSuffixPath(m.Paths, "/remove"), "overlayTargets": targets, "largestPaths": lp})
	}
	sort.Slice(profiles, func(i, j int) bool { return profiles[i]["totalBytes"].(int64) > profiles[j]["totalBytes"].(int64) })
	conflicts := []map[string]any{}
	for t, sources := range targetSources {
		ids := map[string]bool{}
		src := []map[string]any{}
		for _, x := range sources {
			ids[x["moduleId"].(string)] = true
			src = append(src, map[string]any{"path": x["path"], "sizeBytes": x["sizeBytes"], "category": x["category"]})
		}
		if len(ids) > 1 {
			names := []string{}
			for id := range ids {
				names = append(names, id)
			}
			sort.Strings(names)
			conflicts = append(conflicts, map[string]any{"targetPath": t, "moduleIds": names, "sourcePaths": src})
		}
	}
	status := "READY"
	message := fmt.Sprintf("Go indexed %d root module(s).", len(profiles))
	includes := false
	if session != nil {
		includes = session.RootPath == "/data/adb/modules" || strings.HasPrefix("/data/adb/modules/", strings.TrimRight(session.RootPath, "/")+"/")
		if !includes {
			status = "WRONG_SCAN_ROOT"
			message = "Current scan does not include /data/adb/modules."
		}
	}
	return map[string]any{"modules": profiles, "overlayConflicts": conflicts, "diagnostic": map[string]any{"status": status, "message": message, "currentScanRoot": session.RootPath, "expectedRoot": "/data/adb/modules", "scanIncludesModules": includes}}
}

func (s *SQLiteStore) boundedSnapshotDelta(ctx context.Context, scanID string, previous *AndroidScanSession) (map[string]any, error) {
	var previousID any = nil
	if previous != nil {
		previousID = previous.ID
	} else {
		return map[string]any{"currentScanId": scanID, "previousScanId": nil, "totalDeltaBytes": int64(0), "addedBytes": int64(0), "removedBytes": int64(0), "changedEntries": 0}, nil
	}
	var curTotal, oldTotal int64
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(SUM(logical_size_bytes),0) FROM scan_entries WHERE scan_id=?`, scanID).Scan(&curTotal); err != nil {
		return nil, err
	}
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(SUM(logical_size_bytes),0) FROM scan_entries WHERE scan_id=?`, previous.ID).Scan(&oldTotal); err != nil {
		return nil, err
	}
	var added, removed int64
	var changed int
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(SUM(c.logical_size_bytes),0) FROM scan_entries c LEFT JOIN scan_entries p ON p.scan_id=? AND p.path=c.path WHERE c.scan_id=? AND c.is_directory=0 AND p.path IS NULL`, previous.ID, scanID).Scan(&added); err != nil {
		return nil, err
	}
	if err := s.db.QueryRowContext(ctx, `SELECT COALESCE(SUM(p.logical_size_bytes),0) FROM scan_entries p LEFT JOIN scan_entries c ON c.scan_id=? AND c.path=p.path WHERE p.scan_id=? AND p.is_directory=0 AND c.path IS NULL`, scanID, previous.ID).Scan(&removed); err != nil {
		return nil, err
	}
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM scan_entries c JOIN scan_entries p ON p.scan_id=? AND p.path=c.path WHERE c.scan_id=? AND c.is_directory=0 AND p.logical_size_bytes<>c.logical_size_bytes`, previous.ID, scanID).Scan(&changed); err != nil {
		return nil, err
	}
	return map[string]any{"currentScanId": scanID, "previousScanId": previousID, "totalDeltaBytes": curTotal - oldTotal, "addedBytes": added, "removedBytes": removed, "changedEntries": changed}, nil
}

func (s *SQLiteStore) boundedDuplicateCandidates(ctx context.Context, scanID string) ([]map[string]any, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT logical_size_bytes,COUNT(*) FROM scan_entries WHERE scan_id=? AND is_directory=0 AND logical_size_bytes>=1048576 GROUP BY logical_size_bytes HAVING COUNT(*)>1 ORDER BY logical_size_bytes*(COUNT(*)-1) DESC LIMIT 50`, scanID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []map[string]any{}
	for rows.Next() {
		var size int64
		var count int
		if err := rows.Scan(&size, &count); err != nil {
			return nil, err
		}
		p, err := s.db.QueryContext(ctx, `SELECT path FROM scan_entries WHERE scan_id=? AND is_directory=0 AND logical_size_bytes=? ORDER BY path COLLATE NOCASE LIMIT 16`, scanID, size)
		if err != nil {
			return nil, err
		}
		paths := []string{}
		for p.Next() {
			var path string
			if p.Scan(&path) == nil {
				paths = append(paths, path)
			}
		}
		p.Close()
		out = append(out, map[string]any{"sizeBytes": size, "paths": paths, "estimatedSavingsBytes": size * int64(count-1)})
	}
	return out, rows.Err()
}

func (s *SQLiteStore) boundedRecommendationView(ctx context.Context, scanID string, generation int64, residue []map[string]any) (map[string]any, error) {
	items := []map[string]any{}
	now := time.Now().UnixMilli()
	rows, err := s.db.QueryContext(ctx, `SELECT g.id,g.logical_size_bytes,g.canonical_keep_path,c.path,c.content_hash FROM cleanup_duplicate_groups g JOIN cleanup_duplicate_candidates c ON c.group_id=g.id WHERE g.scan_id=? AND g.scan_generation=? AND c.keep=0 AND c.verification_state='verified' ORDER BY g.logical_size_bytes DESC LIMIT 100`, scanID, generation)
	if err == nil {
		for rows.Next() {
			var id, keep, path, hash string
			var size int64
			if rows.Scan(&id, &size, &keep, &path, &hash) == nil {
				items = append(items, map[string]any{"id": "dup:" + id + ":" + path, "kind": "EXACT_DUPLICATES", "title": "Verified duplicate", "summary": "A verified retained copy exists at " + keep, "paths": []string{path}, "reclaimableBytes": size, "confidence": "VERIFIED", "risk": "LOW", "score": 100, "ageDays": nil, "evidence": []map[string]any{{"code": "RETAINED_COPY_VERIFIED", "label": "Retained copy verified", "detail": keep}}, "expectedSha256ByPath": map[string]string{path: hash}})
			}
		}
		rows.Close()
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
			if len(items) >= productRecommendationLimit {
				break
			}
		}
	}
	cutoff180 := time.Now().Add(-180 * 24 * time.Hour).Unix()
	cutoff30 := time.Now().Add(-30 * 24 * time.Hour).Unix()
	query := `SELECT ` + androidScanEntryColumns + ` FROM scan_entries WHERE scan_id=? AND is_directory=0 AND ((logical_size_bytes>=524288000 AND modified_at_ms>0 AND modified_at_ms<=?) OR ((lower(name) LIKE '%.apk' OR lower(name) LIKE '%.apks' OR lower(name) LIKE '%.xapk') AND modified_at_ms>0 AND modified_at_ms<=?) OR ((lower(name) LIKE '%.zip' OR lower(name) LIKE '%.7z' OR lower(name) LIKE '%.rar' OR lower(name) LIKE '%.tar' OR lower(name) LIKE '%.gz' OR lower(name) LIKE '%.xz' OR lower(name) LIKE '%.zst') AND modified_at_ms>0 AND modified_at_ms<=?) OR ((lower(name) LIKE '%.log' OR lower(path) LIKE '%/logs/%') AND logical_size_bytes>=104857600)) ORDER BY logical_size_bytes DESC LIMIT 250`
	rows, err = s.db.QueryContext(ctx, query, scanID, cutoff180, cutoff30, cutoff180)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		if len(items) >= productRecommendationLimit {
			break
		}
		e, er := scanAndroidEntry(rows)
		if er != nil {
			return nil, er
		}
		m := normalizedEpochMillis(e.ModifiedAt)
		age := int64(0)
		if m > 0 {
			age = (now - m) / (24 * time.Hour.Milliseconds())
		}
		kind, title, score, risk := "", "", 0, "MEDIUM"
		confidence := "MEDIUM"
		switch {
		case e.SizeBytes >= 500*1024*1024 && age >= 180:
			kind = "LARGE_OLD_FILE"
			title = "Large old file"
			score = 55
			risk = "HIGH"
		case isInstaller(e.Name) && age >= 30:
			kind = "STALE_INSTALLER"
			title = "Stale installer"
			score = 65
		case isArchive(e.Name) && age >= 180:
			kind = "OLD_ARCHIVE"
			title = "Old archive"
			score = 50
		case (strings.HasSuffix(strings.ToLower(e.Name), ".log") || strings.Contains(strings.ToLower(e.Path), "/logs/")) && e.SizeBytes >= 100*1024*1024:
			kind = "OVERSIZED_LOG"
			title = "Oversized log"
			score = 60
		}
		if kind != "" {
			items = append(items, map[string]any{"id": "scan:" + stableProductID(scanID, e.Path, kind), "kind": kind, "title": title, "summary": "Go scan evidence identifies a review candidate; no recommendation authorizes deletion.", "paths": []string{e.Path}, "reclaimableBytes": e.SizeBytes, "confidence": confidence, "risk": risk, "score": score, "ageDays": age, "evidence": []map[string]any{{"code": "SCAN_EVIDENCE", "label": "Scan evidence", "detail": "Candidate derived from generation-bound Go scan metadata."}}, "expectedSha256ByPath": map[string]string{}})
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i]["score"].(int) > items[j]["score"].(int) })
	if len(items) > productRecommendationLimit {
		items = items[:productRecommendationLimit]
	}
	return map[string]any{"scanId": scanID, "items": items, "generatedAt": now, "scanGeneration": generation, "bounded": true}, nil
}

func (s *SQLiteStore) boundedGrowthView(ctx context.Context, scanID string, current *AndroidScanSession, previous *AndroidScanSession, history []AndroidScanSession) (map[string]any, error) {
	now := time.Now().UnixMilli()
	timeline := []map[string]any{}
	ordered := append([]AndroidScanSession{}, history...)
	sort.Slice(ordered, func(i, j int) bool { return ordered[i].StartedAt < ordered[j].StartedAt })
	var prior *int64
	for _, v := range ordered {
		total := v.TotalBytes
		var delta any = nil
		if prior != nil {
			delta = total - *prior
		}
		completed := v.StartedAt
		if v.CompletedAt != nil {
			completed = *v.CompletedAt
		}
		timeline = append(timeline, map[string]any{"sessionId": v.ID, "completedAt": completed, "totalBytes": total, "deltaFromPreviousBytes": delta})
		copy := total
		prior = &copy
	}
	var pid any = nil
	if previous != nil {
		pid = previous.ID
	}
	if current == nil || previous == nil || current.RootPath != previous.RootPath || !growthComparable(current.IntegrityStatus) || !growthComparable(previous.IntegrityStatus) {
		return map[string]any{"scanId": scanID, "previousScanId": pid, "currentSession": current, "previousSession": previous, "items": []any{}, "timeline": timeline, "insights": []any{}, "generatedAt": now}, nil
	}
	query := `SELECT path,current_bytes,previous_bytes,kind FROM (SELECT c.path,c.logical_size_bytes current_bytes,COALESCE(p.logical_size_bytes,0) previous_bytes,CASE WHEN p.path IS NULL THEN 'ADDED' WHEN c.logical_size_bytes>p.logical_size_bytes THEN 'GROWN' WHEN c.logical_size_bytes<p.logical_size_bytes THEN 'SHRUNK' ELSE 'UNCHANGED' END kind FROM scan_entries c LEFT JOIN scan_entries p ON p.scan_id=? AND p.path=c.path WHERE c.scan_id=? AND c.is_directory=0 UNION ALL SELECT p.path,0 current_bytes,p.logical_size_bytes previous_bytes,'REMOVED' kind FROM scan_entries p LEFT JOIN scan_entries c ON c.scan_id=? AND c.path=p.path WHERE p.scan_id=? AND p.is_directory=0 AND c.path IS NULL) WHERE kind<>'UNCHANGED' ORDER BY abs(current_bytes-previous_bytes) DESC,path COLLATE NOCASE LIMIT ?`
	rows, err := s.db.QueryContext(ctx, query, previous.ID, scanID, scanID, previous.ID, productGrowthLimit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []map[string]any{}
	for rows.Next() {
		var path, kind string
		var cur, old int64
		if err := rows.Scan(&path, &cur, &old, &kind); err != nil {
			return nil, err
		}
		var prevEntry, currentEntry any = nil, nil
		if old > 0 {
			prevEntry = map[string]any{"scanId": previous.ID, "path": path, "parentPath": filepath.Dir(path), "name": filepath.Base(path), "sizeBytes": old, "allocatedBytes": old, "isDirectory": false, "depth": 0, "modifiedAt": 0, "fileCount": 0}
		}
		if cur > 0 {
			currentEntry = map[string]any{"scanId": scanID, "path": path, "parentPath": filepath.Dir(path), "name": filepath.Base(path), "sizeBytes": cur, "allocatedBytes": cur, "isDirectory": false, "depth": 0, "modifiedAt": 0, "fileCount": 0}
		}
		items = append(items, map[string]any{"path": path, "name": filepath.Base(path), "kind": kind, "group": "FILE", "currentBytes": cur, "previousBytes": old, "deltaBytes": cur - old, "previousEntry": prevEntry, "currentEntry": currentEntry})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return map[string]any{"scanId": scanID, "previousScanId": pid, "currentSession": current, "previousSession": previous, "items": items, "timeline": timeline, "insights": buildGrowthInsights(items), "generatedAt": now, "bounded": true, "itemLimit": productGrowthLimit}, nil
}

// Silence database/sql import guards when build tags swap drivers.
var _ = sql.ErrNoRows
