package intelligence

import (
	"errors"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

func TestDuplicateGroupsBindContentIdentityKeepCopyAndActionableEvidence(t *testing.T) {
	inputs := []DuplicateInput{
		{Path: "/data/media/0/DCIM/a.jpg", ScanID: "scan-10", ScanGeneration: 10, SourceContextHash: "ctx", ExpectedIdentity: "dev=1;ino=1", ContentHash: "sha256:same", LogicalSizeBytes: 100, AllocatedSizeBytes: 128, ProtectedState: cleanup.ProtectedStateUnprotected, RetainedCopyEligible: true},
		{Path: "/data/media/0/DCIM/b.jpg", ScanID: "scan-10", ScanGeneration: 10, SourceContextHash: "ctx", ExpectedIdentity: "dev=1;ino=2", ContentHash: "sha256:same", LogicalSizeBytes: 100, AllocatedSizeBytes: 128, ProtectedState: cleanup.ProtectedStateUnprotected, RetainedCopyEligible: true},
	}
	groups, err := BuildDuplicateGroups(inputs)
	if err != nil {
		t.Fatal(err)
	}
	if len(groups) != 1 {
		t.Fatalf("expected one group, got %d", len(groups))
	}
	g := groups[0]
	if g.VerificationState != ConfidenceVerified || g.CanonicalKeepPath == "" || g.RetainedCopyProof == "" || g.GroupHash == "" {
		t.Fatalf("group missing proof: %+v", g)
	}
	var deleteCount, keepCount int
	for _, c := range g.Candidates {
		if c.Keep {
			keepCount++
		} else {
			deleteCount++
		}
		if c.Evidence.DuplicateGroupID != g.GroupID || c.Evidence.SourceKind != "duplicate_verified_content" {
			t.Fatalf("candidate not bound to duplicate evidence: %+v", c.Evidence)
		}
	}
	if keepCount != 1 || deleteCount != 1 {
		t.Fatalf("expected one keep and one delete candidate, got keep=%d delete=%d", keepCount, deleteCount)
	}
}

func TestDuplicateGroupRejectsGenerationContextMismatch(t *testing.T) {
	_, err := BuildDuplicateGroups([]DuplicateInput{
		{Path: "/a", ScanID: "scan-1", ScanGeneration: 1, SourceContextHash: "ctx", ExpectedIdentity: "i1", ContentHash: "sha256:x", LogicalSizeBytes: 1, ProtectedState: cleanup.ProtectedStateUnprotected},
		{Path: "/b", ScanID: "scan-1", ScanGeneration: 2, SourceContextHash: "ctx", ExpectedIdentity: "i2", ContentHash: "sha256:x", LogicalSizeBytes: 1, ProtectedState: cleanup.ProtectedStateUnprotected},
	})
	if err == nil {
		t.Fatal("generation mismatch must fail")
	}
}

func TestResidueEvidenceRequiresPackageUserEvidenceAndKeepsAmbiguity(t *testing.T) {
	snap, err := packageevidence.NormalizeSnapshot(packageevidence.Snapshot{SnapshotID: "snap-1", Generation: 4, Collector: "android", Records: []packageevidence.PackageRecord{{PackageName: "com.example.old", UserID: 10, Installed: false, Archived: true, EnabledState: packageevidence.EnabledStateDisabled, ExternalDataDirs: []string{"/storage/emulated/10/Android/data/com.example.old"}, DataBytes: packageevidence.KnownBytes(4096), CollectionCompleteness: packageevidence.CompletenessSupported}}})
	if err != nil {
		t.Fatal(err)
	}
	residue, err := BuildResidueEvidence(ResidueInput{Snapshot: snap, PackageName: "com.example.old", UserID: 10, Path: "/storage/emulated/10/Android/data/com.example.old/cache", ScanID: "scan-4", ScanGeneration: 4, SpecialContext: "android_external_data"})
	if err != nil {
		t.Fatal(err)
	}
	if residue.Confidence != ConfidenceCandidate || !residue.Candidate.RequiresRecheck || !residue.Evidence.DeletionEligible {
		t.Fatalf("residue did not preserve recheck/eligibility: %+v", residue)
	}
	if residue.Evidence.PackageName != "com.example.old" || residue.SpecialContext == "" {
		t.Fatalf("residue package/special context not bound: %+v", residue)
	}
}

func TestRecommendationRejectsBarePathSafeLabel(t *testing.T) {
	ev := cleanup.ActionablePathEvidence{Path: "/data/local/tmp/maybe", ScanID: "scan", ScanGeneration: 1, SourceSurface: "clean", SourceKind: cleanup.SourceKindBarePath, ExpectedIdentity: "unknown", ProtectedState: cleanup.ProtectedStateUnprotected, DeletionEligible: true, Reason: "guess", LogicalSizeBytes: 1, AllocatedSizeBytes: 1}
	_, err := BuildRecommendation(RecommendationDuplicate, ev, "guessed", true)
	if !errors.Is(err, ErrBarePathUnsafe) {
		t.Fatalf("expected bare-path safety rejection, got %v", err)
	}
}

func TestBoundedRuleExecutionAndConflictDetection(t *testing.T) {
	ev := cleanup.ActionablePathEvidence{Path: "/data/user/0/com.example/cache/x.tmp", ScanID: "scan", ScanGeneration: 2, SourceSurface: "rules", SourceKind: "rule_candidate", ExpectedHash: "sha256:path", ExpectedIdentity: "dev=1", ProtectedState: cleanup.ProtectedStateUnprotected, DeletionEligible: true, Reason: "cache_rule", LogicalSizeBytes: 1, AllocatedSizeBytes: 1}
	results, err := ApplyRule("plan-1", Rule{RuleID: "cache-tmp", Pattern: `cache/.*\.tmp$`, Provenance: "built_in_rule_v1", MaxMatches: 10}, []cleanup.ActionablePathEvidence{ev})
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 1 || results[0].PatternHash == "" || results[0].Provenance == "" {
		t.Fatalf("bad rule result: %+v", results)
	}
	if _, err := ApplyRule("plan-1", Rule{RuleID: "bad", Pattern: `.*`, Provenance: "unbounded", MaxMatches: 10}, []cleanup.ActionablePathEvidence{ev}); !errors.Is(err, ErrPatternTooBroad) {
		t.Fatalf("expected broad pattern rejection, got %v", err)
	}
	rec1, err := BuildRecommendation(RecommendationRule, results[0].Evidence, "rule matched bounded path", true)
	if err != nil {
		t.Fatal(err)
	}
	dupEv := ev
	dupEv.SourceKind = "duplicate_verified_content"
	dupEv.DuplicateGroupID = "dup-1"
	dupEv.ExpectedHash = "sha256:file"
	rec2, err := BuildRecommendation(RecommendationDuplicate, dupEv, "duplicate proof", true)
	if err != nil {
		t.Fatal(err)
	}
	conflicts := DetectPlanConflicts([]Recommendation{rec1, rec2})
	if len(conflicts) != 1 || !conflicts[0].RequiresReview {
		t.Fatalf("expected plan-level conflict, got %+v", conflicts)
	}
}
