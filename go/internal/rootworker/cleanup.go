package rootworker

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"
	"unsafe"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
)

const (
	maxCleanupInput        = 8 * 1024 * 1024
	maxReviewedTreeEntries = 100_000
	capabilityLifetime     = 5 * time.Minute
	oPath                  = 0x200000
	atRemovedir            = 0x200
)

type cleanupPlanInput struct {
	ID               string            `json:"id"`
	OperationID      string            `json:"operationId"`
	ScanID           string            `json:"scanId"`
	RootPath         string            `json:"rootPath"`
	ScanGeneration   int64             `json:"scanGeneration"`
	Items            []cleanupPlanItem `json:"items"`
	PackageInventory packageInventory  `json:"packageInventory"`
}

type cleanupPlanItem struct {
	Path           string `json:"path"`
	SizeBytes      int64  `json:"sizeBytes"`
	Directory      bool   `json:"directory"`
	ModifiedAt     int64  `json:"modifiedAt"`
	ExpectedSHA256 string `json:"expectedSha256,omitempty"`
	RetainedPath   string `json:"retainedPath,omitempty"`
	RetainedSHA256 string `json:"retainedSha256,omitempty"`
	PackageName    string `json:"packageName,omitempty"`
}

type packageInventory struct {
	OK       bool                   `json:"ok"`
	Complete bool                   `json:"complete"`
	Users    []packageInventoryUser `json:"users"`
}

type packageInventoryUser struct {
	ID       int      `json:"id"`
	Packages []string `json:"packages"`
}

type cleanupExecutionInput struct {
	OperationID      string             `json:"operationId"`
	ScanID           string             `json:"scanId"`
	RootPath         string             `json:"rootPath"`
	ScanGeneration   int64              `json:"scanGeneration"`
	Accepted         []approvedJSON     `json:"accepted"`
	GoManifest       cleanup.Manifest   `json:"goManifest"`
	GoCapability     cleanup.Capability `json:"goCapability"`
	MutationProtocol string             `json:"mutationProtocol"`
	PackageInventory packageInventory   `json:"packageInventory"`
	JournalPath      string             `json:"journalPath"`
}

type approvedJSON struct {
	Path           string          `json:"path"`
	PlannedBytes   int64           `json:"plannedBytes"`
	Directory      bool            `json:"directory"`
	Fingerprint    fingerprintJSON `json:"fingerprint"`
	RetainedPath   string          `json:"retainedPath,omitempty"`
	RetainedSHA256 string          `json:"retainedSha256,omitempty"`
	PackageName    string          `json:"packageName,omitempty"`
}

type blockedJSON struct {
	Path   string `json:"path"`
	Reason string `json:"reason"`
}

type fingerprintJSON struct {
	Device        uint64 `json:"device"`
	MountID       string `json:"mountId,omitempty"`
	Inode         uint64 `json:"inode"`
	Mode          uint32 `json:"mode"`
	StatSizeBytes int64  `json:"statSizeBytes"`
	ModifiedAt    int64  `json:"modifiedAt"`
	TreeDigest    string `json:"treeDigest"`
	EntryCount    int64  `json:"entryCount"`
	ContentSHA256 string `json:"contentSha256,omitempty"`
	ContentBytes  int64  `json:"-"`
}

func (f fingerprintJSON) identity() string {
	return fmt.Sprintf("dev=%d;ino=%d;mode=%d;size=%d;mtime=%d;tree=%s;entries=%d", f.Device, f.Inode, f.Mode, f.StatSizeBytes, f.ModifiedAt, f.TreeDigest, f.EntryCount)
}

