package cleanup

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	Protocol                     = "dustui.cleanup.capability.v1"
	ProtectedStateUnprotected    = "unprotected"
	ProtectedStateProtected      = "protected"
	ProtectedStateSystemCritical = "system_critical"
	SourceKindBarePath           = "bare_path"
	ClaimIssued                  = "issued"
	ClaimClaimed                 = "claimed"
	ClaimExpired                 = "expired"
	ReviewAckDeletion            = "ack_delete_exact_paths"
	ReviewAckProtectedDesc       = "ack_protected_descendants"
	ReviewAckRetainedCopy        = "ack_retained_copy_constraints"
)

var (
	ErrEvidenceInvalid        = errors.New("actionable_evidence_invalid")
	ErrManifestInvalid        = errors.New("manifest_invalid")
	ErrCapabilityInvalid      = errors.New("capability_invalid")
	ErrCapabilityExpired      = errors.New("capability_expired")
	ErrCapabilityReplay       = errors.New("capability_replay_denied")
	ErrCapabilityHashMismatch = errors.New("capability_manifest_hash_mismatch")
)

type ActionablePathEvidence struct {
	Path                   string `json:"path"`
	ScanID                 string `json:"scanId"`
	ScanGeneration         int64  `json:"scanGeneration"`
	SourceSurface          string `json:"sourceSurface"`
	SourceKind             string `json:"sourceKind"`
	ExpectedHash           string `json:"expectedHash,omitempty"`
	ExpectedIdentity       string `json:"expectedIdentity"`
	RetainedCopyConstraint string `json:"retainedCopyConstraint,omitempty"`
	PackageName            string `json:"packageName,omitempty"`
	UserHandle             string `json:"userHandle,omitempty"`
	Bucket                 string `json:"bucket,omitempty"`
	ProtectedState         string `json:"protectedState"`
	DuplicateGroupID       string `json:"duplicateGroupId,omitempty"`
	RuleID                 string `json:"ruleId,omitempty"`
	RecommendationID       string `json:"recommendationId,omitempty"`
	DeletionEligible       bool   `json:"deletionEligible"`
	Reason                 string `json:"reason"`
	LogicalSizeBytes       int64  `json:"logicalSizeBytes"`
	AllocatedSizeBytes     int64  `json:"allocatedSizeBytes"`
}

func (e ActionablePathEvidence) Validate() error {
	if strings.TrimSpace(e.Path) == "" || strings.Contains(e.Path, "\x00") {
		return fmt.Errorf("%w:path_required", ErrEvidenceInvalid)
	}
	if !filepath.IsAbs(e.Path) {
		return fmt.Errorf("%w:absolute_path_required", ErrEvidenceInvalid)
	}
	if filepath.Clean(e.Path) != e.Path {
		return fmt.Errorf("%w:canonical_path_required", ErrEvidenceInvalid)
	}
	if e.ScanID == "" || e.ScanGeneration <= 0 {
		return fmt.Errorf("%w:scan_identity_required", ErrEvidenceInvalid)
	}
	if e.SourceSurface == "" || e.SourceKind == "" {
		return fmt.Errorf("%w:source_required", ErrEvidenceInvalid)
	}
	if e.ExpectedIdentity == "" {
		return fmt.Errorf("%w:expected_identity_required", ErrEvidenceInvalid)
	}
	if e.ProtectedState == "" {
		return fmt.Errorf("%w:protected_state_required", ErrEvidenceInvalid)
	}
	if e.ProtectedState != ProtectedStateUnprotected && e.DeletionEligible {
		return fmt.Errorf("%w:protected_path_cannot_be_deletion_eligible", ErrEvidenceInvalid)
	}
	if e.SourceKind == SourceKindBarePath && (e.DuplicateGroupID != "" || e.RuleID != "" || e.RecommendationID != "" || e.PackageName != "" || e.Bucket != "") {
		return fmt.Errorf("%w:bare_paths_cannot_claim_specialized_labels", ErrEvidenceInvalid)
	}
	if e.DeletionEligible && e.Reason == "" {
		return fmt.Errorf("%w:deletion_reason_required", ErrEvidenceInvalid)
	}
	if e.LogicalSizeBytes < 0 || e.AllocatedSizeBytes < 0 {
		return fmt.Errorf("%w:size_must_not_be_negative", ErrEvidenceInvalid)
	}
	return nil
}

