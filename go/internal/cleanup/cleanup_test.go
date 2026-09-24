package cleanup

import (
	"errors"
	"testing"
	"time"
)

func sampleEvidence() ActionablePathEvidence {
	return ActionablePathEvidence{Path: "/data/user/0/example/cache/item.bin", ScanID: "scan-1", ScanGeneration: 7, SourceSurface: "files", SourceKind: "scan_entry", ExpectedHash: "sha256:abc", ExpectedIdentity: "dev=1;ino=2;mtime=3;size=4", RetainedCopyConstraint: "keep_one_duplicate_copy", PackageName: "example", UserHandle: "0", Bucket: "cache", ProtectedState: ProtectedStateUnprotected, DuplicateGroupID: "dup-1", RuleID: "rule-cache", RecommendationID: "rec-1", DeletionEligible: true, Reason: "cache_duplicate", LogicalSizeBytes: 4096, AllocatedSizeBytes: 8192}
}

func sampleManifest(t *testing.T) Manifest {
	t.Helper()
	m, err := BuildManifest(ManifestRequest{ID: "manifest-1", OperationID: "op-1", ScanID: "scan-1", ScanGeneration: 7, ScanContextHash: "ctx-1", ManifestRevision: 1, RootIdentity: "root:boot:pid", SourceContextHash: "src-1", CreatedAt: time.Unix(10, 0), Entries: []ManifestEntry{{Evidence: sampleEvidence(), ExactDescendants: []string{"/data/user/0/example/cache/item.bin"}, PathRole: "leaf", Protection: ProtectedStateUnprotected, MountBoundary: "same_mount", IdentityEvidence: "lstat+hash", RetainedCopySatisfied: true}}})
	if err != nil {
		t.Fatal(err)
	}
	return m
}

func TestManifestBindsReviewedIdentityAndExactDescendants(t *testing.T) {
	m := sampleManifest(t)
	if err := m.Validate(); err != nil {
		t.Fatalf("manifest should validate: %v", err)
	}
	mutated := m
	mutated.Entries[0].Evidence.ExpectedIdentity = "dev=changed"
	if err := mutated.Validate(); err == nil {
		t.Fatal("identity mutation must invalidate manifest hash")
	}
	mutated = m
	mutated.Entries[0].ExactDescendants = append(mutated.Entries[0].ExactDescendants, "/data/user/0/example/cache/new-child")
	if err := mutated.Validate(); err == nil {
		t.Fatal("adding descendant after review must invalidate manifest hash")
	}
}

func TestCapabilityIsSingleUseAndRequiresReconcileAfterClaim(t *testing.T) {
	m := sampleManifest(t)
	now := time.Unix(20, 0)
	cap, err := NewCapability("cap-1", m, now, now.Add(time.Hour))
	if err != nil {
		t.Fatal(err)
	}
	store := NewCapabilityStore()
	if err := store.Put(cap); err != nil {
		t.Fatal(err)
	}
	record, err := store.Claim("cap-1", m, now.Add(time.Second))
	if err != nil {
		t.Fatalf("first claim should pass: %v", err)
	}
	if !record.ReconcileRequired || record.StatusLookupKey == "" {
		t.Fatalf("claim must require status/reconcile after lost response: %+v", record)
	}
	if got := store.LostResponseDisposition("cap-1"); got != "status_reconcile_required" {
		t.Fatalf("lost response disposition = %s", got)
	}
	_, err = store.Claim("cap-1", m, now.Add(2*time.Second))
	if !errors.Is(err, ErrCapabilityReplay) {
		t.Fatalf("replay should fail with ErrCapabilityReplay, got %v", err)
	}
}

func TestExpiredCapabilityAndChangedManifestFail(t *testing.T) {
	m := sampleManifest(t)
	now := time.Unix(20, 0)
	cap, err := NewCapability("cap-exp", m, now, now.Add(time.Minute))
	if err != nil {
		t.Fatal(err)
	}
	if err := cap.ValidateAgainst(m, now.Add(2*time.Minute)); !errors.Is(err, ErrCapabilityExpired) {
		t.Fatalf("expired should fail, got %v", err)
	}
	changed := m
	changed.RootIdentity = "root:other"
	changed.Hash = "different"
	if err := cap.ValidateAgainst(changed, now.Add(time.Second)); !errors.Is(err, ErrCapabilityHashMismatch) && err == nil {
		t.Fatalf("changed manifest must fail, got %v", err)
	}
}

func TestBarePathsCannotClaimSpecializedSafetyLabels(t *testing.T) {
	e := sampleEvidence()
	e.SourceKind = SourceKindBarePath
	e.DuplicateGroupID = "dup-claimed-by-bare-path"
	if err := e.Validate(); err == nil {
		t.Fatal("bare path evidence must not claim duplicate/rule/package labels")
	}
}

func TestReviewFlowShowsExactBytesReasonsWarningsAndAcknowledgements(t *testing.T) {
	m := sampleManifest(t)
	m.Entries[0].Warnings = []string{"protected descendant nearby"}
	m.Hash = ""
	m.Hash = hashStable(m)
	flow, err := PrepareReview(m)
	if err != nil {
		t.Fatal(err)
	}
	if len(flow.ExactPaths) != 1 || flow.ExactPaths[0] != "/data/user/0/example/cache/item.bin" {
		t.Fatalf("unexpected paths: %+v", flow.ExactPaths)
	}
	if flow.ExactLogicalBytes != 4096 || flow.ExactAllocatedBytes != 8192 {
		t.Fatalf("unexpected bytes: %+v", flow)
	}
	if len(flow.RequiredAcks) == 0 || !flow.CanCreateCapability {
		t.Fatalf("review should require acknowledgements and create cap: %+v", flow)
	}
}
