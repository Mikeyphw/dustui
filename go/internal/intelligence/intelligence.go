package intelligence

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

const (
	Protocol = "dustui.cleanup.intelligence.v1"
	Phase    = "OV10"

	ConfidenceVerified  = "verified"
	ConfidenceCandidate = "candidate"
	ConfidenceAmbiguous = "ambiguous"
	ConfidenceRejected  = "rejected"

	RecommendationDuplicate = "duplicate"
	RecommendationResidue   = "residue"
	RecommendationRule      = "rule"

	RuleDecisionInclude   = "include"
	RuleDecisionExclude   = "exclude"
	RuleDecisionAmbiguous = "ambiguous"
)

var (
	ErrIntelligenceInvalid = errors.New("cleanup_intelligence_invalid")
	ErrBarePathUnsafe      = errors.New("bare_path_cannot_retain_verified_label")
	ErrPatternTooBroad     = errors.New("rule_pattern_unbounded")
	ErrConflictDetected    = errors.New("cleanup_plan_conflict_detected")
)

type DuplicateInput struct {
	Path                 string `json:"path"`
	ScanID               string `json:"scanId"`
	ScanGeneration       int64  `json:"scanGeneration"`
	SourceContextHash    string `json:"sourceContextHash"`
	ExpectedIdentity     string `json:"expectedIdentity"`
	ContentHash          string `json:"contentHash"`
	LogicalSizeBytes     int64  `json:"logicalSizeBytes"`
	AllocatedSizeBytes   int64  `json:"allocatedSizeBytes"`
	ProtectedState       string `json:"protectedState"`
	RetainedCopyEligible bool   `json:"retainedCopyEligible"`
}

type DuplicateCandidate struct {
	Path               string                         `json:"path"`
	ExpectedIdentity   string                         `json:"expectedIdentity"`
	ContentHash        string                         `json:"contentHash"`
	LogicalSizeBytes   int64                          `json:"logicalSizeBytes"`
	AllocatedSizeBytes int64                          `json:"allocatedSizeBytes"`
	Keep               bool                           `json:"keep"`
	VerificationState  string                         `json:"verificationState"`
	Evidence           cleanup.ActionablePathEvidence `json:"evidence"`
}

type DuplicateGroup struct {
	GroupID           string               `json:"groupId"`
	ScanID            string               `json:"scanId"`
	ScanGeneration    int64                `json:"scanGeneration"`
	SourceContextHash string               `json:"sourceContextHash"`
	LogicalSizeBytes  int64                `json:"logicalSizeBytes"`
	ContentHash       string               `json:"contentHash"`
	VerificationState string               `json:"verificationState"`
	CanonicalKeepPath string               `json:"canonicalKeepPath"`
	RetainedCopyProof string               `json:"retainedCopyProof"`
	Candidates        []DuplicateCandidate `json:"candidates"`
	GroupHash         string               `json:"groupHash"`
	Warnings          []string             `json:"warnings,omitempty"`
}