type ManifestEntry struct {
	Evidence              ActionablePathEvidence `json:"evidence"`
	ExactDescendants      []string               `json:"exactDescendants"`
	PathRole              string                 `json:"pathRole"`
	Protection            string                 `json:"protection"`
	MountBoundary         string                 `json:"mountBoundary"`
	IdentityEvidence      string                 `json:"identityEvidence"`
	RetainedCopySatisfied bool                   `json:"retainedCopySatisfied"`
	Warnings              []string               `json:"warnings,omitempty"`
}

func (e ManifestEntry) Validate() error {
	if err := e.Evidence.Validate(); err != nil {
		return err
	}
	if e.PathRole == "" || e.Protection == "" || e.MountBoundary == "" || e.IdentityEvidence == "" {
		return fmt.Errorf("%w:entry_context_required", ErrManifestInvalid)
	}
	for _, d := range e.ExactDescendants {
		if d == "" || strings.Contains(d, "\x00") || !filepath.IsAbs(d) || filepath.Clean(d) != d {
			return fmt.Errorf("%w:exact_descendant_must_be_canonical_absolute", ErrManifestInvalid)
		}
		if d != e.Evidence.Path && !strings.HasPrefix(d, e.Evidence.Path+string(filepath.Separator)) {
			return fmt.Errorf("%w:descendant_outside_reviewed_path", ErrManifestInvalid)
		}
	}
	if e.Evidence.RetainedCopyConstraint != "" && !e.RetainedCopySatisfied {
		return fmt.Errorf("%w:retained_copy_constraint_unsatisfied", ErrManifestInvalid)
	}
	return nil
}

type Manifest struct {
	ID                string          `json:"id"`
	OperationID       string          `json:"operationId"`
	ScanID            string          `json:"scanId"`
	ScanGeneration    int64           `json:"scanGeneration"`
	ScanContextHash   string          `json:"scanContextHash"`
	ManifestRevision  int64           `json:"manifestRevision"`
	RootIdentity      string          `json:"rootIdentity"`
	SourceContextHash string          `json:"sourceContextHash"`
	CreatedAtUnixMs   int64           `json:"createdAtUnixMs"`
	Entries           []ManifestEntry `json:"entries"`
	Hash              string          `json:"hash"`
}

type ManifestRequest struct {
	ID                string
	OperationID       string
	ScanID            string
	ScanGeneration    int64
	ScanContextHash   string
	ManifestRevision  int64
	RootIdentity      string
	SourceContextHash string
	CreatedAt         time.Time
	Entries           []ManifestEntry
}

func BuildManifest(req ManifestRequest) (Manifest, error) {
	if req.ID == "" || req.OperationID == "" || req.ScanID == "" || req.ScanGeneration <= 0 || req.ScanContextHash == "" || req.ManifestRevision <= 0 || req.RootIdentity == "" || req.SourceContextHash == "" {
		return Manifest{}, fmt.Errorf("%w:manifest_identity_required", ErrManifestInvalid)
	}
	if len(req.Entries) == 0 {
		return Manifest{}, fmt.Errorf("%w:entries_required", ErrManifestInvalid)
	}
	entries := append([]ManifestEntry{}, req.Entries...)
	for i := range entries {
		entries[i].ExactDescendants = append([]string{}, entries[i].ExactDescendants...)
		sort.Strings(entries[i].ExactDescendants)
		entries[i].Warnings = append([]string{}, entries[i].Warnings...)
		sort.Strings(entries[i].Warnings)
		if err := entries[i].Validate(); err != nil {
			return Manifest{}, err
		}
		if entries[i].Evidence.ScanID != req.ScanID || entries[i].Evidence.ScanGeneration != req.ScanGeneration {
			return Manifest{}, fmt.Errorf("%w:evidence_scan_mismatch", ErrManifestInvalid)
		}
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i].Evidence.Path < entries[j].Evidence.Path })
	created := req.CreatedAt
	if created.IsZero() {
		created = time.UnixMilli(0)
	}
	m := Manifest{ID: req.ID, OperationID: req.OperationID, ScanID: req.ScanID, ScanGeneration: req.ScanGeneration, ScanContextHash: req.ScanContextHash, ManifestRevision: req.ManifestRevision, RootIdentity: req.RootIdentity, SourceContextHash: req.SourceContextHash, CreatedAtUnixMs: created.UnixMilli(), Entries: entries}
	m.Hash = hashStable(m)
	return m, nil
}

