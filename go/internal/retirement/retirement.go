package retirement

// Status captures the post-OV14 production runtime authority surface.
// The values are intentionally string constants because they are exported as
// evidence and compared by validators without importing implementation details.
type Status struct {
    CppScannerRuntime         string `json:"cppScannerRuntime"`
    PythonRuntime             string `json:"pythonRuntime"`
    RoomDomainAuthority       string `json:"roomDomainAuthority"`
    LegacyDestructiveExecutor string `json:"legacyDestructiveExecutor"`
    LegacyNavigation          string `json:"legacyNavigation"`
    GoAuthority               string `json:"goAuthority"`
}

const None = "none"

// Current returns the OV14 production retirement contract.  Archived fixtures may
// remain under migration/archive, but none of these legacy systems may remain a
// production authority or fallback after OV14.
func Current() Status {
    return Status{
        CppScannerRuntime:         None,
        PythonRuntime:             None,
        RoomDomainAuthority:       None,
        LegacyDestructiveExecutor: None,
        LegacyNavigation:          None,
        GoAuthority:               "product-default",
    }
}