// ReviewCleanup converts Android's selected paths into the immutable Go cleanup
// manifest and a short-lived, single-use capability. No mutation happens here.
func ReviewCleanup(r io.Reader, w io.Writer) error {
	var input cleanupPlanInput
	raw, err := io.ReadAll(io.LimitReader(r, maxCleanupInput+1))
	if err != nil {
		return err
	}
	if len(raw) > maxCleanupInput {
		return errors.New("cleanup review request too large")
	}
	if err := json.Unmarshal(raw, &input); err != nil {
		return fmt.Errorf("decode cleanup review: %w", err)
	}
	if input.OperationID == "" {
		input.OperationID = input.ID
	}
	if input.ScanGeneration <= 0 {
		input.ScanGeneration = 1
	}
	root, err := canonicalAbsolute(input.RootPath)
	if err != nil {
		return writeReviewFailure(w, input.OperationID, err.Error())
	}
	rootIdentity, err := nodeIdentity(root)
	if err != nil {
		return writeReviewFailure(w, input.OperationID, "Unable to inspect reviewed root: "+err.Error())
	}
	if input.OperationID == "" || input.ScanID == "" || len(input.Items) == 0 {
		return writeReviewFailure(w, input.OperationID, "Cleanup requires an operation, scan, root, and selected paths")
	}

	accepted := make([]approvedJSON, 0, len(input.Items))
	blocked := make([]blockedJSON, 0)
	entries := make([]cleanup.ManifestEntry, 0, len(input.Items))
	seen := map[string]struct{}{}
	var plannedBytes int64
	for _, item := range input.Items {
		path, pathErr := canonicalAbsolute(item.Path)
		if pathErr != nil {
			blocked = append(blocked, blockedJSON{Path: item.Path, Reason: pathErr.Error()})
			continue
		}
		if _, duplicate := seen[path]; duplicate {
			continue
		}
		seen[path] = struct{}{}
		if reason := cleanupPathBlockReason(path, root); reason != "" {
			blocked = append(blocked, blockedJSON{Path: path, Reason: reason})
			continue
		}
		fp, descendants, inspectErr := inspectTree(path, root, true)
		if inspectErr != nil {
			blocked = append(blocked, blockedJSON{Path: path, Reason: inspectErr.Error()})
			continue
		}
		if item.ModifiedAt > 0 && fp.ModifiedAt != item.ModifiedAt {
			blocked = append(blocked, blockedJSON{Path: path, Reason: "Path modification time changed after scan"})
			continue
		}
		if item.Directory != isDirectoryMode(fp.Mode) {
			blocked = append(blocked, blockedJSON{Path: path, Reason: "Path type changed after scan"})
			continue
		}
		if item.ExpectedSHA256 != "" {
			h, hashErr := sha256File(path)
			if hashErr != nil || !equalSHA256(item.ExpectedSHA256, h) {
				blocked = append(blocked, blockedJSON{Path: path, Reason: "Expected content hash no longer matches"})
				continue
			}
			fp.ContentSHA256 = h
		}
		retainedConstraint, retainedOK, retainedReason := retainedCopyConstraint(path, root, item.RetainedPath, item.RetainedSHA256)
		if retainedReason != "" {
			blocked = append(blocked, blockedJSON{Path: path, Reason: retainedReason})
			continue
		}
		if reason := packageRemovalBlockReason(item.PackageName, input.PackageInventory); reason != "" {
			blocked = append(blocked, blockedJSON{Path: path, Reason: reason})
			continue
		}
		logical := item.SizeBytes
		if logical < 0 {
			logical = 0
		}
		allocated := fp.ContentBytes
		if allocated < logical {
			allocated = logical
		}
		evidence := cleanup.ActionablePathEvidence{
			Path: path, ScanID: input.ScanID, ScanGeneration: input.ScanGeneration,
			SourceSurface: "android_cleanup_review", SourceKind: "scan_entry",
			ExpectedHash: item.ExpectedSHA256, ExpectedIdentity: fp.identity(),
			RetainedCopyConstraint: retainedConstraint, PackageName: item.PackageName,
			ProtectedState: cleanup.ProtectedStateUnprotected, DeletionEligible: true,
			Reason: "user_reviewed_cleanup", LogicalSizeBytes: logical, AllocatedSizeBytes: allocated,
		}
		entry := cleanup.ManifestEntry{Evidence: evidence, ExactDescendants: descendants, PathRole: map[bool]string{true: "tree", false: "leaf"}[isDirectoryMode(fp.Mode)], Protection: cleanup.ProtectedStateUnprotected, MountBoundary: "same_mount", IdentityEvidence: fp.identity(), RetainedCopySatisfied: retainedOK}
		if err := entry.Validate(); err != nil {
			blocked = append(blocked, blockedJSON{Path: path, Reason: err.Error()})
			continue
		}
		entries = append(entries, entry)
		approved := approvedJSON{Path: path, PlannedBytes: allocated, Directory: isDirectoryMode(fp.Mode), Fingerprint: fp, RetainedPath: item.RetainedPath, RetainedSHA256: item.RetainedSHA256, PackageName: item.PackageName}
		accepted = append(accepted, approved)
		plannedBytes += allocated
	}
	if len(entries) == 0 {
		return json.NewEncoder(w).Encode(map[string]any{"ok": false, "operationId": input.OperationID, "accepted": accepted, "blocked": blocked, "plannedBytes": 0, "message": "No selected path passed Go cleanup review"})
	}
	created := time.Now()
	sourceHash := sha256.Sum256(raw)
	scanHash := sha256.Sum256([]byte(fmt.Sprintf("%s\x00%d\x00%s", input.ScanID, input.ScanGeneration, root)))
	manifest, err := cleanup.BuildManifest(cleanup.ManifestRequest{
		ID: "manifest-" + input.OperationID, OperationID: input.OperationID, ScanID: input.ScanID,
		ScanGeneration: input.ScanGeneration, ScanContextHash: hex.EncodeToString(scanHash[:]), ManifestRevision: 1,
		RootIdentity: rootIdentity, SourceContextHash: hex.EncodeToString(sourceHash[:]), CreatedAt: created, Entries: entries,
	})
	if err != nil {
		return writeReviewFailure(w, input.OperationID, err.Error())
	}
	capability, err := cleanup.NewCapability("cap-"+manifest.Hash[:24], manifest, created, created.Add(capabilityLifetime))
	if err != nil {
		return writeReviewFailure(w, input.OperationID, err.Error())
	}
	flow, err := cleanup.PrepareReview(manifest)
	if err != nil {
		return writeReviewFailure(w, input.OperationID, err.Error())
	}
	return json.NewEncoder(w).Encode(map[string]any{
		"ok": true, "operationId": input.OperationID, "scanId": input.ScanID, "scanGeneration": input.ScanGeneration,
		"rootPath": root, "accepted": accepted, "blocked": blocked, "plannedBytes": plannedBytes,
		"goManifest": manifest, "goCapability": capability, "mutationProtocol": cleanup.MutationProtocol,
		"requiredAcks": flow.RequiredAcks, "reviewedAt": created.UnixMilli(), "expiresAt": capability.ExpiresAtUnixMs,
		"message": fmt.Sprintf("Go reviewed %d path(s); %d blocked", len(accepted), len(blocked)),
	})
}