func BuildDuplicateGroups(inputs []DuplicateInput) ([]DuplicateGroup, error) {
	buckets := map[string][]DuplicateInput{}
	for _, in := range inputs {
		if err := validateDuplicateInput(in); err != nil {
			return nil, err
		}
		key := fmt.Sprintf("%s|%d", in.ContentHash, in.LogicalSizeBytes)
		buckets[key] = append(buckets[key], in)
	}
	groups := []DuplicateGroup{}
	for _, candidates := range buckets {
		if len(candidates) < 2 {
			continue
		}
		sort.Slice(candidates, func(i, j int) bool { return candidates[i].Path < candidates[j].Path })
		first := candidates[0]
		keepIdx := 0
		for i, c := range candidates {
			if c.ProtectedState != cleanup.ProtectedStateUnprotected {
				keepIdx = i
				break
			}
		}
		groupID := stableID("dup", first.ScanID, first.ContentHash, fmt.Sprint(first.LogicalSizeBytes))
		out := DuplicateGroup{GroupID: groupID, ScanID: first.ScanID, ScanGeneration: first.ScanGeneration, SourceContextHash: first.SourceContextHash, LogicalSizeBytes: first.LogicalSizeBytes, ContentHash: first.ContentHash, VerificationState: ConfidenceVerified, CanonicalKeepPath: candidates[keepIdx].Path, RetainedCopyProof: "at_least_one_verified_copy_retained"}
		for i, c := range candidates {
			if c.ScanID != first.ScanID || c.ScanGeneration != first.ScanGeneration || c.SourceContextHash != first.SourceContextHash {
				return nil, fmt.Errorf("%w:duplicate_generation_context_mismatch", ErrIntelligenceInvalid)
			}
			keep := i == keepIdx
			ev := cleanup.ActionablePathEvidence{Path: c.Path, ScanID: c.ScanID, ScanGeneration: c.ScanGeneration, SourceSurface: "duplicates", SourceKind: "duplicate_verified_content", ExpectedHash: c.ContentHash, ExpectedIdentity: c.ExpectedIdentity, RetainedCopyConstraint: "retain_one_verified_copy", ProtectedState: c.ProtectedState, DuplicateGroupID: groupID, DeletionEligible: !keep && c.ProtectedState == cleanup.ProtectedStateUnprotected, Reason: "verified_duplicate", LogicalSizeBytes: c.LogicalSizeBytes, AllocatedSizeBytes: c.AllocatedSizeBytes}
			if err := ev.Validate(); err != nil {
				return nil, err
			}
			out.Candidates = append(out.Candidates, DuplicateCandidate{Path: c.Path, ExpectedIdentity: c.ExpectedIdentity, ContentHash: c.ContentHash, LogicalSizeBytes: c.LogicalSizeBytes, AllocatedSizeBytes: c.AllocatedSizeBytes, Keep: keep, VerificationState: ConfidenceVerified, Evidence: ev})
		}
		out.GroupHash = hashStable(out)
		groups = append(groups, out)
	}
	sort.Slice(groups, func(i, j int) bool { return groups[i].GroupID < groups[j].GroupID })
	return groups, nil
}

func validateDuplicateInput(in DuplicateInput) error {
	if in.Path == "" || !filepath.IsAbs(in.Path) || filepath.Clean(in.Path) != in.Path || strings.Contains(in.Path, "\x00") {
		return fmt.Errorf("%w:path_required", ErrIntelligenceInvalid)
	}
	if in.ScanID == "" || in.ScanGeneration <= 0 || in.SourceContextHash == "" {
		return fmt.Errorf("%w:scan_context_required", ErrIntelligenceInvalid)
	}
	if in.ExpectedIdentity == "" || in.ContentHash == "" {
		return fmt.Errorf("%w:content_identity_required", ErrIntelligenceInvalid)
	}
	if in.LogicalSizeBytes < 0 || in.AllocatedSizeBytes < 0 {
		return fmt.Errorf("%w:size_negative", ErrIntelligenceInvalid)
	}
	if in.ProtectedState == "" {
		in.ProtectedState = cleanup.ProtectedStateUnprotected
	}
	return nil
}

type ResidueInput struct {
	Snapshot       packageevidence.NormalizedSnapshot `json:"snapshot"`
	PackageName    string                             `json:"packageName"`
	UserID         int                                `json:"userId"`
	Path           string                             `json:"path"`
	ScanID         string                             `json:"scanId"`
	ScanGeneration int64                              `json:"scanGeneration"`
	SpecialContext string                             `json:"specialContext"`
}

type ResidueEvidence struct {
	Candidate        packageevidence.ResidueCandidate `json:"candidate"`
	Confidence       string                           `json:"confidence"`
	AmbiguityReasons []string                         `json:"ambiguityReasons,omitempty"`
	SpecialContext   string                           `json:"specialContext"`
	Evidence         cleanup.ActionablePathEvidence   `json:"evidence"`
}

