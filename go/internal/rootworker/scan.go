package rootworker

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/mikeyphw/dustui/go/internal/scan"
)

// ScanRequest is the privileged transport contract. Privilege changes process
// identity only: traversal, filtering, hard-link identity and size semantics are
// delegated to internal/scan, the same authority used by non-root scans.
type ScanRequest struct {
	ScanID         string   `json:"scanId"`
	RootPath       string   `json:"rootPath"`
	MaxDepth       int      `json:"maxDepth"`
	MaxEntries     int64    `json:"maxEntries"`
	Mode           string   `json:"mode"`
	MinSize        int64    `json:"minSize"`
	IncludeHidden  bool     `json:"includeHidden"`
	ApparentSize   bool     `json:"apparentSize"`
	SameFilesystem bool     `json:"sameFilesystem"`
	Excludes       []string `json:"excludes"`
}

func RunScanStream(ctx context.Context, r io.Reader, w io.Writer) error {
	var req ScanRequest
	if err := json.NewDecoder(io.LimitReader(r, 256*1024)).Decode(&req); err != nil {
		return fmt.Errorf("decode scan request: %w", err)
	}
	if req.ScanID == "" || !filepath.IsAbs(req.RootPath) || strings.ContainsRune(req.RootPath, '\x00') {
		return errors.New("invalid root scan request")
	}
	opts := scan.DefaultOptions()
	opts.Backend = scan.BackendIdentity{Name: "dustui-go-root", Version: "aud-fix-02", Privileged: true}
	opts.IncludeHidden = req.IncludeHidden
	opts.Exclusions = append([]string{}, req.Excludes...)
	opts.MaxDepth = req.MaxDepth
	if req.MaxEntries > 0 {
		if req.MaxEntries > int64(^uint(0)>>1) {
			opts.MaxEntries = int(^uint(0) >> 1)
		} else {
			opts.MaxEntries = int(req.MaxEntries)
		}
	}
	opts.MinSizeBytes = req.MinSize
	opts.EntryMode = req.Mode
	if req.ApparentSize {
		opts.SizeSemantics = scan.SizeLogical
	} else {
		opts.SizeSemantics = scan.SizeAllocated
	}
	if req.SameFilesystem {
		opts.FilesystemBoundaryPolicy = scan.BoundaryStayOnDevice
	} else {
		opts.FilesystemBoundaryPolicy = scan.BoundaryAllowCross
	}

	out := bufio.NewWriterSize(w, 256*1024)
	emit := func(v any, flush bool) error {
		b, err := json.Marshal(v)
		if err != nil {
			return err
		}
		if _, err := out.Write(append(b, '\n')); err != nil {
			return err
		}
		if flush {
			return out.Flush()
		}
		return nil
	}
	if err := emit(map[string]any{"type": "start", "backend": "dustui-go-root/aud-fix-02", "root": req.RootPath}, true); err != nil {
		return err
	}

	session, err := scan.ScanContext(ctx, req.RootPath, 1, opts)
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		_ = emit(map[string]any{"type": "cancelled", "scannedItems": session.ObservedEntryCount, "scannedBytes": scanSessionBytes(session, req.ApparentSize)}, true)
		return nil
	}
	if err != nil {
		_ = emit(map[string]any{"type": "error", "code": "GO_SCAN_FAILED", "phase": "traversal", "path": req.RootPath, "message": err.Error()}, true)
		return err
	}

	for _, problem := range session.Problems {
		if problem.Kind == scan.ProblemTruncated {
			continue
		}
		if err := emit(map[string]any{"type": "warning", "code": string(problem.Kind), "phase": "traversal", "path": problem.Path, "message": problem.Message}, false); err != nil {
			return err
		}
	}

	var emittedFiles, emittedDirs uint64
	for i, entry := range session.Entries {
		if err := ctx.Err(); err != nil {
			_ = emit(map[string]any{"type": "cancelled", "scannedItems": i, "scannedBytes": scanSessionBytes(session, req.ApparentSize)}, true)
			return nil
		}
		absPath := filepath.Join(session.CanonicalRoot, filepath.FromSlash(entry.Path))
		absParent := filepath.Join(session.CanonicalRoot, filepath.FromSlash(entry.ParentPath))
		if entry.ParentPath == "" {
			absParent = session.CanonicalRoot
		}
		size := entry.AllocatedSizeBytes
		if req.ApparentSize {
			size = entry.LogicalSizeBytes
		}
		if err := emit(map[string]any{
			"type": "entry", "path": absPath, "parentPath": absParent, "name": entry.Name,
			"sizeBytes": size, "allocatedBytes": entry.AllocatedSizeBytes, "directory": entry.IsDirectory,
			"depth": entry.Depth, "modifiedAt": entry.ModifiedUnixMs / 1000, "fileCount": entry.FileCount,
		}, false); err != nil {
			return err
		}
		if entry.IsDirectory {
			emittedDirs++
		} else {
			emittedFiles++
		}
		if (i+1)%128 == 0 {
			if err := emit(map[string]any{"type": "progress", "scannedItems": i + 1, "scannedBytes": scanSessionBytes(session, req.ApparentSize), "currentPath": absPath}, true); err != nil {
				return err
			}
		}
	}
	total := scanSessionBytes(session, req.ApparentSize)
	return emit(map[string]any{
		"type": "complete", "totalBytes": total, "scannedItems": session.ObservedEntryCount,
		"scannedBytes": total, "warnings": len(session.Problems), "emittedEntries": len(session.Entries),
		"emittedFiles": emittedFiles, "emittedDirectories": emittedDirs, "truncated": session.Truncated,
		"depthLimitedDirectories": 0, "skippedFilesystemEntries": countBoundaryProblems(session), "rootOpened": true,
	}, true)
}

func scanSessionBytes(session scan.Session, apparent bool) int64 {
	var total int64
	for _, e := range session.Entries {
		if e.IsDirectory {
			continue
		}
		if apparent {
			total += e.LogicalSizeBytes
		} else {
			total += e.AllocatedSizeBytes
		}
	}
	return total
}

func countBoundaryProblems(session scan.Session) int {
	n := 0
	for _, p := range session.Problems {
		if p.Kind == scan.ProblemBoundarySkipped {
			n++
		}
	}
	return n
}
