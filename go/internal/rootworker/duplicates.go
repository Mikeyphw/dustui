package rootworker

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
)

const (
	maxDuplicateInputBytes = 2 * 1024 * 1024
	maxDuplicatePaths      = 4096
	maxPartialHashBytes    = 1024 * 1024
	defaultPartialBytes    = 64 * 1024
)

type duplicateVerifyRequest struct {
	OperationID  string   `json:"operationId"`
	ScanID       string   `json:"scanId"`
	SizeBytes    int64    `json:"sizeBytes"`
	PartialBytes int      `json:"partialBytes"`
	ScanRoot     string   `json:"scanRoot"`
	Paths        []string `json:"paths"`
}

type duplicateProbe struct {
	Path        string
	DeviceID    uint64
	Inode       uint64
	LinkCount   uint64
	ModifiedAt  int64
	PartialHash string
	BlockReason string
}

type hashResult struct {
	Hash      string
	BytesRead int64
}

func VerifyDuplicates(ctx context.Context, r io.Reader, w io.Writer) error {
	var req duplicateVerifyRequest
	dec := json.NewDecoder(io.LimitReader(r, maxDuplicateInputBytes+1))
	if err := dec.Decode(&req); err != nil {
		return encodeDuplicateFailure(w, req.OperationID, false, err)
	}
	if req.OperationID == "" {
		req.OperationID = req.ScanID
	}
	if req.ScanID == "" || req.OperationID == "" || req.SizeBytes <= 0 || len(req.Paths) < 2 || len(req.Paths) > maxDuplicatePaths {
		return encodeDuplicateFailure(w, req.OperationID, false, errors.New("invalid_duplicate_verification_request"))
	}
	root, err := canonicalAbsolute(req.ScanRoot)
	if err != nil {
		return encodeDuplicateFailure(w, req.OperationID, false, err)
	}
	partialBytes := req.PartialBytes
	if partialBytes <= 0 {
		partialBytes = defaultPartialBytes
	}
	if partialBytes > maxPartialHashBytes {
		partialBytes = maxPartialHashBytes
	}
	if partialBytes < 4*1024 {
		partialBytes = 4 * 1024
	}

	probes := make([]duplicateProbe, 0, len(req.Paths))
	skipped := make([]map[string]any, 0)
	var bytesHashed int64
	for _, raw := range req.Paths {
		if err := ctx.Err(); err != nil {
			return encodeDuplicateCancelled(w, req.OperationID)
		}
		p, err := canonicalAbsolute(raw)
		if err != nil {
			skipped = append(skipped, map[string]any{"path": raw, "reason": err.Error()})
			continue
		}
		info, err := os.Lstat(p)
		if err != nil || !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
			reason := "Path is not a regular file"
			if err != nil {
				reason = err.Error()
			}
			skipped = append(skipped, map[string]any{"path": raw, "reason": reason})
			continue
		}
		st, ok := info.Sys().(*syscall.Stat_t)
		if !ok {
			skipped = append(skipped, map[string]any{"path": raw, "reason": "Filesystem identity unavailable"})
			continue
		}
		if info.Size() != req.SizeBytes {
			skipped = append(skipped, map[string]any{"path": raw, "reason": "File size changed since scan"})
			continue
		}
		partial, err := hashEdges(ctx, p, req.SizeBytes, partialBytes)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return encodeDuplicateCancelled(w, req.OperationID)
			}
			skipped = append(skipped, map[string]any{"path": raw, "reason": err.Error()})
			continue
		}
		bytesHashed += partial.BytesRead
		probes = append(probes, duplicateProbe{
			Path: p, DeviceID: uint64(st.Dev), Inode: uint64(st.Ino), LinkCount: uint64(st.Nlink),
			ModifiedAt: info.ModTime().Unix(), PartialHash: partial.Hash, BlockReason: cleanupPathBlockReason(p, root),
		})
	}

	byPartial := map[string][]duplicateProbe{}
	for _, p := range probes {
		byPartial[p.PartialHash] = append(byPartial[p.PartialHash], p)
	}
	partialGroups := 0
	byFull := map[string][]duplicateProbe{}
	for _, group := range byPartial {
		if len(group) < 2 {
			continue
		}
		partialGroups++
		for _, probe := range group {
			if err := ctx.Err(); err != nil {
				return encodeDuplicateCancelled(w, req.OperationID)
			}
			full, err := hashWhole(ctx, probe.Path)
			if err != nil {
				if errors.Is(err, context.Canceled) {
					return encodeDuplicateCancelled(w, req.OperationID)
				}
				skipped = append(skipped, map[string]any{"path": probe.Path, "reason": err.Error()})
				continue
			}
			bytesHashed += full.BytesRead
			info, err := os.Lstat(probe.Path)
			st, ok := func() (*syscall.Stat_t, bool) {
				if err != nil {
					return nil, false
				}
				v, ok := info.Sys().(*syscall.Stat_t)
				return v, ok
			}()
			if !ok || uint64(st.Dev) != probe.DeviceID || uint64(st.Ino) != probe.Inode || info.Size() != req.SizeBytes || info.ModTime().Unix() != probe.ModifiedAt {
				skipped = append(skipped, map[string]any{"path": probe.Path, "reason": "File changed while hashing"})
				continue
			}
			byFull[full.Hash] = append(byFull[full.Hash], probe)
		}
	}

	hashes := make([]string, 0, len(byFull))
	for h, group := range byFull {
		if len(group) > 1 {
			hashes = append(hashes, h)
		}
	}
	sort.Strings(hashes)
	verified := make([]map[string]any, 0, len(hashes))
	for _, hash := range hashes {
		matches := byFull[hash]
		physical := map[string][]duplicateProbe{}
		for _, p := range matches {
			key := fmt.Sprintf("%d:%d", p.DeviceID, p.Inode)
			physical[key] = append(physical[key], p)
		}
		if len(physical) < 2 {
			continue
		}
		keys := make([]string, 0, len(physical))
		for key := range physical {
			keys = append(keys, key)
		}
		sort.Strings(keys)
		hardLinkedCopies, eligibleCopies := 0, 0
		records := make([]map[string]any, 0, len(matches))
		for _, key := range keys {
			owned := physical[key]
			sort.Slice(owned, func(i, j int) bool { return owned[i].Path < owned[j].Path })
			hardLinked := len(owned) > 1
			for _, p := range owned {
				if p.LinkCount > 1 {
					hardLinked = true
				}
			}
			if hardLinked {
				hardLinkedCopies++
			}
			eligible := len(owned) == 1 && owned[0].LinkCount == 1 && owned[0].BlockReason == ""
			if eligible {
				eligibleCopies++
			}
			for _, p := range owned {
				records = append(records, map[string]any{
					"path": p.Path, "deviceId": p.DeviceID, "inode": p.Inode, "linkCount": p.LinkCount,
					"modifiedAt": p.ModifiedAt, "copyKey": key, "hardLinked": hardLinked,
					"autoDeleteEligible": eligible, "deletionBlockReason": nullableString(p.BlockReason),
				})
			}
		}
		retainedByPolicy := len(physical) - eligibleCopies
		copiesToDelete := eligibleCopies
		if retainedByPolicy == 0 {
			copiesToDelete--
		}
		if copiesToDelete < 0 {
			copiesToDelete = 0
		}
		verified = append(verified, map[string]any{
			"sha256": hash, "partialHash": matches[0].PartialHash, "sizeBytes": req.SizeBytes, "paths": records,
			"physicalCopyCount": len(physical), "hardLinkedCopyCount": hardLinkedCopies,
			"reclaimableBytes":     req.SizeBytes * int64(len(physical)-1),
			"safeReclaimableBytes": req.SizeBytes * int64(copiesToDelete),
		})
	}
	return json.NewEncoder(w).Encode(map[string]any{
		"ok": true, "operationId": req.OperationID, "cancelled": false, "checkedFiles": len(probes),
		"skipped": skipped, "partialGroups": partialGroups, "bytesHashed": bytesHashed, "groups": verified,
	})
}

