package packageevidence

import (
	"errors"
	"testing"
)

func TestOV09MultiUserPackageEvidenceIsDistinctAndNormalized(t *testing.T) {
	snap, err := NormalizeSnapshot(Snapshot{SnapshotID: "pkg-1", Generation: 9, Collector: "android-platform-api", Records: []PackageRecord{
		{PackageName: "com.example.app", UserID: 0, Installed: true, EnabledState: EnabledStateEnabled, CEDataDirs: []string{"/data/user/0/com.example.app"}, DataBytes: KnownBytes(1024), CollectionCompleteness: CompletenessSupported},
		{PackageName: "com.example.app", UserID: 10, Archived: true, EnabledState: EnabledStateDisabled, CEDataDirs: []string{"/data/user/10/com.example.app"}, DataBytes: KnownBytes(2048), CollectionCompleteness: CompletenessSupported},
	}})
	if err != nil {
		t.Fatal(err)
	}
	if len(snap.Records) != 2 {
		t.Fatalf("expected two user-scoped records: %+v", snap.Records)
	}
	if snap.Records[0].Key == snap.Records[1].Key {
		t.Fatalf("multi-user keys collapsed: %+v", snap.Records)
	}
	if snap.EvidenceHash == "" {
		t.Fatal("snapshot evidence hash missing")
	}
}

func TestOV09PackageStatesDriveTruthfulUIAndDeletionEligibility(t *testing.T) {
	snap, err := NormalizeSnapshot(Snapshot{SnapshotID: "pkg-2", Generation: 9, Collector: "android-platform-api", Records: []PackageRecord{
		{PackageName: "com.android.systemui", UserID: 0, Installed: true, SystemApp: true, EnabledState: EnabledStateEnabled, SourceDirs: []string{"/system/priv-app/SystemUI/SystemUI.apk"}, CodeBytes: KnownBytes(1), CollectionCompleteness: CompletenessSupported},
		{PackageName: "com.example.disabled", UserID: 0, Installed: true, EnabledState: EnabledStateDisabled, CEDataDirs: []string{"/data/user/0/com.example.disabled"}, DataBytes: KnownBytes(1), CollectionCompleteness: CompletenessSupported},
		{PackageName: "com.example.archived", UserID: 0, Archived: true, EnabledState: EnabledStateDisabled, CEDataDirs: []string{"/data/user/0/com.example.archived"}, DataBytes: KnownBytes(1), CollectionCompleteness: CompletenessSupported},
	}})
	if err != nil {
		t.Fatal(err)
	}
	systemCandidate, err := BuildResidueCandidate(snap, "com.android.systemui", 0, "/data/user/0/com.android.systemui/cache")
	if err != nil {
		t.Fatal(err)
	}
	if !systemCandidate.BlocksDeletion || systemCandidate.Eligibility != "blocked_active_or_system_package" {
		t.Fatalf("system package must block deletion: %+v", systemCandidate)
	}
	archivedCandidate, err := BuildResidueCandidate(snap, "com.example.archived", 0, "/data/user/0/com.example.archived/cache")
	if err != nil {
		t.Fatal(err)
	}
	if archivedCandidate.BlocksDeletion || archivedCandidate.Eligibility != "eligible_after_recheck" || !archivedCandidate.RequiresRecheck {
		t.Fatalf("archived package should be eligible only after recheck: %+v", archivedCandidate)
	}
}

func TestOV09PartialEvidenceIsNotRenderedAsZero(t *testing.T) {
	if _, err := NormalizeSnapshot(Snapshot{SnapshotID: "pkg-3", Generation: 9, Collector: "android-platform-api", Records: []PackageRecord{
		{PackageName: "com.example.partial", UserID: 0, Installed: true, EnabledState: EnabledStateEnabled, DataBytes: KnownBytes(0), CollectionCompleteness: CompletenessPartial, DegradationReasons: []string{"usage_stats_permission_missing"}},
	}}); !errors.Is(err, ErrInvalidPackageEvidence) {
		t.Fatalf("partial evidence with known zero should fail, got %v", err)
	}
	snap, err := NormalizeSnapshot(Snapshot{SnapshotID: "pkg-4", Generation: 9, Collector: "android-platform-api", Records: []PackageRecord{
		{PackageName: "com.example.partial", UserID: 0, Installed: true, EnabledState: EnabledStateEnabled, DataBytes: UnknownBytes(), CollectionCompleteness: CompletenessPartial, DegradationReasons: []string{"usage_stats_permission_missing"}},
	}})
	if err != nil {
		t.Fatal(err)
	}
	if snap.Records[0].UIState != UIStatePartial || snap.Records[0].DataBytes.Known {
		t.Fatalf("partial evidence must remain unknown/partial, not zero: %+v", snap.Records[0])
	}
}

func TestOV09ActionTimeReEvaluationIsRequiredForPackageSensitiveDeletion(t *testing.T) {
	snap, err := NormalizeSnapshot(Snapshot{SnapshotID: "pkg-5", Generation: 9, Collector: "android-platform-api", Records: []PackageRecord{
		{PackageName: "com.example.gone", UserID: 0, Archived: true, EnabledState: EnabledStateDisabled, CollectionCompleteness: CompletenessSupported},
	}})
	if err != nil {
		t.Fatal(err)
	}
	candidate, err := BuildResidueCandidate(snap, "com.example.gone", 0, "/data/user/0/com.example.gone/cache")
	if err != nil {
		t.Fatal(err)
	}
	if err := VerifyActionTime(candidate, ActionTimeContext{EvidenceHash: "stale", PackageKey: candidate.PackageKey, Path: candidate.Path}); !errors.Is(err, ErrRecheckRequired) {
		t.Fatalf("stale evidence must fail action-time recheck, got %v", err)
	}
	if err := VerifyActionTime(candidate, ActionTimeContext{EvidenceHash: candidate.EvidenceHash, PackageKey: candidate.PackageKey, Path: candidate.Path}); err != nil {
		t.Fatalf("matching action-time evidence should pass: %v", err)
	}
}

func TestOV09RegexOnlyResidueEvidenceIsForbidden(t *testing.T) {
	candidate := ResidueCandidate{PackageKey: "u0:com.example", Path: "/sdcard/Android/data/com.example", DerivedFromRegex: true, RequiresRecheck: true}
	if err := VerifyActionTime(candidate, ActionTimeContext{EvidenceHash: "hash", PackageKey: candidate.PackageKey, Path: candidate.Path}); !errors.Is(err, ErrEvidenceRequired) {
		t.Fatalf("regex-only evidence should be forbidden, got %v", err)
	}
}