func (m Manifest) Validate() error {
	if m.ID == "" || m.OperationID == "" || m.ScanID == "" || m.ScanGeneration <= 0 || m.ScanContextHash == "" || m.ManifestRevision <= 0 || m.RootIdentity == "" || m.SourceContextHash == "" || m.Hash == "" {
		return fmt.Errorf("%w:manifest_identity_required", ErrManifestInvalid)
	}
	for _, entry := range m.Entries {
		if err := entry.Validate(); err != nil {
			return err
		}
	}
	if hashStable(m) != m.Hash {
		return fmt.Errorf("%w:manifest_hash_mismatch", ErrManifestInvalid)
	}
	return nil
}

func hashStable(m Manifest) string {
	m.Hash = ""
	encoded, _ := json.Marshal(m)
	sum := sha256.Sum256(encoded)
	return hex.EncodeToString(sum[:])
}

type Capability struct {
	CapabilityID     string `json:"capabilityId"`
	OperationID      string `json:"operationId"`
	ScanID           string `json:"scanId"`
	ScanGeneration   int64  `json:"scanGeneration"`
	ScanContextHash  string `json:"scanContextHash"`
	ManifestHash     string `json:"manifestHash"`
	ManifestRevision int64  `json:"manifestRevision"`
	RootIdentity     string `json:"rootIdentity"`
	ReviewedAtUnixMs int64  `json:"reviewedAtUnixMs"`
	ExpiresAtUnixMs  int64  `json:"expiresAtUnixMs"`
	ClaimState       string `json:"claimState"`
}

func NewCapability(id string, manifest Manifest, reviewedAt, expiresAt time.Time) (Capability, error) {
	if id == "" {
		return Capability{}, fmt.Errorf("%w:capability_id_required", ErrCapabilityInvalid)
	}
	if err := manifest.Validate(); err != nil {
		return Capability{}, err
	}
	if reviewedAt.IsZero() {
		reviewedAt = time.UnixMilli(0)
	}
	if expiresAt.IsZero() || !expiresAt.After(reviewedAt) {
		return Capability{}, fmt.Errorf("%w:expiry_required", ErrCapabilityInvalid)
	}
	return Capability{CapabilityID: id, OperationID: manifest.OperationID, ScanID: manifest.ScanID, ScanGeneration: manifest.ScanGeneration, ScanContextHash: manifest.ScanContextHash, ManifestHash: manifest.Hash, ManifestRevision: manifest.ManifestRevision, RootIdentity: manifest.RootIdentity, ReviewedAtUnixMs: reviewedAt.UnixMilli(), ExpiresAtUnixMs: expiresAt.UnixMilli(), ClaimState: ClaimIssued}, nil
}

func (c Capability) ValidateAgainst(manifest Manifest, now time.Time) error {
	if c.CapabilityID == "" || c.OperationID == "" || c.ManifestHash == "" || c.ClaimState == "" {
		return fmt.Errorf("%w:capability_identity_required", ErrCapabilityInvalid)
	}
	if c.ClaimState != ClaimIssued {
		return ErrCapabilityReplay
	}
	if !now.Before(time.UnixMilli(c.ExpiresAtUnixMs)) {
		return ErrCapabilityExpired
	}
	if err := manifest.Validate(); err != nil {
		return err
	}
	if c.OperationID != manifest.OperationID || c.ScanID != manifest.ScanID || c.ScanGeneration != manifest.ScanGeneration || c.ScanContextHash != manifest.ScanContextHash || c.ManifestHash != manifest.Hash || c.ManifestRevision != manifest.ManifestRevision || c.RootIdentity != manifest.RootIdentity {
		return ErrCapabilityHashMismatch
	}
	return nil
}

