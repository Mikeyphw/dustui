package rootworker

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"
)

const progressInterval = 128

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

type scanTotals struct {
	bytes uint64
	files uint64
}

type scanState struct {
	ctx                      context.Context
	out                      *bufio.Writer
	req                      ScanRequest
	rootDev                  uint64
	seen                     map[[2]uint64]struct{}
	scannedItems             uint64
	scannedBytes             uint64
	emittedEntries           uint64
	emittedFiles             uint64
	emittedDirectories       uint64
	warnings                 uint64
	depthLimitedDirectories  uint64
	skippedFilesystemEntries uint64
	truncated                bool
	rootOpened               bool
	fatal                    bool
}

func RunScanStream(ctx context.Context, r io.Reader, w io.Writer) error {
	var req ScanRequest
	if err := json.NewDecoder(io.LimitReader(r, 256*1024)).Decode(&req); err != nil {
		return fmt.Errorf("decode scan request: %w", err)
	}
	if req.ScanID == "" || !filepath.IsAbs(req.RootPath) || strings.ContainsRune(req.RootPath, '\x00') {
		return errors.New("invalid root scan request")
	}
	if req.MaxEntries <= 0 {
		req.MaxEntries = 1<<63 - 1
	}
	if req.MinSize < 0 {
		req.MinSize = 0
	}
	info, err := os.Lstat(req.RootPath)
	if err != nil {
		emitJSON(w, map[string]any{"type": "error", "code": "ROOT_LSTAT_FAILED", "phase": "root-preflight", "path": req.RootPath, "message": err.Error()})
		return err
	}
	if info.Mode()&os.ModeSymlink != 0 {
		emitJSON(w, map[string]any{"type": "error", "code": "ROOT_SYMLINK", "phase": "root-preflight", "path": req.RootPath, "message": "Scan root is a symbolic link"})
		return errors.New("scan root is a symbolic link")
	}
	if !info.IsDir() {
		emitJSON(w, map[string]any{"type": "error", "code": "ROOT_NOT_DIRECTORY", "phase": "root-preflight", "path": req.RootPath, "message": "Scan root is not a directory"})
		return errors.New("scan root is not a directory")
	}
	rootDev := statDevice(info)
	out := bufio.NewWriterSize(w, 256*1024)
	s := &scanState{ctx: ctx, out: out, req: req, rootDev: rootDev, seen: map[[2]uint64]struct{}{}}
	if err := s.emit(map[string]any{"type": "start", "backend": "dustui-go-root-v1", "root": req.RootPath}, true); err != nil {
		return err
	}
	totals := s.scanPath(req.RootPath, 0)
	if ctx.Err() != nil {
		_ = s.emit(map[string]any{"type": "cancelled", "scannedItems": s.scannedItems, "scannedBytes": s.scannedBytes}, true)
		return nil
	}
	if s.fatal {
		_ = out.Flush()
		return errors.New("fatal root scan failure")
	}
	if err := s.emit(map[string]any{
		"type": "complete", "totalBytes": totals.bytes, "scannedItems": s.scannedItems,
		"scannedBytes": s.scannedBytes, "warnings": s.warnings, "emittedEntries": s.emittedEntries,
		"emittedFiles": s.emittedFiles, "emittedDirectories": s.emittedDirectories, "truncated": s.truncated,
		"depthLimitedDirectories": s.depthLimitedDirectories, "skippedFilesystemEntries": s.skippedFilesystemEntries,
		"rootOpened": s.rootOpened,
	}, true); err != nil {
		return err
	}
	return nil
}

