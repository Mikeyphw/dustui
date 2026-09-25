package state

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

func TestAUD_FIX10OverviewKeepsLogicalAndAllocatedBytesDistinct(t *testing.T) {
	acc := newBoundedProductAccumulator()
	acc.add(productEntry{AndroidScanEntry: AndroidScanEntry{
		Path: "/storage/emulated/0/a.bin", Name: "a.bin",
		SizeBytes: 1000, AllocatedBytes: 4096,
	}}, &packageevidence.NormalizedSnapshot{}, false, map[string]bool{}, 1, "scan-a", nil)
	acc.add(productEntry{AndroidScanEntry: AndroidScanEntry{
		Path: "/storage/emulated/0/b.bin", Name: "b.bin",
		SizeBytes: 3000, AllocatedBytes: 4096,
	}}, &packageevidence.NormalizedSnapshot{}, false, map[string]bool{}, 1, "scan-a", nil)
	acc.add(productEntry{AndroidScanEntry: AndroidScanEntry{
		Path: "/storage/emulated/0/dir", Name: "dir", IsDirectory: true,
		SizeBytes: 9999, AllocatedBytes: 9999,
	}}, &packageevidence.NormalizedSnapshot{}, false, map[string]bool{}, 1, "scan-a", nil)

	if acc.LogicalBytes != 4000 {
		t.Fatalf("logical bytes = %d, want 4000", acc.LogicalBytes)
	}
	if acc.AllocatedBytes != 8192 {
		t.Fatalf("allocated bytes = %d, want 8192", acc.AllocatedBytes)
	}
	if acc.FileCount != 2 {
		t.Fatalf("file count = %d, want 2", acc.FileCount)
	}
}