func writeReviewFailure(w io.Writer, operationID, message string) error {
	return json.NewEncoder(w).Encode(map[string]any{"ok": false, "operationId": operationID, "accepted": []any{}, "blocked": []any{}, "plannedBytes": 0, "message": message})
}

type persistentExecutionClaim struct {
	OperationID  string `json:"operationId"`
	CapabilityID string `json:"capabilityId"`
	ClaimedAtMs  int64  `json:"claimedAtUnixMs"`
}

func executionClaimPath(journalPath string) string { return journalPath + ".claim" }

func claimExecution(input cleanupExecutionInput) error {
	if input.JournalPath == "" || !filepath.IsAbs(input.JournalPath) {
		return errors.New("absolute cleanup journal path required for durable single-use claim")
	}
	if err := os.MkdirAll(filepath.Dir(input.JournalPath), 0700); err != nil {
		return err
	}
	claimPath := executionClaimPath(input.JournalPath)
	f, err := os.OpenFile(claimPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		if os.IsExist(err) {
			return cleanup.ErrCapabilityReplay
		}
		return err
	}
	claim := persistentExecutionClaim{OperationID: input.OperationID, CapabilityID: input.GoCapability.CapabilityID, ClaimedAtMs: time.Now().UnixMilli()}
	encodeErr := json.NewEncoder(f).Encode(claim)
	syncErr := f.Sync()
	closeErr := f.Close()
	if encodeErr != nil {
		return encodeErr
	}
	if syncErr != nil {
		return syncErr
	}
	return closeErr
}

func validateRetainedCopiesAtExecution(input cleanupExecutionInput) string {
	root, err := canonicalAbsolute(input.RootPath)
	if err != nil {
		return "Invalid cleanup root during retained-copy revalidation"
	}
	for _, approved := range input.Accepted {
		if approved.RetainedPath == "" && approved.RetainedSHA256 == "" {
			continue
		}
		if approved.RetainedPath == "" || approved.RetainedSHA256 == "" {
			return "Duplicate cleanup retained-copy evidence is incomplete"
		}
		target, err := canonicalAbsolute(approved.Path)
		if err != nil {
			return "Deletion target became invalid before retained-copy revalidation"
		}
		retained, err := canonicalAbsolute(approved.RetainedPath)
		if err != nil || !sameOrDescendant(retained, root) {
			return "Retained duplicate moved outside the reviewed scan root"
		}
		targetInfo, err := os.Lstat(target)
		if err != nil {
			return "Deletion target unavailable during retained-copy revalidation"
		}
		retainedInfo, err := os.Lstat(retained)
		if err != nil || !retainedInfo.Mode().IsRegular() || retainedInfo.Mode()&os.ModeSymlink != 0 {
			return "Retained duplicate copy is unavailable or unsafe at execution"
		}
		ts, tok := targetInfo.Sys().(*syscall.Stat_t)
		rs, rok := retainedInfo.Sys().(*syscall.Stat_t)
		if tok && rok && ts.Dev == rs.Dev && ts.Ino == rs.Ino {
			return "Retained duplicate resolves to the same filesystem object as the deletion target"
		}
		hash, err := sha256File(retained)
		if err != nil || !equalSHA256(approved.RetainedSHA256, hash) {
			return "Retained duplicate copy changed after review; create a new cleanup plan"
		}
	}
	return ""
}

