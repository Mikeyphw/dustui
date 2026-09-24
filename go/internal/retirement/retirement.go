package retirement

// Status captures either the observed repository authority surface or the
// desired retirement target. It is exported as machine-readable evidence.
type Status struct {
	CppScannerRuntime         string `json:"cppScannerRuntime"`
	PythonRuntime             string `json:"pythonRuntime"`
	RoomDomainAuthority       string `json:"roomDomainAuthority"`
	LegacyDestructiveExecutor string `json:"legacyDestructiveExecutor"`
	LegacyNavigation          string `json:"legacyNavigation"`
	GoAuthority               string `json:"goAuthority"`
	AndroidGoRuntimeBinding   string `json:"androidGoRuntimeBinding"`
	GoSQLitePersistence       string `json:"goSQLitePersistence"`
	GenericReleaseTruth       string `json:"genericReleaseTruth"`
}

const None = "none"

// Desired is the structural condition that must be true before OV14/Gate D can
// be considered closed.
func Desired() Status {
	return Status{
		CppScannerRuntime:         None,
		PythonRuntime:             None,
		RoomDomainAuthority:       None,
		LegacyDestructiveExecutor: None,
		LegacyNavigation:          None,
		GoAuthority:               "product-default-qualified",
		AndroidGoRuntimeBinding:   "production",
		GoSQLitePersistence:       "go-owned-sqlite",
		GenericReleaseTruth:       "devtool-only",
	}
}

// Observed records the source-audited state at the OV14 truth hotfix. The
// Python value means a compatibility target is still present; it does not claim
// Python is the current Android production engine.
func Observed() Status {
	return Status{
		CppScannerRuntime:         "active-production",
		PythonRuntime:             "compatibility-only-target-present",
		RoomDomainAuthority:       "active-production",
		LegacyDestructiveExecutor: "active-production",
		LegacyNavigation:          "not-proven-retired",
		GoAuthority:               "go-domain-contracts-present-android-production-unqualified",
		AndroidGoRuntimeBinding:   "missing-production-binding",
		GoSQLitePersistence:       "missing-implementation",
		GenericReleaseTruth:       "duplicated-app-local",
	}
}

// Current deliberately returns observed truth, not the desired end-state.
func Current() Status { return Observed() }