type ClaimRecord struct {
	CapabilityID      string `json:"capabilityId"`
	OperationID       string `json:"operationId"`
	ClaimedAtUnixMs   int64  `json:"claimedAtUnixMs"`
	ReconcileRequired bool   `json:"reconcileRequired"`
	StatusLookupKey   string `json:"statusLookupKey"`
}

type CapabilityStore struct {
	mu           sync.Mutex
	capabilities map[string]Capability
	claims       map[string]ClaimRecord
}

func NewCapabilityStore() *CapabilityStore {
	return &CapabilityStore{capabilities: map[string]Capability{}, claims: map[string]ClaimRecord{}}
}

func (s *CapabilityStore) Put(c Capability) error {
	if c.CapabilityID == "" {
		return fmt.Errorf("%w:capability_id_required", ErrCapabilityInvalid)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, exists := s.capabilities[c.CapabilityID]; exists {
		return fmt.Errorf("%w:capability_duplicate", ErrCapabilityInvalid)
	}
	s.capabilities[c.CapabilityID] = c
	return nil
}

func (s *CapabilityStore) Claim(id string, manifest Manifest, now time.Time) (ClaimRecord, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.capabilities[id]
	if !ok {
		return ClaimRecord{}, fmt.Errorf("%w:not_found", ErrCapabilityInvalid)
	}
	if err := c.ValidateAgainst(manifest, now); err != nil {
		if errors.Is(err, ErrCapabilityExpired) {
			c.ClaimState = ClaimExpired
			s.capabilities[id] = c
		}
		return ClaimRecord{}, err
	}
	c.ClaimState = ClaimClaimed
	s.capabilities[id] = c
	record := ClaimRecord{CapabilityID: id, OperationID: c.OperationID, ClaimedAtUnixMs: now.UnixMilli(), ReconcileRequired: true, StatusLookupKey: c.OperationID + ":" + id}
	s.claims[id] = record
	return record, nil
}

func (s *CapabilityStore) LostResponseDisposition(id string) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if rec, ok := s.claims[id]; ok && rec.ReconcileRequired {
		return "status_reconcile_required"
	}
	return "no_claim_record"
}

type ReviewFlow struct {
	ExactPaths          []string `json:"exactPaths"`
	ExactLogicalBytes   int64    `json:"exactLogicalBytes"`
	ExactAllocatedBytes int64    `json:"exactAllocatedBytes"`
	Reasons             []string `json:"reasons"`
	Warnings            []string `json:"warnings"`
	RequiredAcks        []string `json:"requiredAcks"`
	CanCreateCapability bool     `json:"canCreateCapability"`
}

func PrepareReview(manifest Manifest) (ReviewFlow, error) {
	if err := manifest.Validate(); err != nil {
		return ReviewFlow{}, err
	}
	paths := make([]string, 0, len(manifest.Entries))
	warnings := []string{}
	reasons := []string{}
	acks := map[string]bool{ReviewAckDeletion: true}
	var logical, allocated int64
	for _, entry := range manifest.Entries {
		paths = append(paths, entry.Evidence.Path)
		logical += entry.Evidence.LogicalSizeBytes
		allocated += entry.Evidence.AllocatedSizeBytes
		if entry.Evidence.Reason != "" {
			reasons = append(reasons, entry.Evidence.Reason)
		}
		warnings = append(warnings, entry.Warnings...)
		if entry.Evidence.ProtectedState != ProtectedStateUnprotected || entry.Protection != ProtectedStateUnprotected {
			acks[ReviewAckProtectedDesc] = true
		}
		if entry.Evidence.RetainedCopyConstraint != "" {
			acks[ReviewAckRetainedCopy] = true
		}
	}
	sort.Strings(paths)
	sort.Strings(warnings)
	sort.Strings(reasons)
	ackList := make([]string, 0, len(acks))
	for ack := range acks {
		ackList = append(ackList, ack)
	}
	sort.Strings(ackList)
	return ReviewFlow{ExactPaths: paths, ExactLogicalBytes: logical, ExactAllocatedBytes: allocated, Reasons: reasons, Warnings: warnings, RequiredAcks: ackList, CanCreateCapability: true}, nil
}

const MutationProtocol = "dustui.cleanup.mutation.v1"