func BuildResidueEvidence(in ResidueInput) (ResidueEvidence, error) {
	cand, err := packageevidence.BuildResidueCandidate(in.Snapshot, in.PackageName, in.UserID, in.Path)
	if err != nil {
		return ResidueEvidence{}, err
	}
	confidence := ConfidenceCandidate
	ambiguity := append([]string{}, cand.RecheckReasons...)
	deletionEligible := !cand.BlocksDeletion && cand.Eligibility == "eligible_after_recheck"
	if cand.CollectionCompleteness != packageevidence.CompletenessSupported || cand.BlocksDeletion {
		confidence = ConfidenceAmbiguous
	}
	if deletionEligible && len(ambiguity) == 1 && ambiguity[0] == "package_state_must_match_at_action_time" {
		confidence = ConfidenceCandidate
	}
	ev := cleanup.ActionablePathEvidence{Path: cand.Path, ScanID: in.ScanID, ScanGeneration: in.ScanGeneration, SourceSurface: "residue", SourceKind: "package_user_evidence", ExpectedHash: cand.EvidenceHash, ExpectedIdentity: "package-key:" + cand.PackageKey, PackageName: cand.PackageName, UserHandle: fmt.Sprint(cand.UserID), Bucket: "residue", ProtectedState: cleanup.ProtectedStateUnprotected, RecommendationID: stableID("residue", cand.PackageKey, cand.Path, cand.EvidenceHash), DeletionEligible: deletionEligible, Reason: "package_residue", LogicalSizeBytes: 0, AllocatedSizeBytes: 0}
	if cand.DerivedFromRegex {
		ev.SourceKind = cleanup.SourceKindBarePath
	}
	if err := ev.Validate(); err != nil {
		return ResidueEvidence{}, err
	}
	sort.Strings(ambiguity)
	return ResidueEvidence{Candidate: cand, Confidence: confidence, AmbiguityReasons: ambiguity, SpecialContext: in.SpecialContext, Evidence: ev}, nil
}

type Recommendation struct {
	ID            string                         `json:"id"`
	Kind          string                         `json:"kind"`
	EvidenceHash  string                         `json:"evidenceHash"`
	Proof         string                         `json:"proof"`
	BoundedImport bool                           `json:"boundedImport"`
	CanAuthorize  bool                           `json:"canAuthorize"`
	Evidence      cleanup.ActionablePathEvidence `json:"evidence"`
}

func BuildRecommendation(kind string, ev cleanup.ActionablePathEvidence, proof string, bounded bool) (Recommendation, error) {
	if proof == "" {
		return Recommendation{}, fmt.Errorf("%w:proof_required", ErrIntelligenceInvalid)
	}
	if !bounded {
		return Recommendation{}, fmt.Errorf("%w:bounded_import_required", ErrIntelligenceInvalid)
	}
	if err := ev.Validate(); err != nil {
		return Recommendation{}, err
	}
	if ev.SourceKind == cleanup.SourceKindBarePath && (kind == RecommendationDuplicate || kind == RecommendationResidue || ev.DeletionEligible) {
		return Recommendation{}, ErrBarePathUnsafe
	}
	id := stableID("rec", kind, ev.Path, ev.ExpectedHash, ev.ExpectedIdentity)
	return Recommendation{ID: id, Kind: kind, EvidenceHash: ev.ExpectedHash, Proof: proof, BoundedImport: true, CanAuthorize: ev.DeletionEligible, Evidence: ev}, nil
}

type Rule struct {
	RuleID     string `json:"ruleId"`
	Pattern    string `json:"pattern"`
	Provenance string `json:"provenance"`
	MaxMatches int    `json:"maxMatches"`
}

type RuleResult struct {
	PlanID      string                         `json:"planId"`
	RuleID      string                         `json:"ruleId"`
	PatternHash string                         `json:"patternHash"`
	Provenance  string                         `json:"provenance"`
	Decision    string                         `json:"decision"`
	Evidence    cleanup.ActionablePathEvidence `json:"evidence"`
}

