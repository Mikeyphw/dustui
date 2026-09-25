package shell

import "testing"

func TestAUD_FIX09BindingRejectsEveryStaleIdentityDimension(t *testing.T) {
	route, err := Restore(RouteFilesItem, "/sdcard/A.txt", 9)
	if err != nil {
		t.Fatal(err)
	}
	active, err := NewBinding(route, "req-9", "q:alpha")
	if err != nil {
		t.Fatal(err)
	}
	if err := AcceptBinding(active, active); err != nil {
		t.Fatalf("matching binding rejected: %v", err)
	}

	cases := []RouteBinding{
		func() RouteBinding {
			v := active
			v.RouteKey = RouteCleanCandidate
			v.EntityKind = "candidate"
			v.EntityID = "c1"
			return v
		}(),
		func() RouteBinding { v := active; v.RouteGeneration++; return v }(),
		func() RouteBinding { v := active; v.EntityKind = "package"; return v }(),
		func() RouteBinding { v := active; v.EntityID = "/sdcard/B.txt"; return v }(),
		func() RouteBinding { v := active; v.RequestID = "req-late"; return v }(),
		func() RouteBinding { v := active; v.Fingerprint = "q:beta"; return v }(),
	}
	for i, stale := range cases {
		if err := AcceptBinding(active, stale); err == nil {
			t.Fatalf("stale binding case %d accepted: %+v", i, stale)
		}
	}
}

func TestAUD_FIX09TypedActionGatesHaveStableReasonAndRemediation(t *testing.T) {
	gate := EvaluateActionGate(GateContext{Action: "cleanup.execute", HasTrustedScan: true, ScanFresh: false, CapabilityAvailable: true, EvidenceSupported: true})
	if gate.Allowed || gate.ReasonCode != "scan_stale" || gate.Message == "" || len(gate.RemediationActions) != 1 || gate.RemediationActions[0].Code != "start_scan" {
		t.Fatalf("bad stale gate: %+v", gate)
	}
	allowed := EvaluateActionGate(GateContext{Action: "cleanup.execute", HasTrustedScan: true, ScanFresh: true, CapabilityAvailable: true, EvidenceSupported: true, OperationActive: true})
	if !allowed.Allowed || allowed.ReasonCode != "allowed" || allowed.Message == "" || allowed.RemediationActions == nil {
		t.Fatalf("bad allowed gate: %+v", allowed)
	}
	certification := EvaluateActionGate(GateContext{Action: "certification.run", HasTrustedScan: false, ScanFresh: true, CapabilityAvailable: true, EvidenceSupported: false})
	if certification.Allowed || certification.ReasonCode != "scan_required" || len(certification.RemediationActions) == 0 {
		t.Fatalf("certification must consume scan evidence gate: %+v", certification)
	}
}

func TestAUD_FIX09SharedScreenStatesRequireTypedRecovery(t *testing.T) {
	route, _ := Restore(RouteActivityRun, "op-1", 4)
	binding, _ := NewBinding(route, "req-a", "op:op-1")
	blocked, err := BuildScreenState(ScreenStateRequest{
		Binding: binding, Kind: StateBlocked,
		Gates: []GateContext{{Action: "activity.cancel", RecoveryRequired: true, CapabilityAvailable: true, OperationActive: true}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if blocked.Kind != StateBlocked || len(blocked.ActionGates) != 1 || blocked.ActionGates[0].ReasonCode != "recovery_required" {
		t.Fatalf("bad blocked state: %+v", blocked)
	}
	if _, err := BuildScreenState(ScreenStateRequest{Binding: binding, Kind: StateEmpty, ReasonCode: "no_results"}); err == nil {
		t.Fatal("empty state without recovery must be rejected")
	}
}

func TestAUD_FIX09AdversarialLateResponsesAcrossCriticalSurfaces(t *testing.T) {
	cases := []struct {
		name        string
		route       RouteID
		entity      string
		fingerprint string
	}{
		{"directory-query", RouteFilesSearch, "", "query:alpha:page:0"},
		{"package-switch", RouteAppsPackage, "com.example.one", "package:com.example.one"},
		{"cleanup-review", RouteCleanReview, "manifest-1", "manifest:manifest-1"},
		{"activity-selection", RouteActivityRun, "op-1", "operation:op-1"},
		{"diagnostics-generation", RouteSystemDiagnostics, "", "evidence-generation:8"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			route, err := Restore(tc.route, tc.entity, 8)
			if err != nil {
				t.Fatal(err)
			}
			active, err := NewBinding(route, "req-current", tc.fingerprint)
			if err != nil {
				t.Fatal(err)
			}
			late := active
			late.RequestID = "req-late"
			if err := AcceptBinding(active, late); err == nil {
				t.Fatalf("late %s response accepted", tc.name)
			}
		})
	}
}

func TestAUD_FIX10TruthGatesCoverRootScopeIntegrityAndEvidence(t *testing.T) {
	root := EvaluateActionGate(GateContext{Action: "scan.start", CapabilityAvailable: false})
	if root.Allowed || root.ReasonCode != "root_unavailable" {
		t.Fatalf("root gate = %+v", root)
	}
	preflight := EvaluateActionGate(GateContext{Action: "scan.start", CapabilityAvailable: true, PreflightChecked: true, PreflightPassed: false})
	if preflight.Allowed || preflight.ReasonCode != "preflight_failed" {
		t.Fatalf("preflight gate = %+v", preflight)
	}
	wrongScope := EvaluateActionGate(GateContext{Action: "apps.inspect", HasTrustedScan: true, ScanFresh: true, CapabilityAvailable: true, EvidenceSupported: true, ScopePath: "/data/adb/modules", IntegrityStatus: "COMPLETE"})
	if wrongScope.Allowed || wrongScope.ReasonCode != "wrong_scope" {
		t.Fatalf("wrong-scope gate = %+v", wrongScope)
	}
	degraded := EvaluateActionGate(GateContext{Action: "cleanup.review", HasTrustedScan: true, ScanFresh: true, CapabilityAvailable: true, EvidenceSupported: true, ScopePath: "/data", IntegrityStatus: "TRUNCATED"})
	if degraded.Allowed || degraded.ReasonCode != "integrity_degraded" {
		t.Fatalf("integrity gate = %+v", degraded)
	}
	unsupported := EvaluateActionGate(GateContext{Action: "apps.inspect", HasTrustedScan: true, ScanFresh: true, CapabilityAvailable: true, EvidenceSupported: false, ScopePath: "/data/user/0", IntegrityStatus: "COMPLETE"})
	if unsupported.Allowed || unsupported.ReasonCode != "evidence_unavailable" {
		t.Fatalf("evidence gate = %+v", unsupported)
	}
}
