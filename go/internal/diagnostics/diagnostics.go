package diagnostics

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"
)

const Protocol = "dustui.diagnostics.evidence.v1"

// EvidenceHealth is the only health vocabulary exposed to UI/support export.
type EvidenceHealth string

const (
	EvidenceMissing  EvidenceHealth = "missing"
	EvidenceStale    EvidenceHealth = "stale"
	EvidenceCurrent  EvidenceHealth = "current"
	EvidenceDegraded EvidenceHealth = "degraded"
)

type EvidenceGeneration struct {
	Subject      string         `json:"subject"`
	Generation   int64          `json:"generation"`
	Hash         string         `json:"hash"`
	Health       EvidenceHealth `json:"health"`
	Reason       string         `json:"reason,omitempty"`
	AdvancedAtMS int64          `json:"advancedAtMs,omitempty"`
}

type DerivedEvidence struct {
	Subject          string            `json:"subject"`
	DependsOn        map[string]int64  `json:"dependsOn"`
	DependsOnHash    map[string]string `json:"dependsOnHash"`
	LastComputedAtMS int64             `json:"lastComputedAtMs"`
}

func EvaluateEvidence(g EvidenceGeneration) EvidenceHealth {
	if strings.TrimSpace(g.Subject) == "" || g.Generation <= 0 || strings.TrimSpace(g.Hash) == "" {
		return EvidenceMissing
	}
	if g.Health == EvidenceDegraded || g.Health == EvidenceStale || g.Health == EvidenceMissing {
		return g.Health
	}
	return EvidenceCurrent
}

func EvaluateDerivedHealth(derived DerivedEvidence, authorities []EvidenceGeneration) EvidenceHealth {
	if strings.TrimSpace(derived.Subject) == "" || len(authorities) == 0 {
		return EvidenceMissing
	}
	bySubject := map[string]EvidenceGeneration{}
	for _, authority := range authorities {
		bySubject[authority.Subject] = authority
	}
	for subject, expectedGeneration := range derived.DependsOn {
		authority, ok := bySubject[subject]
		if !ok || EvaluateEvidence(authority) == EvidenceMissing {
			return EvidenceMissing
		}
		if EvaluateEvidence(authority) == EvidenceDegraded {
			return EvidenceDegraded
		}
		if EvaluateEvidence(authority) == EvidenceStale || authority.Generation != expectedGeneration {
			return EvidenceStale
		}
		if expectedHash, ok := derived.DependsOnHash[subject]; ok && expectedHash != "" && authority.Hash != expectedHash {
			return EvidenceStale
		}
	}
	return EvidenceCurrent
}

type RemediationRisk string

const (
	RiskNone        RemediationRisk = "none"
	RiskLow         RemediationRisk = "low"
	RiskDestructive RemediationRisk = "destructive"
)

type Problem struct {
	ID               string          `json:"id"`
	Reason           string          `json:"reason"`
	RecoveryRequired bool            `json:"recoveryRequired"`
	RootCauseID      string          `json:"rootCauseId,omitempty"`
	Guidance         string          `json:"guidance,omitempty"`
	Risk             RemediationRisk `json:"risk"`
}

func RecoveryProblem(id, reason string) Problem {
	return Problem{ID: StableProblemID(id), Reason: reason, RecoveryRequired: true, Risk: RiskLow}
}

func StableProblemID(raw string) string {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	normalized = strings.NewReplacer(" ", "_", "/", "_", "-", "_").Replace(normalized)
	if normalized == "" {
		return "problem_unknown"
	}
	return normalized
}

type ProblemGraph struct {
	problems map[string]Problem
}

func NewProblemGraph() *ProblemGraph { return &ProblemGraph{problems: map[string]Problem{}} }

func (g *ProblemGraph) Add(problem Problem) error {
	if strings.TrimSpace(problem.ID) == "" || strings.TrimSpace(problem.Reason) == "" {
		return errors.New("problem_id_and_reason_required")
	}
	problem.ID = StableProblemID(problem.ID)
	problem.RootCauseID = StableProblemID(problem.RootCauseID)
	if problem.Risk == "" {
		problem.Risk = RiskLow
	}
	g.problems[problem.ID] = problem
	return nil
}