func hashEdges(ctx context.Context, path string, size int64, n int) (hashResult, error) {
	f, err := os.Open(path)
	if err != nil {
		return hashResult{}, err
	}
	defer f.Close()
	h := sha256.New()
	_, _ = io.WriteString(h, fmt.Sprint(size))
	var total int64
	readAt := func(off int64, want int) error {
		if want <= 0 {
			return nil
		}
		buf := make([]byte, want)
		read, err := f.ReadAt(buf, off)
		if err != nil && !errors.Is(err, io.EOF) {
			return err
		}
		if err := ctx.Err(); err != nil {
			return err
		}
		total += int64(read)
		_, _ = h.Write(buf[:read])
		return nil
	}
	first := int64(n)
	if size < first {
		first = size
	}
	if err := readAt(0, int(first)); err != nil {
		return hashResult{}, err
	}
	if size > first {
		last := int64(n)
		if size-first < last {
			last = size - first
		}
		if err := readAt(size-last, int(last)); err != nil {
			return hashResult{}, err
		}
	}
	return hashResult{Hash: hex.EncodeToString(h.Sum(nil)), BytesRead: total}, nil
}

func hashWhole(ctx context.Context, path string) (hashResult, error) {
	f, err := os.Open(path)
	if err != nil {
		return hashResult{}, err
	}
	defer f.Close()
	h := sha256.New()
	buf := make([]byte, 1024*1024)
	var total int64
	for {
		if err := ctx.Err(); err != nil {
			return hashResult{}, err
		}
		n, err := f.Read(buf)
		if n > 0 {
			_, _ = h.Write(buf[:n])
			total += int64(n)
		}
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return hashResult{}, err
		}
	}
	return hashResult{Hash: hex.EncodeToString(h.Sum(nil)), BytesRead: total}, nil
}

func nullableString(v string) any {
	if strings.TrimSpace(v) == "" {
		return nil
	}
	return v
}
func encodeDuplicateCancelled(w io.Writer, op string) error {
	return json.NewEncoder(w).Encode(map[string]any{"ok": false, "operationId": op, "cancelled": true, "groups": []any{}, "skipped": []any{}, "message": "Duplicate verification cancelled"})
}
func encodeDuplicateFailure(w io.Writer, op string, cancelled bool, err error) error {
	return json.NewEncoder(w).Encode(map[string]any{"ok": false, "operationId": op, "cancelled": cancelled, "groups": []any{}, "skipped": []any{}, "message": err.Error()})
}

var _ = filepath.Separator