type MutationState string

const (
	MutationReviewed         MutationState = "reviewed"
	MutationClaimed          MutationState = "claimed"
	MutationAdmitted         MutationState = "admitted"
	MutationExecuting        MutationState = "executing"
	MutationVerifying        MutationState = "verifying"
	MutationComplete         MutationState = "complete"
	MutationFailed           MutationState = "failed"
	MutationInterrupted      MutationState = "interrupted"
	MutationRecoveryRequired MutationState = "recovery_required"
	MutationCleanupDebt      MutationState = "cleanup_debt"
)

var (
	ErrMutationDenied       = errors.New("mutation_denied")
	ErrMutationInterrupted  = errors.New("mutation_interrupted")
	ErrMutationSingleFlight = errors.New("mutation_single_flight")
	ErrVerificationMismatch = errors.New("mutation_verification_mismatch")
)

type MutationJournalEvent struct {
	State      MutationState `json:"state"`
	Reason     string        `json:"reason,omitempty"`
	Path       string        `json:"path,omitempty"`
	UnixMillis int64         `json:"unixMillis"`
}

type MutationEntryResult struct {
	Path           string        `json:"path"`
	State          MutationState `json:"state"`
	LogicalBytes   int64         `json:"logicalBytes"`
	AllocatedBytes int64         `json:"allocatedBytes"`
	Reason         string        `json:"reason,omitempty"`
}

type MutationResult struct {
	Protocol          string                 `json:"protocol"`
	OperationID       string                 `json:"operationId"`
	CapabilityID      string                 `json:"capabilityId"`
	State             MutationState          `json:"state"`
	ReclaimedBytes    int64                  `json:"reclaimedBytes"`
	Certainty         string                 `json:"certainty"`
	StaleAfterCleanup bool                   `json:"staleAfterCleanup"`
	ReconcileRequired bool                   `json:"reconcileRequired"`
	Entries           []MutationEntryResult  `json:"entries"`
	Journal           []MutationJournalEvent `json:"journal"`
}

type IdentityProvider interface {
	Identity(path string) (identity string, exists bool, err error)
}

type ExactMutator interface {
	RemoveExact(path string) error
}

type MutationFS interface {
	IdentityProvider
	ExactMutator
}

type MutationRequest struct {
	Manifest     Manifest
	CapabilityID string
	Now          time.Time
	Filesystem   MutationFS
	Context      context.Context
	OnEvent      func(MutationResult)
}

type Executor struct {
	mu      sync.Mutex
	running bool
	results map[string]MutationResult
}

func NewExecutor() *Executor {
	return &Executor{results: map[string]MutationResult{}}
}

func (e *Executor) Execute(store *CapabilityStore, req MutationRequest) (MutationResult, error) {
	if req.Now.IsZero() {
		req.Now = time.Now()
	}
	e.mu.Lock()
	if e.running {
		e.mu.Unlock()
		return MutationResult{Protocol: MutationProtocol, State: MutationRecoveryRequired, ReconcileRequired: true, Certainty: "ambiguous"}, ErrMutationSingleFlight
	}
	e.running = true
	e.mu.Unlock()
	defer func() {
		e.mu.Lock()
		e.running = false
		e.mu.Unlock()
	}()

	result, err := executeMutation(store, req)
	e.mu.Lock()
	if result.OperationID != "" {
		e.results[result.OperationID] = result
	}
	e.mu.Unlock()
	return result, err
}

func (e *Executor) Reconcile(operationID string) (MutationResult, bool) {
	e.mu.Lock()
	defer e.mu.Unlock()
	result, ok := e.results[operationID]
	return result, ok
}