func (s *scanState) scanPath(path string, depth int) scanTotals {
	if s.ctx.Err() != nil {
		return scanTotals{}
	}
	info, err := os.Lstat(path)
	if err != nil {
		s.warning("LSTAT_FAILED", "traversal", path, err)
		return scanTotals{}
	}
	dev := statDevice(info)
	if s.req.SameFilesystem && s.rootDev != 0 && dev != 0 && dev != s.rootDev {
		s.skippedFilesystemEntries++
		return scanTotals{}
	}
	s.scannedItems++
	directory := info.IsDir()
	total := scanTotals{bytes: sizeFor(info, s.req.ApparentSize)}
	if !directory {
		total.files = 1
	}
	if !directory {
		if st, ok := info.Sys().(*syscall.Stat_t); ok && st.Nlink > 1 {
			key := [2]uint64{uint64(st.Dev), uint64(st.Ino)}
			if _, exists := s.seen[key]; exists {
				total = scanTotals{}
			} else {
				s.seen[key] = struct{}{}
			}
		}
	}
	mayDescend := directory && (s.req.MaxDepth < 0 || depth < s.req.MaxDepth)
	if directory && !mayDescend {
		s.depthLimitedDirectories++
	}
	if mayDescend {
		entries, err := os.ReadDir(path)
		if err != nil {
			if depth == 0 {
				s.fatal = true
				_ = s.emit(map[string]any{"type": "error", "code": "ROOT_OPEN_FAILED", "phase": "root-open", "path": path, "message": err.Error()}, true)
			} else {
				s.warning("DIRECTORY_OPEN_FAILED", "traversal", path, err)
			}
		} else {
			if depth == 0 {
				s.rootOpened = true
			}
			for _, entry := range entries {
				if s.ctx.Err() != nil {
					break
				}
				name := entry.Name()
				if !s.req.IncludeHidden && strings.HasPrefix(name, ".") {
					continue
				}
				child := filepath.Join(path, name)
				if s.excluded(child, name) {
					continue
				}
				childTotals := s.scanPath(child, depth+1)
				total.bytes += childTotals.bytes
				total.files += childTotals.files
			}
		}
	}
	if !directory {
		s.scannedBytes += total.bytes
	}
	if depth != 0 && int64(total.bytes) >= s.req.MinSize && s.modeAllows(directory) {
		if s.emittedEntries >= uint64(s.req.MaxEntries) {
			s.truncated = true
		} else {
			parent := filepath.Dir(path)
			if err := s.emit(map[string]any{
				"type": "entry", "path": path, "parentPath": parent, "name": filepath.Base(path),
				"sizeBytes": total.bytes, "allocatedBytes": allocatedSize(info), "directory": directory,
				"depth": depth, "modifiedAt": info.ModTime().Unix(), "fileCount": total.files,
			}, false); err == nil {
				s.emittedEntries++
				if directory {
					s.emittedDirectories++
				} else {
					s.emittedFiles++
				}
			}
		}
	}
	if s.scannedItems%progressInterval == 0 {
		_ = s.emit(map[string]any{"type": "progress", "scannedItems": s.scannedItems, "scannedBytes": s.scannedBytes, "currentPath": path}, true)
	}
	return total
}

func (s *scanState) excluded(path, name string) bool {
	for _, pattern := range s.req.Excludes {
		if pattern == "" {
			continue
		}
		if ok, _ := filepath.Match(pattern, name); ok {
			return true
		}
		if ok, _ := filepath.Match(pattern, path); ok {
			return true
		}
	}
	return false
}
func (s *scanState) modeAllows(directory bool) bool {
	switch strings.ToUpper(s.req.Mode) {
	case "DIRECTORIES":
		return directory
	case "FILES":
		return !directory
	default:
		return true
	}
}
func (s *scanState) warning(code, phase, path string, err error) {
	s.warnings++
	_ = s.emit(map[string]any{"type": "warning", "code": code, "phase": phase, "path": path, "message": err.Error()}, false)
}
func (s *scanState) emit(v any, flush bool) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if _, err = s.out.Write(append(b, '\n')); err != nil {
		return err
	}
	if flush {
		return s.out.Flush()
	}
	return nil
}
func emitJSON(w io.Writer, v any) error {
	b, e := json.Marshal(v)
	if e != nil {
		return e
	}
	_, e = w.Write(append(b, '\n'))
	return e
}
func sizeFor(info os.FileInfo, apparent bool) uint64 {
	if apparent {
		if info.Size() > 0 {
			return uint64(info.Size())
		}
		return 0
	}
	return allocatedSize(info)
}
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
