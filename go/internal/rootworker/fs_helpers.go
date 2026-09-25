package rootworker

import (
	"os"
	"syscall"
)

// allocatedSize and statDevice are shared low-level filesystem facts used by
// privileged cleanup verification. Scanner traversal itself lives in
// internal/scan so root and non-root semantics cannot diverge.
func allocatedSize(info os.FileInfo) uint64 {
	if st, ok := info.Sys().(*syscall.Stat_t); ok && st.Blocks > 0 {
		return uint64(st.Blocks) * 512
	}
	if info.Size() > 0 {
		return uint64(info.Size())
	}
	return 0
}

func statDevice(info os.FileInfo) uint64 {
	if st, ok := info.Sys().(*syscall.Stat_t); ok {
		return uint64(st.Dev)
	}
	return 0
}