func executeMutation(store *CapabilityStore, req MutationRequest) (MutationResult, error) {
	result := MutationResult{Protocol: MutationProtocol, OperationID: req.Manifest.OperationID, CapabilityID: req.CapabilityID, State: MutationReviewed, Certainty: "none", StaleAfterCleanup: true}
	ctx := req.Context
	if ctx == nil {
		ctx = context.Background()
	}
	appendEvent := func(state MutationState, reason, path string) {
		result.Journal = append(result.Journal, MutationJournalEvent{State: state, Reason: reason, Path: path, UnixMillis: time.Now().UnixMilli()})
		result.State = state
		if req.OnEvent != nil {
			req.OnEvent(result)
		}
	}
	interrupted := func(reason string) (MutationResult, error) {
		appendEvent(MutationInterrupted, reason, "")
		result.ReconcileRequired = true
		if result.ReclaimedBytes > 0 {
			result.Certainty = "ambiguous"
		} else {
			result.Certainty = "failed_closed"
		}
		return result, ErrMutationInterrupted
	}
	if err := ctx.Err(); err != nil {
		return interrupted("context_cancelled_before_claim")
	}
	appendEvent(MutationReviewed, "immutable_manifest_loaded", "")
	if store == nil || req.Filesystem == nil || req.CapabilityID == "" {
		appendEvent(MutationFailed, "store_filesystem_capability_required", "")
		return result, fmt.Errorf("%w:store_filesystem_capability_required", ErrMutationDenied)
	}
	if err := req.Manifest.Validate(); err != nil {
		appendEvent(MutationFailed, "manifest_invalid", "")
		return result, err
	}
	claim, err := store.Claim(req.CapabilityID, req.Manifest, req.Now)
	if err != nil {
		appendEvent(MutationFailed, "capability_claim_failed", "")
		return result, err
	}
	appendEvent(MutationClaimed, "capability_single_use_claimed", "")
	result.ReconcileRequired = claim.ReconcileRequired
	appendEvent(MutationAdmitted, "go_mutation_authority_admitted", "")
	if err := ctx.Err(); err != nil {
		return interrupted("context_cancelled_before_preflight")
	}
	if err := validateMutationPreconditions(req.Manifest, req.Filesystem); err != nil {
		appendEvent(MutationRecoveryRequired, err.Error(), "")
		result.Certainty = "failed_closed"
		return result, err
	}
	appendEvent(MutationExecuting, "exact_manifest_execution_started", "")
	for _, entry := range req.Manifest.Entries {
		if err := ctx.Err(); err != nil {
			return interrupted("context_cancelled_during_mutation")
		}
		if err := req.Filesystem.RemoveExact(entry.Evidence.Path); err != nil {
			appendEvent(MutationInterrupted, "remove_failed:"+err.Error(), entry.Evidence.Path)
			result.ReconcileRequired = true
			result.Certainty = "ambiguous"
			return result, ErrMutationInterrupted
		}
		result.ReclaimedBytes += entry.Evidence.AllocatedSizeBytes
		result.Entries = append(result.Entries, MutationEntryResult{Path: entry.Evidence.Path, State: MutationExecuting, LogicalBytes: entry.Evidence.LogicalSizeBytes, AllocatedBytes: entry.Evidence.AllocatedSizeBytes})
	}
	appendEvent(MutationVerifying, "post_mutation_verification_started", "")
	for i, entry := range req.Manifest.Entries {
		if err := ctx.Err(); err != nil {
			return interrupted("context_cancelled_during_verification")
		}
		_, exists, err := req.Filesystem.Identity(entry.Evidence.Path)
		if err != nil || exists {
			appendEvent(MutationCleanupDebt, "verification_mismatch", entry.Evidence.Path)
			result.Certainty = "verification_failed"
			if i < len(result.Entries) {
				result.Entries[i].State = MutationCleanupDebt
				result.Entries[i].Reason = "verification_mismatch"
			}
			return result, ErrVerificationMismatch
		}
		if i < len(result.Entries) {
			result.Entries[i].State = MutationComplete
		}
	}
	appendEvent(MutationComplete, "all_manifest_entries_verified_absent", "")
	result.Certainty = "verified"
	return result, nil
}

