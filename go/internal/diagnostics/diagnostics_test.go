package diagnostics

import (
	"strings"
	"testing"
	"time"
)

func TestEvidenceHealthInvalidatesDerivedEvidence(t *testing.T) {
	derived := DerivedEvidence{
		Subject:       "overview-health",
		DependsOn:     map[string]int64{"scan": 10, "package-storage": 4},
		DependsOnHash: map[string]string{"scan": "scan10", "package-storage": "pkg4"},
	}
	authorities := []EvidenceGeneration{
		{Subject: "scan", Generation: 10, Hash: "scan10", Health: EvidenceCurrent},
		{Subject: "package-storage", Generation: 4, Hash: "pkg4", Health: EvidenceCurrent},
	}
	if got := EvaluateDerivedHealth(derived, authorities); got != EvidenceCurrent {
		t.Fatalf("expected current derived health, got %s", got)
	}
	authorities[0].Generation = 11
	if got := EvaluateDerivedHealth(derived, authorities); got != EvidenceStale {
		t.Fatalf("advanced authority must stale derived health, got %s", got)
	}
	authorities[0] = EvidenceGeneration{Subject: "scan", Generation: 10, Hash: "scan10", Health: EvidenceDegraded}
	if got := EvaluateDerivedHealth(derived, authorities); got != EvidenceDegraded {
		t.Fatalf("degraded authority must degrade derived health, got %s", got)
	}
}

func TestCompletedAloneNeverHealthy(t *testing.T) {
	if Contract().CompletedAloneHealthy {
		t.Fatalf("COMPLETED alone must never produce healthy")
	}
	if got := EvaluateEvidence(EvidenceGeneration{Subject: "scan", Generation: 1, Hash: "", Health: EvidenceCurrent}); got != EvidenceMissing {
		t.Fatalf("missing hash must not become current just because an operation completed: %s", got)
	}
}

func TestProblemGraphSuppressesDependentSymptoms(t *testing.T) {
	graph := NewProblemGraph()
	if err := graph.Add(Problem{ID: "Root Evidence Stale", Reason: "scan evidence is stale", Guidance: "Run a fresh scan", Risk: RiskLow}); err != nil {
		t.Fatal(err)
	}
	if err := graph.Add(Problem{ID: "Cleanup Blocked", Reason: "cleanup blocked by stale evidence", RootCauseID: "Root Evidence Stale", Guidance: "Resolve root stale evidence", Risk: RiskDestructive}); err != nil {
		t.Fatal(err)
	}
	visible := graph.VisibleProblems()
	if len(visible) != 1 || visible[0].ID != "root_evidence_stale" {
		t.Fatalf("expected root only after symptom suppression, got %#v", visible)
	}
}

func TestDiagnosticsRedactBeforePersistenceAndBoundValues(t *testing.T) {
	event := DiagnosticEvent{SessionID: "s1", Area: "scan", Severity: SeverityWarning, Action: "persist", Result: "blocked", Values: map[string]string{
		"rawPath":     "/sdcard/secret/photo.jpg",
		"packageName": "com.example.private",
		"message":     strings.Repeat("a", 200),
	}}
	redacted := RedactEvent(event, DefaultDiagnosticPolicy())
	if strings.Contains(redacted.Values["rawPath"], "/sdcard") || !strings.HasPrefix(redacted.Values["rawPath"], "redacted:") {
		t.Fatalf("raw paths must be redacted before persistence: %#v", redacted.Values)
	}
	if strings.Contains(redacted.Values["packageName"], "com.example") {
		t.Fatalf("package names must be redacted before persistence: %#v", redacted.Values)
	}
	if len(redacted.Values["message"]) > 129 {
		t.Fatalf("diagnostic values must be bounded, got %d bytes", len(redacted.Values["message"]))
	}
	if DefaultDiagnosticPolicy().AutoUpload {
		t.Fatalf("diagnostics must not auto-upload")
	}
}

func TestRetentionMatrixAndSupportBundleArePrivacyQualified(t *testing.T) {
	created := time.Unix(1000, 0)
	if !PruneDeadline(created, PrivacyRawPath).After(created) {
		t.Fatalf("raw-path retention must have a deterministic prune deadline")
	}
	matrix := RetentionMatrix()
	if len(matrix) != 4 {
		t.Fatalf("expected four privacy classes, got %d", len(matrix))
	}
	for _, rule := range matrix {
		if !rule.PruneWithEvidence || rule.ExportRedaction == "" || rule.RetainDays <= 0 {
			t.Fatalf("invalid retention rule: %#v", rule)
		}
	}
	bundle := NewSupportBundle(
		PrivacyMinimal,
		[]EvidenceGeneration{{Subject: "scan", Generation: 1, Hash: "scan1", Health: EvidenceCurrent}},
		[]Problem{RecoveryProblem("root stale", "stale scan")},
		[]DiagnosticEvent{{SessionID: "s1", Area: "scan", Severity: SeverityInfo, Action: "export", Result: "ok"}},
	)
	if bundle.IncludesFileContent || bundle.AutomaticUpload {
		t.Fatalf("support bundle must not leak file content or upload automatically: %#v", bundle)
	}
	if bundle.Checksums["manifest"] == "" || bundle.ReproducibleExportKey == "" {
		t.Fatalf("support bundle must be sealed and reproducible: %#v", bundle)
	}
}