// ExecuteCleanup consumes a Go manifest/capability and performs only the exact
// reviewed descendants. The journal is atomically replaced after every protocol
// transition, so Android can reconcile a lost binder/worker response.
func ExecuteCleanup(ctx context.Context, r io.Reader, w io.Writer) error {
	var input cleanupExecutionInput
	dec := json.NewDecoder(io.LimitReader(r, maxCleanupInput))
	if err := dec.Decode(&input); err != nil {
		return fmt.Errorf("decode cleanup execution: %w", err)
	}
	if input.MutationProtocol != cleanup.MutationProtocol || input.OperationID == "" || input.GoManifest.OperationID != input.OperationID || input.GoCapability.OperationID != input.OperationID {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, "Go cleanup manifest/capability binding is invalid"))
	}
	if input.ScanID != input.GoManifest.ScanID || input.ScanGeneration != input.GoManifest.ScanGeneration || filepath.Clean(input.RootPath) == "." {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, "Cleanup execution no longer matches the reviewed scan context"))
	}
	if err := input.GoCapability.ValidateAgainst(input.GoManifest, time.Now()); err != nil {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, err.Error()))
	}
	if reason := validatePackageInventoryAtExecution(input.GoManifest, input.PackageInventory); reason != "" {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, reason))
	}
	if reason := validateRetainedCopiesAtExecution(input); reason != "" {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, reason))
	}
	if err := claimExecution(input); err != nil {
		response := executionFailure(input.OperationID, err.Error())
		if errors.Is(err, cleanup.ErrCapabilityReplay) {
			response["message"] = "Cleanup capability was already claimed; reconcile the existing operation instead of replaying it"
			response["reconcileRequired"] = true
		}
		return json.NewEncoder(w).Encode(response)
	}
	initial := executionFailure(input.OperationID, "Cleanup capability durably claimed; mutation has not completed")
	initial["status"] = "UNKNOWN_OUTCOME"
	initial["reconcileRequired"] = true
	initial["mutationState"] = cleanup.MutationClaimed
	if err := writeJournal(input.JournalPath, initial); err != nil {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, "Unable to persist cleanup claim journal: "+err.Error()))
	}
	fs, err := newRootMutationFS(input.GoManifest, input.RootPath)
	if err != nil {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, err.Error()))
	}
	defer fs.Close()
	store := cleanup.NewCapabilityStore()
	if err := store.Put(input.GoCapability); err != nil {
		return json.NewEncoder(w).Encode(executionFailure(input.OperationID, err.Error()))
	}
	executor := cleanup.NewExecutor()
	observer := func(result cleanup.MutationResult) {
		if input.JournalPath != "" {
			_ = writeJournal(input.JournalPath, mutationToExecution(input, result, nil))
		}
	}
	result, runErr := executor.Execute(store, cleanup.MutationRequest{Manifest: input.GoManifest, CapabilityID: input.GoCapability.CapabilityID, Now: time.Now(), Filesystem: fs, Context: ctx, OnEvent: observer})
	response := mutationToExecution(input, result, runErr)
	if input.JournalPath != "" {
		_ = writeJournal(input.JournalPath, response)
	}
	return json.NewEncoder(w).Encode(response)
}