func validateMutationPreconditions(manifest Manifest, fs IdentityProvider) error {
	for _, entry := range manifest.Entries {
		if !entry.Evidence.DeletionEligible {
			return fmt.Errorf("%w:not_deletion_eligible:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		if entry.Evidence.ProtectedState != ProtectedStateUnprotected || entry.Protection != ProtectedStateUnprotected {
			return fmt.Errorf("%w:protected_descendant:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		if entry.MountBoundary != "same_mount" {
			return fmt.Errorf("%w:mount_boundary:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		if entry.Evidence.RetainedCopyConstraint != "" && !entry.RetainedCopySatisfied {
			return fmt.Errorf("%w:retained_copy:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		identity, exists, err := fs.Identity(entry.Evidence.Path)
		if err != nil {
			return fmt.Errorf("%w:identity_error:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		if !exists {
			return fmt.Errorf("%w:path_missing_before_mutation:%s", ErrMutationDenied, entry.Evidence.Path)
		}
		if identity != entry.Evidence.ExpectedIdentity {
			return fmt.Errorf("%w:identity_changed:%s", ErrMutationDenied, entry.Evidence.Path)
		}
	}
	return nil
}

type InMemoryMutationFS struct {
	mu                sync.Mutex
	identities        map[string]string
	removeErrors      map[string]error
	verificationGhost map[string]string
}

func NewInMemoryMutationFS(identities map[string]string) *InMemoryMutationFS {
	copyIDs := map[string]string{}
	for k, v := range identities {
		copyIDs[k] = v
	}
	return &InMemoryMutationFS{identities: copyIDs, removeErrors: map[string]error{}, verificationGhost: map[string]string{}}
}

func (fs *InMemoryMutationFS) SetRemoveError(path string, err error) {
	fs.mu.Lock()
	defer fs.mu.Unlock()
	fs.removeErrors[path] = err
}

func (fs *InMemoryMutationFS) SetVerificationGhost(path, identity string) {
	fs.mu.Lock()
	defer fs.mu.Unlock()
	fs.verificationGhost[path] = identity
}

func (fs *InMemoryMutationFS) Identity(path string) (string, bool, error) {
	fs.mu.Lock()
	defer fs.mu.Unlock()
	if ghost, ok := fs.verificationGhost[path]; ok {
		return ghost, true, nil
	}
	identity, ok := fs.identities[path]
	return identity, ok, nil
}

func (fs *InMemoryMutationFS) RemoveExact(path string) error {
	fs.mu.Lock()
	defer fs.mu.Unlock()
	if err := fs.removeErrors[path]; err != nil {
		return err
	}
	if _, ok := fs.identities[path]; !ok {
		return fmt.Errorf("path_not_found:%s", path)
	}
	delete(fs.identities, path)
	return nil
}

func Contract() map[string]any {
	return map[string]any{
		"protocol":                 Protocol,
		"mutationProtocol":         MutationProtocol,
		"mutationAuthority":        true,
		"productionExecutor":       true,
		"cutoverOverlay":           "REM-04",
		"legacyExecutorAuthority":  "none",
		"activityRunView":          "canonical",
		"actionableEvidenceFields": []string{"path", "scanId", "scanGeneration", "sourceSurface", "sourceKind", "expectedHash", "expectedIdentity", "retainedCopyConstraint", "packageName", "userHandle", "bucket", "protectedState", "duplicateGroupId", "ruleId", "recommendationId", "deletionEligible"},
		"manifestFields":           []string{"exactDescendants", "pathRole", "protection", "mountBoundary", "identityEvidence", "manifestHash", "manifestRevision", "sourceContextHash", "rootIdentity"},
		"capabilityFields":         []string{"capabilityId", "operationId", "scanId", "scanGeneration", "scanContextHash", "manifestHash", "manifestRevision", "rootIdentity", "reviewedAt", "expiresAt", "claimState"},
		"mutationStates":           []MutationState{MutationReviewed, MutationClaimed, MutationAdmitted, MutationExecuting, MutationVerifying, MutationComplete, MutationFailed, MutationInterrupted, MutationRecoveryRequired, MutationCleanupDebt},
		"safetyRules":              []string{"identity_change_invalidates_authorization", "added_descendant_after_review_not_approved", "capability_single_use", "expired_capability_fails", "lost_response_requires_status_reconcile", "bare_paths_cannot_claim_specialized_labels", "pre_mutation_identity_revalidation", "mount_boundary_enforced", "protected_descendant_rejected", "retained_copy_required", "no_unreviewed_recursive_expansion", "post_mutation_verification", "stale_after_cleanup", "global_destructive_single_flight", "legacy_destructive_executor_unreachable"},
	}
}