func (g *ProblemGraph) VisibleProblems() []Problem {
	suppressed := map[string]bool{}
	for _, p := range g.problems {
		if p.RootCauseID != "" {
			if _, ok := g.problems[p.RootCauseID]; ok {
				suppressed[p.ID] = true
			}
		}
	}
	ids := make([]string, 0, len(g.problems))
	for id := range g.problems {
		if !suppressed[id] {
			ids = append(ids, id)
		}
	}
	sort.Strings(ids)
	out := make([]Problem, 0, len(ids))
	for _, id := range ids {
		out = append(out, g.problems[id])
	}
	return out
}

type Severity string

const (
	SeverityInfo    Severity = "info"
	SeverityWarning Severity = "warning"
	SeverityError   Severity = "error"
)

type DiagnosticEvent struct {
	SessionID   string            `json:"sessionId"`
	OperationID string            `json:"operationId,omitempty"`
	ParentID    string            `json:"parentId,omitempty"`
	Area        string            `json:"area"`
	Severity    Severity          `json:"severity"`
	Action      string            `json:"action"`
	Result      string            `json:"result"`
	Values      map[string]string `json:"values,omitempty"`
	CreatedAtMS int64             `json:"createdAtMs"`
}

type DiagnosticPolicy struct {
	MaxValueBytes       int  `json:"maxValueBytes"`
	MaxEventsPerSession int  `json:"maxEventsPerSession"`
	MaxSessions         int  `json:"maxSessions"`
	AutoUpload          bool `json:"autoUpload"`
}

func DefaultDiagnosticPolicy() DiagnosticPolicy {
	return DiagnosticPolicy{MaxValueBytes: 128, MaxEventsPerSession: 2000, MaxSessions: 25, AutoUpload: false}
}

func RedactEvent(event DiagnosticEvent, policy DiagnosticPolicy) DiagnosticEvent {
	if policy.MaxValueBytes <= 0 {
		policy.MaxValueBytes = 128
	}
	redacted := event
	redacted.Values = map[string]string{}
	for key, value := range event.Values {
		lk := strings.ToLower(key)
		if strings.Contains(lk, "path") || strings.Contains(lk, "hash") || strings.Contains(lk, "package") || strings.Contains(lk, "device") || strings.Contains(lk, "root") {
			redacted.Values[key] = "redacted:" + shortHash(value)
			continue
		}
		if len(value) > policy.MaxValueBytes {
			value = value[:policy.MaxValueBytes-3] + "..."
		}
		redacted.Values[key] = value
	}
	return redacted
}

type SignificantProblemRecord struct {
	ProblemID   string          `json:"problemId"`
	OperationID string          `json:"operationId,omitempty"`
	Severity    Severity        `json:"severity"`
	Risk        RemediationRisk `json:"risk"`
	Guidance    string          `json:"guidance"`
}

type PrivacyClass string

const (
	PrivacyRawPath    PrivacyClass = "raw_paths"
	PrivacyHash       PrivacyClass = "hashes"
	PrivacyPackage    PrivacyClass = "package_names"
	PrivacyRootDevice PrivacyClass = "root_device_metadata"
)

type RetentionRule struct {
	Class                  PrivacyClass `json:"class"`
	RetainDays             int          `json:"retainDays"`
	PruneWithEvidence      bool         `json:"pruneWithEvidence"`
	ExportRedaction        string       `json:"exportRedaction"`
	BackupTransferEligible bool         `json:"backupTransferEligible"`
}

func RetentionMatrix() []RetentionRule {
	return []RetentionRule{
		{Class: PrivacyRawPath, RetainDays: 7, PruneWithEvidence: true, ExportRedaction: "hash_path_segments", BackupTransferEligible: false},
		{Class: PrivacyHash, RetainDays: 30, PruneWithEvidence: true, ExportRedaction: "truncate_hash", BackupTransferEligible: true},
		{Class: PrivacyPackage, RetainDays: 30, PruneWithEvidence: true, ExportRedaction: "package_alias", BackupTransferEligible: false},
		{Class: PrivacyRootDevice, RetainDays: 14, PruneWithEvidence: true, ExportRedaction: "capability_summary_only", BackupTransferEligible: false},
	}
}

