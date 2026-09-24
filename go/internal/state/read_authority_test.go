package state

import "testing"

func TestOV05AuthorityMatrixCutsNewRoomScanWrites(t *testing.T) {
	if err := ValidateOV05AuthorityMatrix(); err != nil {
		t.Fatal(err)
	}
	if !CanWriteNewScan(SourceGoSQLite) {
		t.Fatalf("Go SQLite must write new scan state after OV05")
	}
	for _, source := range []AuthoritySource{SourceRoomV3, SourceLegacyCpp, SourceKotlin} {
		if CanWriteNewScan(source) {
			t.Fatalf("%s must not write new scan state after OV05", source)
		}
	}
}
