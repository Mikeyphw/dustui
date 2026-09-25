package state

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/authority"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

func TestAUD_FIX03AuthoritySurvivesRestartWithGenerationAndEvidenceTruth(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "authority.db")
	completed := int64(2000)
	store, err := OpenSQLiteStore(path)
	if err != nil {
		t.Fatal(err)
	}
	session := AndroidScanSession{ID: "scan-1", RootPath: "/data", OptionsJSON: "{}", Status: "COMPLETED", Backend: "go", StartedAt: 1000, CompletedAt: &completed, Generation: 7, ObservedEntryCount: 1, PersistedEntryCount: 1, IntegrityStatus: "COMPLETE"}
	if err := store.UpsertAndroidScanSession(ctx, session); err != nil {
		t.Fatal(err)
	}
	if err := store.UpsertAndroidScanEntries(ctx, []AndroidScanEntry{{ScanID: "scan-1", Path: "/data/user/0/com.example/cache/a.bin", ParentPath: "/data/user/0/com.example/cache", Name: "a.bin", SizeBytes: 4096, AllocatedBytes: 4096, ModifiedAt: 1000}}); err != nil {
		t.Fatal(err)
	}
	normalized, err := packageevidence.NormalizeSnapshot(packageevidence.Snapshot{SnapshotID: "pkg-7", Generation: 7, Collector: "android-platform-adapter", Completeness: packageevidence.CompletenessPartial, Records: []packageevidence.PackageRecord{{PackageName: "com.example", UserID: 0, Installed: true, EnabledState: packageevidence.EnabledStateEnabled, CEDataDirs: []string{"/data/user/0/com.example"}, CollectionCompleteness: packageevidence.CompletenessPartial, DegradationReasons: []string{"package_byte_counts_not_available"}}}})
	if err != nil {
		t.Fatal(err)
	}
	if err := store.ReplacePackageEvidence(ctx, "scan-1", normalized, "android-platform-adapter", []string{"package_byte_counts_not_available"}); err != nil {
		t.Fatal(err)
	}
	req := authority.Request{Schema: authority.RequestSchema, Domain: "package-storage", ScanID: "scan-1", Generation: 7}
	before, err := store.DomainAuthority(ctx, req, ProductInsightsOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if before.Authority.EvidenceState != authority.EvidencePartial || before.Authority.Freshness != authority.FreshnessFresh || !before.Authority.Durable || !before.Authority.Bounded {
		t.Fatalf("bad pre-restart authority: %#v", before.Authority)
	}
	// A newer snapshot for another scan with the same generation must never bleed into scan-1.
	other := AndroidScanSession{ID: "scan-2", RootPath: "/storage/emulated/0", OptionsJSON: "{}", Status: "COMPLETED", Backend: "go", StartedAt: 1500, CompletedAt: &completed, Generation: 7, IntegrityStatus: "COMPLETE"}
	if err := store.UpsertAndroidScanSession(ctx, other); err != nil {
		t.Fatal(err)
	}
	otherSnapshot, err := packageevidence.NormalizeSnapshot(packageevidence.Snapshot{SnapshotID: "pkg-7-other", Generation: 7, Collector: "android-platform-adapter", Completeness: packageevidence.CompletenessSupported, Records: []packageevidence.PackageRecord{{PackageName: "com.other", UserID: 0, Installed: true, EnabledState: packageevidence.EnabledStateEnabled, CollectionCompleteness: packageevidence.CompletenessSupported}}})
	if err != nil {
		t.Fatal(err)
	}
	if err := store.ReplacePackageEvidence(ctx, "scan-2", otherSnapshot, "android-platform-adapter", nil); err != nil {
		t.Fatal(err)
	}
	rebound, err := store.DomainAuthority(ctx, req, ProductInsightsOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if rebound.Authority.EvidenceState != authority.EvidencePartial {
		t.Fatalf("cross-scan evidence bleed: %#v", rebound.Authority)
	}
	report := VerificationReport{SchemaVersion: 2, OperationID: "op-1", ScanID: "scan-1", RootPath: "/data", VerificationAttempts: 1, LastVerifiedAt: 2000}
	if err := store.PersistVerificationReport(ctx, report, 7); err != nil {
		t.Fatal(err)
	}
	if err := store.Close(); err != nil {
		t.Fatal(err)
	}

	store, err = OpenSQLiteStore(path)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	after, err := store.DomainAuthority(ctx, req, ProductInsightsOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if after.Authority.Generation != 7 || after.Authority.EvidenceState != authority.EvidencePartial || after.Authority.Source != "go-sqlite" {
		t.Fatalf("restart lost authority truth: %#v", after.Authority)
	}
	restored, gen, err := store.VerificationReport(ctx, "op-1")
	if err != nil {
		t.Fatal(err)
	}
	if restored == nil || gen != 7 || restored.OperationID != "op-1" || restored.VerificationAttempts != 1 {
		t.Fatalf("verification not durable: %#v gen=%d", restored, gen)
	}
	reports, err := store.VerificationReports(ctx, 100)
	if err != nil {
		t.Fatal(err)
	}
	if len(reports) != 1 || reports[0].Report.OperationID != "op-1" || reports[0].Generation != 7 {
		t.Fatalf("verification list not durable: %#v", reports)
	}
	stale := req
	stale.Generation = 6
	if _, err := store.DomainAuthority(ctx, stale, ProductInsightsOptions{}); err == nil {
		t.Fatal("stale generation accepted")
	}
}