func PruneDeadline(createdAt time.Time, class PrivacyClass) time.Time {
	for _, rule := range RetentionMatrix() {
		if rule.Class == class {
			return createdAt.AddDate(0, 0, rule.RetainDays)
		}
	}
	return createdAt
}

type PrivacyMode string

const (
	PrivacyMinimal  PrivacyMode = "minimal"
	PrivacyDetailed PrivacyMode = "detailed_redacted"
)

type SupportBundle struct {
	Manifest              string               `json:"manifest"`
	Protocol              string               `json:"protocol"`
	PrivacyMode           PrivacyMode          `json:"privacyMode"`
	EvidenceGenerations   []EvidenceGeneration `json:"evidenceGenerations"`
	Problems              []Problem            `json:"problems"`
	DiagnosticEventCount  int                  `json:"diagnosticEventCount"`
	Checksums             map[string]string    `json:"checksums"`
	IncludesFileContent   bool                 `json:"includesFileContent"`
	AutomaticUpload       bool                 `json:"automaticUpload"`
	ReproducibleExportKey string               `json:"reproducibleExportKey"`
}

func NewSupportBundle(mode PrivacyMode, generations []EvidenceGeneration, problems []Problem, events []DiagnosticEvent) SupportBundle {
	sort.Slice(generations, func(i, j int) bool { return generations[i].Subject < generations[j].Subject })
	sort.Slice(problems, func(i, j int) bool { return problems[i].ID < problems[j].ID })
	manifest := fmt.Sprintf("dustui-support-bundle/%s/%d/%d", mode, len(generations), len(problems))
	bundle := SupportBundle{
		Manifest:             manifest,
		Protocol:             Protocol,
		PrivacyMode:          mode,
		EvidenceGenerations:  generations,
		Problems:             problems,
		DiagnosticEventCount: len(events),
		Checksums:            map[string]string{},
		IncludesFileContent:  false,
		AutomaticUpload:      false,
	}
	bundle.Checksums["manifest"] = checksum(manifest)
	bundle.Checksums["evidence"] = checksumJSON(generations)
	bundle.Checksums["problems"] = checksumJSON(problems)
	bundle.ReproducibleExportKey = checksumJSON(bundle.Checksums)
	return bundle
}

type ContractReport struct {
	Phase                      string           `json:"phase"`
	Protocol                   string           `json:"protocol"`
	EvidenceStates             []EvidenceHealth `json:"evidenceStates"`
	CompletedAloneHealthy      bool             `json:"completedAloneHealthy"`
	DerivedHealthInvalidates   bool             `json:"derivedHealthInvalidates"`
	ProblemGraph               bool             `json:"problemGraph"`
	RootCauseSuppression       bool             `json:"rootCauseSuppression"`
	StableProblemIDs           bool             `json:"stableProblemIds"`
	StructuredPersistentEvents bool             `json:"structuredPersistentEvents"`
	RedactionBeforePersistence bool             `json:"redactionBeforePersistence"`
	BoundedRetention           bool             `json:"boundedRetention"`
	AutomaticUpload            bool             `json:"automaticUpload"`
	SupportBundle              bool             `json:"supportBundle"`
	PrivacyClasses             []PrivacyClass   `json:"privacyClasses"`
	CanonicalUX                string           `json:"canonicalUx"`
}

func Contract() ContractReport {
	return ContractReport{
		Phase:                      "OV12",
		Protocol:                   Protocol,
		EvidenceStates:             []EvidenceHealth{EvidenceMissing, EvidenceStale, EvidenceCurrent, EvidenceDegraded},
		CompletedAloneHealthy:      false,
		DerivedHealthInvalidates:   true,
		ProblemGraph:               true,
		RootCauseSuppression:       true,
		StableProblemIDs:           true,
		StructuredPersistentEvents: true,
		RedactionBeforePersistence: true,
		BoundedRetention:           true,
		AutomaticUpload:            false,
		SupportBundle:              true,
		PrivacyClasses:             []PrivacyClass{PrivacyRawPath, PrivacyHash, PrivacyPackage, PrivacyRootDevice},
		CanonicalUX:                "System > Diagnostics",
	}
}

func checksumJSON(value any) string {
	encoded, _ := json.Marshal(value)
	return checksum(string(encoded))
}

func checksum(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func shortHash(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:8])
}
