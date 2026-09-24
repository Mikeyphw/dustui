package retirement

import "testing"

func TestOV14CurrentReportsObservedBlockersInsteadOfDesiredState(t *testing.T) {
	status := Current()
	if status.CppScannerRuntime == None || status.RoomDomainAuthority == None || status.LegacyDestructiveExecutor == None {
		t.Fatalf("current status must not claim retired authorities before source cutover: %+v", status)
	}
	if status.AndroidGoRuntimeBinding == "production" || status.GoSQLitePersistence == "go-owned-sqlite" {
		t.Fatalf("current status must not claim unimplemented Android Go runtime/SQLite cutover: %+v", status)
	}
	if status.GoAuthority == "product-default-qualified" {
		t.Fatalf("current Go authority must remain unqualified while Gate D is reopened: %+v", status)
	}
}

func TestOV14DesiredRetirementTargetRemainsExplicit(t *testing.T) {
	status := Desired()
	if status.CppScannerRuntime != None || status.PythonRuntime != None || status.RoomDomainAuthority != None || status.LegacyDestructiveExecutor != None || status.LegacyNavigation != None {
		t.Fatalf("desired legacy retirement target changed: %+v", status)
	}
	if status.GoAuthority != "product-default-qualified" || status.AndroidGoRuntimeBinding != "production" || status.GoSQLitePersistence != "go-owned-sqlite" || status.GenericReleaseTruth != "devtool-only" {
		t.Fatalf("desired Go/Devtool authority target changed: %+v", status)
	}
}
