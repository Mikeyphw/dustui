package cleanup

import (
	"errors"
	"testing"
	"time"
)

func cloneManifestForMutationTest(m Manifest) Manifest {
	clone := m
	clone.Entries = append([]ManifestEntry{}, m.Entries...)
	for i := range clone.Entries {
		clone.Entries[i].ExactDescendants = append([]string{}, m.Entries[i].ExactDescendants...)
		clone.Entries[i].Warnings = append([]string{}, m.Entries[i].Warnings...)
	}
	return clone
}

func sampleStoreAndCapability(t *testing.T, manifest Manifest, id string, now time.Time) *CapabilityStore {
	t.Helper()
	cap, err := NewCapability(id, manifest, now, now.Add(time.Hour))
	if err != nil {
		t.Fatal(err)
	}
	store := NewCapabilityStore()
	if err := store.Put(cap); err != nil {
		t.Fatal(err)
	}
	return store
}

func TestOV08MutationExecutesExactManifestAndVerifiesAbsent(t *testing.T) {
	m := sampleManifest(t)
	now := time.Unix(100, 0)
	store := sampleStoreAndCapability(t, m, "cap-mut", now)
	fs := NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	result, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-mut", Now: now.Add(time.Second), Filesystem: fs})
	if err != nil {
		t.Fatalf("mutation should complete: %v %+v", err, result)
	}
	if result.State != MutationComplete || result.Certainty != "verified" || !result.StaleAfterCleanup || !result.ReconcileRequired {
		t.Fatalf("unexpected mutation result: %+v", result)
	}
	if result.ReclaimedBytes != m.Entries[0].Evidence.AllocatedSizeBytes || len(result.Entries) != 1 || result.Entries[0].State != MutationComplete {
		t.Fatalf("unexpected entry result: %+v", result)
	}
}

func TestOV08MutationRejectsManifestTamperIdentityReplacementProtectionAndRetainedCopyFailure(t *testing.T) {
	m := sampleManifest(t)
	now := time.Unix(100, 0)

	store := sampleStoreAndCapability(t, m, "cap-tamper", now)
	tampered := cloneManifestForMutationTest(m)
	tampered.Entries[0].ExactDescendants = append(tampered.Entries[0].ExactDescendants, "/data/user/0/example/cache/extra")
	fs := NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: tampered, CapabilityID: "cap-tamper", Now: now.Add(time.Second), Filesystem: fs}); err == nil {
		t.Fatal("tampered manifest must fail before mutation")
	}

	store = sampleStoreAndCapability(t, m, "cap-identity", now)
	fs = NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: "dev=changed"})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-identity", Now: now.Add(time.Second), Filesystem: fs}); !errors.Is(err, ErrMutationDenied) {
		t.Fatalf("identity replacement should be denied, got %v", err)
	}

	protected := cloneManifestForMutationTest(m)
	protected.Entries[0].Protection = ProtectedStateProtected
	protected.Hash = ""
	protected.Hash = hashStable(protected)
	store = sampleStoreAndCapability(t, protected, "cap-protected", now)
	fs = NewInMemoryMutationFS(map[string]string{protected.Entries[0].Evidence.Path: protected.Entries[0].Evidence.ExpectedIdentity})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: protected, CapabilityID: "cap-protected", Now: now.Add(time.Second), Filesystem: fs}); !errors.Is(err, ErrMutationDenied) {
		t.Fatalf("protected descendant should be denied, got %v", err)
	}

	retained := cloneManifestForMutationTest(m)
	retained.Entries[0].RetainedCopySatisfied = false
	retained.Hash = ""
	retained.Hash = hashStable(retained)
	if err := retained.Validate(); err == nil {
		t.Fatal("retained-copy failure must fail closed before capability issuance or mutation")
	}
}

func TestOV08MutationCapabilityReplayExpiryInterruptedAndVerificationMismatch(t *testing.T) {
	m := sampleManifest(t)
	now := time.Unix(100, 0)
	store := sampleStoreAndCapability(t, m, "cap-replay", now)
	fs := NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-replay", Now: now.Add(time.Second), Filesystem: fs}); err != nil {
		t.Fatal(err)
	}
	fs = NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-replay", Now: now.Add(2 * time.Second), Filesystem: fs}); !errors.Is(err, ErrCapabilityReplay) {
		t.Fatalf("capability replay should fail, got %v", err)
	}

	cap, err := NewCapability("cap-expire", m, now, now.Add(time.Second))
	if err != nil {
		t.Fatal(err)
	}
	store = NewCapabilityStore()
	if err := store.Put(cap); err != nil {
		t.Fatal(err)
	}
	fs = NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	if _, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-expire", Now: now.Add(time.Hour), Filesystem: fs}); !errors.Is(err, ErrCapabilityExpired) {
		t.Fatalf("expired capability should fail, got %v", err)
	}

	store = sampleStoreAndCapability(t, m, "cap-interrupt", now)
	fs = NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	fs.SetRemoveError(m.Entries[0].Evidence.Path, errors.New("root transport died"))
	result, err := NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-interrupt", Now: now.Add(time.Second), Filesystem: fs})
	if !errors.Is(err, ErrMutationInterrupted) || result.State != MutationInterrupted || !result.ReconcileRequired {
		t.Fatalf("interruption must require reconcile, got %v %+v", err, result)
	}

	store = sampleStoreAndCapability(t, m, "cap-verify", now)
	fs = NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	fs.SetVerificationGhost(m.Entries[0].Evidence.Path, m.Entries[0].Evidence.ExpectedIdentity)
	result, err = NewExecutor().Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-verify", Now: now.Add(time.Second), Filesystem: fs})
	if !errors.Is(err, ErrVerificationMismatch) || result.State != MutationCleanupDebt || result.Certainty != "verification_failed" {
		t.Fatalf("verification mismatch should create cleanup debt, got %v %+v", err, result)
	}
}

func TestOV08MutationExecutorIsGlobalSingleFlightAndReconcileOnly(t *testing.T) {
	ex := NewExecutor()
	ex.mu.Lock()
	ex.running = true
	ex.mu.Unlock()
	m := sampleManifest(t)
	store := sampleStoreAndCapability(t, m, "cap-flight", time.Unix(100, 0))
	fs := NewInMemoryMutationFS(map[string]string{m.Entries[0].Evidence.Path: m.Entries[0].Evidence.ExpectedIdentity})
	if _, err := ex.Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-flight", Now: time.Unix(101, 0), Filesystem: fs}); !errors.Is(err, ErrMutationSingleFlight) {
		t.Fatalf("single-flight should reject second mutation, got %v", err)
	}
	ex.mu.Lock()
	ex.running = false
	ex.mu.Unlock()
	result, err := ex.Execute(store, MutationRequest{Manifest: m, CapabilityID: "cap-flight", Now: time.Unix(102, 0), Filesystem: fs})
	if err != nil {
		t.Fatal(err)
	}
	if recovered, ok := ex.Reconcile(result.OperationID); !ok || recovered.State != MutationComplete {
		t.Fatalf("reconcile should return durable terminal result: %+v %v", recovered, ok)
	}
}