func ReconcileCleanup(r io.Reader, w io.Writer) error {
	var req struct {
		JournalPath string `json:"journalPath"`
		OperationID string `json:"operationId"`
	}
	if err := json.NewDecoder(io.LimitReader(r, 64*1024)).Decode(&req); err != nil {
		return err
	}
	if req.JournalPath == "" || !filepath.IsAbs(req.JournalPath) {
		return json.NewEncoder(w).Encode(executionFailure(req.OperationID, "Cleanup journal unavailable"))
	}
	data, err := os.ReadFile(req.JournalPath)
	if err != nil {
		if _, claimErr := os.Stat(executionClaimPath(req.JournalPath)); claimErr == nil {
			response := executionFailure(req.OperationID, "Cleanup capability was claimed but its journal is unavailable; filesystem reconciliation is required")
			response["status"] = "UNKNOWN_OUTCOME"
			response["reconcileRequired"] = true
			response["mutationState"] = cleanup.MutationRecoveryRequired
			return json.NewEncoder(w).Encode(response)
		}
		return json.NewEncoder(w).Encode(executionFailure(req.OperationID, "Cleanup journal unavailable: "+err.Error()))
	}
	var response map[string]any
	if err := json.Unmarshal(data, &response); err != nil {
		return json.NewEncoder(w).Encode(executionFailure(req.OperationID, "Cleanup journal invalid"))
	}
	return json.NewEncoder(w).Encode(response)
}

func mutationToExecution(input cleanupExecutionInput, result cleanup.MutationResult, runErr error) map[string]any {
	status := "FAILED"
	ok := false
	switch result.State {
	case cleanup.MutationComplete:
		status, ok = "COMPLETED", true
	case cleanup.MutationCleanupDebt, cleanup.MutationRecoveryRequired:
		status = "UNKNOWN_OUTCOME"
	case cleanup.MutationInterrupted:
		if result.ReclaimedBytes > 0 {
			status = "UNKNOWN_OUTCOME"
		} else {
			status = "BLOCKED"
		}
	case cleanup.MutationFailed:
		status = "BLOCKED"
	}
	byPath := map[string]cleanup.MutationEntryResult{}
	for _, entry := range result.Entries {
		byPath[entry.Path] = entry
	}
	items := make([]map[string]any, 0, len(input.Accepted))
	failed := 0
	for _, approved := range input.Accepted {
		entry, exists := byPath[approved.Path]
		itemStatus := "SKIPPED"
		deleted := int64(0)
		message := "Not mutated"
		if exists {
			switch entry.State {
			case cleanup.MutationComplete:
				itemStatus, deleted, message = "DELETED", approved.PlannedBytes, "Verified absent after Go mutation"
			case cleanup.MutationCleanupDebt:
				itemStatus, deleted, message = "UNKNOWN_OUTCOME", approved.PlannedBytes, entry.Reason
			default:
				itemStatus, deleted, message = "UNKNOWN_OUTCOME", approved.PlannedBytes, entry.Reason
			}
		}
		if itemStatus != "DELETED" {
			failed++
		}
		items = append(items, map[string]any{"path": approved.Path, "plannedBytes": approved.PlannedBytes, "deletedBytes": deleted, "status": itemStatus, "message": message})
	}
	message := "Go cleanup mutation completed and verified"
	if runErr != nil {
		message = runErr.Error()
	}
	return map[string]any{"ok": ok, "operationId": input.OperationID, "status": status, "deletedBytes": result.ReclaimedBytes, "failedCount": failed, "items": items, "message": message, "mutationProtocol": cleanup.MutationProtocol, "mutationState": result.State, "certainty": result.Certainty, "reconcileRequired": result.ReconcileRequired, "journal": result.Journal}
}

func executionFailure(operationID, message string) map[string]any {
	return map[string]any{"ok": false, "operationId": operationID, "status": "BLOCKED", "deletedBytes": 0, "failedCount": 0, "items": []any{}, "message": message, "mutationProtocol": cleanup.MutationProtocol, "certainty": "failed_closed"}
}

func writeJournal(path string, response map[string]any) error {
	if !filepath.IsAbs(path) {
		return errors.New("journal path must be absolute")
	}
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		return err
	}
	payload, err := json.Marshal(response)
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, payload, 0600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func canonicalAbsolute(path string) (string, error) {
	if strings.ContainsRune(path, '\x00') || !filepath.IsAbs(path) {
		return "", errors.New("Invalid or relative path")
	}
	clean := filepath.Clean(path)
	if clean == "." {
		return "", errors.New("Invalid path")
	}
	return clean, nil
}

func cleanupPathBlockReason(path, root string) string {
	if path == root {
		return "The scan root cannot be deleted"
	}
	if !sameOrDescendant(path, root) {
		return "Path is outside the reviewed scan root"
	}
	protectedExact := map[string]bool{"/": true, "/data": true}
	if protectedExact[path] {
		return "Protected Android path"
	}
	for _, p := range []string{"/system", "/vendor", "/product", "/metadata", "/persist", "/odm", "/system_ext", "/proc", "/sys", "/dev", "/apex", "/data/adb", "/data/system", "/data/misc", "/data/local/tmp/dustui"} {
		if sameOrDescendant(path, p) {
			return "Protected Android path"
		}
	}
	return ""
}

