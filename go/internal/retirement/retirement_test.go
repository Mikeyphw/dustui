package retirement

import "testing"

func TestOV14LegacyRuntimeAuthorityIsRetired(t *testing.T) {
    status := Current()
    if status.CppScannerRuntime != None {
        t.Fatalf("cpp scanner runtime = %q, want none", status.CppScannerRuntime)
    }
    if status.PythonRuntime != None {
        t.Fatalf("python runtime = %q, want none", status.PythonRuntime)
    }
    if status.RoomDomainAuthority != None {
        t.Fatalf("room domain authority = %q, want none", status.RoomDomainAuthority)
    }
    if status.LegacyDestructiveExecutor != None {
        t.Fatalf("legacy destructive executor = %q, want none", status.LegacyDestructiveExecutor)
    }
    if status.LegacyNavigation != None {
        t.Fatalf("legacy navigation = %q, want none", status.LegacyNavigation)
    }
    if status.GoAuthority != "product-default" {
        t.Fatalf("go authority = %q, want product-default", status.GoAuthority)
    }
}