func ApplyRule(planID string, rule Rule, entries []cleanup.ActionablePathEvidence) ([]RuleResult, error) {
	if planID == "" || rule.RuleID == "" || rule.Pattern == "" || rule.Provenance == "" {
		return nil, fmt.Errorf("%w:rule_identity_required", ErrIntelligenceInvalid)
	}
	if strings.Contains(rule.Pattern, ".*") && len(rule.Pattern) < 6 {
		return nil, ErrPatternTooBroad
	}
	max := rule.MaxMatches
	if max <= 0 || max > 500 {
		return nil, ErrPatternTooBroad
	}
	re, err := regexp.Compile(rule.Pattern)
	if err != nil {
		return nil, fmt.Errorf("%w:rule_pattern_invalid", ErrPatternTooBroad)
	}
	out := []RuleResult{}
	for _, ev := range entries {
		if len(out) >= max {
			return nil, ErrPatternTooBroad
		}
		if !re.MatchString(ev.Path) {
			continue
		}
		ev.RuleID = rule.RuleID
		if err := ev.Validate(); err != nil {
			return nil, err
		}
		out = append(out, RuleResult{PlanID: planID, RuleID: rule.RuleID, PatternHash: stableID("pattern", rule.Pattern), Provenance: rule.Provenance, Decision: RuleDecisionInclude, Evidence: ev})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Evidence.Path < out[j].Evidence.Path })
	return out, nil
}

type PlanConflict struct {
	ConflictID     string   `json:"conflictId"`
	ConflictKind   string   `json:"conflictKind"`
	Paths          []string `json:"paths"`
	Severity       string   `json:"severity"`
	RequiresReview bool     `json:"requiresReview"`
}

func DetectPlanConflicts(recommendations []Recommendation) []PlanConflict {
	byPath := map[string][]Recommendation{}
	for _, r := range recommendations {
		byPath[r.Evidence.Path] = append(byPath[r.Evidence.Path], r)
	}
	conflicts := []PlanConflict{}
	for path, recs := range byPath {
		kinds := map[string]bool{}
		for _, r := range recs {
			kinds[r.Kind] = true
		}
		if len(kinds) > 1 {
			conflicts = append(conflicts, PlanConflict{ConflictID: stableID("conflict", path), ConflictKind: "multiple_recommendation_kinds", Paths: []string{path}, Severity: "review_required", RequiresReview: true})
		}
	}
	sort.Slice(conflicts, func(i, j int) bool { return conflicts[i].ConflictID < conflicts[j].ConflictID })
	return conflicts
}

func Contract() map[string]any {
	return map[string]any{
		"protocol":            Protocol,
		"phase":               Phase,
		"goAuthority":         true,
		"androidBuildability": "intentionally-suspended",
		"domains":             []string{"duplicates", "residue", "recommendations", "rules"},
		"duplicateRules":      []string{"candidate_grouping", "logical_size_semantics", "content_identity_verification", "canonical_keep_selection", "retained_copy_proof", "actionable_evidence_output"},
		"residueRules":        []string{"package_user_evidence_required", "action_time_reevaluation", "ambiguity_confidence", "special_path_context", "exclusions_protection", "actionable_evidence_output"},
		"recommendationRules": []string{"proof_bearing_outputs", "bounded_imports", "bounded_regex_execution", "provenance_preserving_unions", "plan_level_conflict_detection", "protected_descendant_retained_copy_validation"},
		"safetyRules":         []string{"bare_path_never_verified_safe", "no_specialized_label_without_evidence", "no_regex_only_authorization", "no_unbounded_pattern_execution"},
		"schemaTables":        []string{"cleanup_duplicate_groups", "cleanup_duplicate_candidates", "cleanup_residue_evidence", "cleanup_recommendations", "cleanup_rule_results", "cleanup_plan_conflicts"},
	}
}

func stableID(parts ...string) string {
	h := sha256.Sum256([]byte(strings.Join(parts, "\x00")))
	return hex.EncodeToString(h[:12])
}

func hashStable(v any) string {
	encoded, _ := json.Marshal(v)
	sum := sha256.Sum256(encoded)
	return hex.EncodeToString(sum[:])
}