func sameOrDescendant(path, root string) bool {
	return path == root || strings.HasPrefix(path, strings.TrimSuffix(root, string(filepath.Separator))+string(filepath.Separator))
}

func nodeIdentity(path string) (string, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return "", err
	}
	if info.Mode()&os.ModeSymlink != 0 {
		return "", errors.New("root symbolic links are not accepted")
	}
	st, ok := info.Sys().(*syscall.Stat_t)
	if !ok {
		return "", errors.New("filesystem identity unavailable")
	}
	return fmt.Sprintf("dev=%d;ino=%d;mode=%d", uint64(st.Dev), uint64(st.Ino), uint32(st.Mode)), nil
}

func inspectTree(target, root string, collectDescendants bool) (fingerprintJSON, []string, error) {
	top, err := os.Lstat(target)
	if err != nil {
		return fingerprintJSON{}, nil, err
	}
	if top.Mode()&os.ModeSymlink != 0 {
		return fingerprintJSON{}, nil, errors.New("Selected symbolic links are not deleted")
	}
	rootInfo, err := os.Lstat(root)
	if err != nil {
		return fingerprintJSON{}, nil, err
	}
	rootDev := statDevice(rootInfo)
	digest := sha256.New()
	descendants := make([]string, 0)
	var count int64
	var content int64
	err = filepath.Walk(target, func(path string, info os.FileInfo, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if info == nil {
			return errors.New("missing filesystem metadata")
		}
		if statDevice(info) != rootDev {
			return fmt.Errorf("Nested filesystem or mount boundary: %s", path)
		}
		if path != target && cleanupPathBlockReason(path, root) != "" {
			return fmt.Errorf("Protected descendant: %s", path)
		}
		count++
		if count > maxReviewedTreeEntries {
			return fmt.Errorf("Selection exceeds %d planned entries; choose smaller items", maxReviewedTreeEntries)
		}
		st, _ := info.Sys().(*syscall.Stat_t)
		dev, ino, mode, mtime, mtimeNS, ctime, ctimeNS := uint64(0), uint64(0), uint32(info.Mode()), info.ModTime().Unix(), int64(0), int64(0), int64(0)
		if st != nil {
			dev, ino, mode, mtime, mtimeNS, ctime, ctimeNS = uint64(st.Dev), uint64(st.Ino), uint32(st.Mode), st.Mtim.Sec, st.Mtim.Nsec, st.Ctim.Sec, st.Ctim.Nsec
		}
		rel, _ := filepath.Rel(target, path)
		fmt.Fprintf(digest, "%s\x00%d\x00%d\x00%d\x00%d\x00%d\x00%d\x00%d\x00%d\n", rel, dev, ino, mode, info.Size(), mtime, mtimeNS, ctime, ctimeNS)
		if info.Mode().IsRegular() {
			content += int64(allocatedSize(info))
		}
		if collectDescendants {
			descendants = append(descendants, filepath.Clean(path))
		}
		return nil
	})
	if err != nil {
		return fingerprintJSON{}, nil, err
	}
	st, ok := top.Sys().(*syscall.Stat_t)
	if !ok {
		return fingerprintJSON{}, nil, errors.New("Filesystem identity unavailable")
	}
	sort.Strings(descendants)
	return fingerprintJSON{Device: uint64(st.Dev), Inode: uint64(st.Ino), Mode: uint32(st.Mode), StatSizeBytes: top.Size(), ModifiedAt: st.Mtim.Sec, TreeDigest: hex.EncodeToString(digest.Sum(nil)), EntryCount: count, ContentBytes: content}, descendants, nil
}

func isDirectoryMode(mode uint32) bool { return mode&syscall.S_IFMT == syscall.S_IFDIR }

func sha256File(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
func equalSHA256(expected, actual string) bool {
	return strings.TrimPrefix(strings.ToLower(expected), "sha256:") == strings.ToLower(actual)
}

func retainedCopyConstraint(target, root, retainedPath, retainedHash string) (string, bool, string) {
	if retainedPath == "" && retainedHash == "" {
		return "", true, ""
	}
	if retainedPath == "" || retainedHash == "" {
		return "", false, "Duplicate cleanup requires retained-copy evidence"
	}
	retained, err := canonicalAbsolute(retainedPath)
	if err != nil || !sameOrDescendant(retained, root) {
		return "", false, "Retained duplicate is outside the reviewed scan root"
	}
	targetInfo, err := os.Lstat(target)
	if err != nil {
		return "", false, "Unable to inspect deletion target identity"
	}
	retainedInfo, err := os.Lstat(retained)
	if err != nil || !retainedInfo.Mode().IsRegular() || retainedInfo.Mode()&os.ModeSymlink != 0 {
		return "", false, "Retained duplicate copy is unavailable or unsafe"
	}
	ts, tok := targetInfo.Sys().(*syscall.Stat_t)
	rs, rok := retainedInfo.Sys().(*syscall.Stat_t)
	if tok && rok && ts.Dev == rs.Dev && ts.Ino == rs.Ino {
		return "", false, "Retained duplicate is the same filesystem object as the deletion target"
	}
	h, err := sha256File(retained)
	if err != nil || !equalSHA256(retainedHash, h) {
		return "", false, "Retained duplicate copy changed or no longer matches the expected hash"
	}
	return "path=" + retained + ";sha256=" + strings.TrimPrefix(strings.ToLower(retainedHash), "sha256:"), true, ""
}

func packageRemovalBlockReason(packageName string, inventory packageInventory) string {
	if packageName == "" {
		return ""
	}
	if !inventory.OK || !inventory.Complete || len(inventory.Users) == 0 {
		return "Package inventory unavailable; cannot safely delete app residue"
	}
	for _, user := range inventory.Users {
		for _, p := range user.Packages {
			if p == packageName {
				return "Package " + packageName + " is installed now; app residue must be replanned"
			}
		}
	}
	return ""
}
func validatePackageInventoryAtExecution(manifest cleanup.Manifest, inventory packageInventory) string {
	for _, entry := range manifest.Entries {
		if reason := packageRemovalBlockReason(entry.Evidence.PackageName, inventory); reason != "" {
			return reason
		}
	}
	return ""
}

type rootMutationFS struct {
	manifest cleanup.Manifest
	root     string
	rootFD   int
	rootDev  uint64
	allowed  map[string]struct{}
}

func newRootMutationFS(manifest cleanup.Manifest, rootPath string) (*rootMutationFS, error) {
	if err := manifest.Validate(); err != nil {
		return nil, err
	}
	if len(manifest.Entries) == 0 {
		return nil, errors.New("cleanup manifest has no entries")
	}
	root, err := canonicalAbsolute(rootPath)
	if err != nil {
		return nil, err
	}
	rootIdentity, err := nodeIdentity(root)
	if err != nil {
		return nil, fmt.Errorf("inspect cleanup root: %w", err)
	}
	if rootIdentity != manifest.RootIdentity {
		return nil, errors.New("cleanup root identity changed after review")
	}
	for _, entry := range manifest.Entries {
		if !sameOrDescendant(entry.Evidence.Path, root) || entry.Evidence.Path == root {
			return nil, fmt.Errorf("manifest path outside reviewed root:%s", entry.Evidence.Path)
		}
	}
	fd, err := syscall.Open(root, syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_CLOEXEC|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return nil, fmt.Errorf("open cleanup root: %w", err)
	}
	var st syscall.Stat_t
	if err := syscall.Fstat(fd, &st); err != nil {
		syscall.Close(fd)
		return nil, err
	}
	allowed := map[string]struct{}{}
	for _, entry := range manifest.Entries {
		for _, p := range entry.ExactDescendants {
			allowed[filepath.Clean(p)] = struct{}{}
		}
	}
	return &rootMutationFS{manifest: manifest, root: root, rootFD: fd, rootDev: uint64(st.Dev), allowed: allowed}, nil
}

func (fs *rootMutationFS) Close() error {
	if fs.rootFD >= 0 {
		err := syscall.Close(fs.rootFD)
		fs.rootFD = -1
		return err
	}
	return nil
}

func (fs *rootMutationFS) Identity(path string) (string, bool, error) {
	path = filepath.Clean(path)
	if _, err := os.Lstat(path); os.IsNotExist(err) {
		return "", false, nil
	} else if err != nil {
		return "", false, err
	}
	fp, descendants, err := inspectTree(path, fs.root, true)
	if err != nil {
		return "", true, err
	}
	for _, d := range descendants {
		if _, ok := fs.allowed[d]; !ok {
			return "", true, fmt.Errorf("unreviewed_descendant:%s", d)
		}
	}
	return fp.identity(), true, nil
}

func (fs *rootMutationFS) RemoveExact(path string) error {
	clean := filepath.Clean(path)
	if _, ok := fs.allowed[clean]; !ok {
		return fmt.Errorf("path_not_in_manifest:%s", clean)
	}
	if !sameOrDescendant(clean, fs.root) || clean == fs.root {
		return fmt.Errorf("path_outside_mutation_root:%s", clean)
	}
	rel, err := filepath.Rel(fs.root, clean)
	if err != nil || rel == "." || strings.HasPrefix(rel, "..") {
		return fmt.Errorf("invalid_relative_target:%s", clean)
	}
	parentFD, base, err := fs.openParent(rel)
	if err != nil {
		return err
	}
	defer syscall.Close(parentFD)
	return fs.deleteAt(parentFD, base, filepath.Join(fs.root, rel))
}

func (fs *rootMutationFS) openParent(rel string) (int, string, error) {
	parts := strings.Split(filepath.Clean(rel), string(filepath.Separator))
	if len(parts) == 0 {
		return -1, "", errors.New("empty relative path")
	}
	fd, err := syscall.Dup(fs.rootFD)
	if err != nil {
		return -1, "", err
	}
	for _, part := range parts[:len(parts)-1] {
		if part == "" || part == "." || part == ".." {
			syscall.Close(fd)
			return -1, "", errors.New("unsafe path component")
		}
		next, err := syscall.Openat(fd, part, syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_CLOEXEC|syscall.O_NOFOLLOW, 0)
		if err != nil {
			syscall.Close(fd)
			return -1, "", err
		}
		var st syscall.Stat_t
		if err := syscall.Fstat(next, &st); err != nil || uint64(st.Dev) != fs.rootDev {
			syscall.Close(next)
			syscall.Close(fd)
			if err != nil {
				return -1, "", err
			}
			return -1, "", errors.New("mount boundary changed")
		}
		syscall.Close(fd)
		fd = next
	}
	return fd, parts[len(parts)-1], nil
}

func (fs *rootMutationFS) deleteAt(parentFD int, name, absolute string) error {
	if name == "" || name == "." || name == ".." {
		return errors.New("unsafe delete component")
	}
	if _, ok := fs.allowed[filepath.Clean(absolute)]; !ok {
		return fmt.Errorf("unreviewed_descendant:%s", absolute)
	}
	fd, err := syscall.Openat(parentFD, name, oPath|syscall.O_CLOEXEC|syscall.O_NOFOLLOW, 0)
	if err != nil {
		return err
	}
	var st syscall.Stat_t
	if err := syscall.Fstat(fd, &st); err != nil {
		syscall.Close(fd)
		return err
	}
	if uint64(st.Dev) != fs.rootDev {
		syscall.Close(fd)
		return fmt.Errorf("mount_boundary_changed:%s", absolute)
	}
	isDir := st.Mode&syscall.S_IFMT == syscall.S_IFDIR
	syscall.Close(fd)
	if isDir {
		dirFD, err := syscall.Openat(parentFD, name, syscall.O_RDONLY|syscall.O_DIRECTORY|syscall.O_CLOEXEC|syscall.O_NOFOLLOW, 0)
		if err != nil {
			return err
		}
		file := os.NewFile(uintptr(dirFD), absolute)
		if file == nil {
			syscall.Close(dirFD)
			return errors.New("unable to wrap directory descriptor")
		}
		names, err := file.Readdirnames(-1)
		if err != nil {
			file.Close()
			return err
		}
		sort.Strings(names)
		for _, child := range names {
			childAbs := filepath.Join(absolute, child)
			if _, ok := fs.allowed[childAbs]; !ok {
				file.Close()
				return fmt.Errorf("unreviewed_descendant:%s", childAbs)
			}
		}
		for _, child := range names {
			if err := fs.deleteAt(dirFD, child, filepath.Join(absolute, child)); err != nil {
				file.Close()
				return err
			}
		}
		file.Close()
		return unlinkatRemovedir(parentFD, name)
	}
	return syscall.Unlinkat(parentFD, name)
}

func unlinkatRemovedir(dirfd int, name string) error {
	p, err := syscall.BytePtrFromString(name)
	if err != nil {
		return err
	}
	_, _, errno := syscall.Syscall(syscall.SYS_UNLINKAT, uintptr(dirfd), uintptr(unsafe.Pointer(p)), uintptr(atRemovedir))
	if errno != 0 {
		return errno
	}
	return nil
}
