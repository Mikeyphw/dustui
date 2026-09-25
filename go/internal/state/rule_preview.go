package state

import (
	"context"
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

type CleanupRuleConditionsInput struct {
	IncludePaths      []string `json:"includePaths"`
	ExcludePaths      []string `json:"excludePaths"`
	Extensions        []string `json:"extensions"`
	MinimumBytes      int64    `json:"minimumBytes"`
	MaximumBytes      *int64   `json:"maximumBytes"`
	OlderThanDays     *int     `json:"olderThanDays"`
	Category          *string  `json:"category"`
	PackageName       *string  `json:"packageName"`
	RegularExpression *string  `json:"regularExpression"`
	MaximumDepth      *int     `json:"maximumDepth"`
	EntryType         string   `json:"entryType"`
	DuplicateOnly     bool     `json:"duplicateOnly"`
	ResidueConfidence string   `json:"residueConfidence"`
}

type CleanupRuleInput struct {
	ID            string                     `json:"id"`
	Name          string                     `json:"name"`
	Enabled       bool                       `json:"enabled"`
	Imported      bool                       `json:"imported"`
	ReviewedAt    *int64                     `json:"reviewedAt"`
	SchemaVersion int                        `json:"schemaVersion"`
	Conditions    CleanupRuleConditionsInput `json:"conditions"`
}

const cleanupRulePreviewLimit = 500

type cleanupRuleCompiled struct {
	rule       CleanupRuleInput
	includes   []string
	excludes   []string
	extensions map[string]bool
	re         *regexp.Regexp
}

func (s *SQLiteStore) CleanupRulePreview(ctx context.Context, scanID string, rules []CleanupRuleInput, includeDisabled bool) (map[string]any, error) {
	session, err := s.AndroidScanSessionByID(ctx, scanID)
	if err != nil || session == nil {
		return nil, fmt.Errorf("scan_not_found:%s", scanID)
	}
	dup, err := s.verifiedDuplicateDeletePaths(ctx, scanID, session.Generation)
	if err != nil {
		return nil, err
	}
	residue, err := s.residueConfidenceByPath(ctx)
	if err != nil {
		return nil, err
	}
	now := time.Now().Unix()
	matches := []map[string]any{}
	for _, rule := range rules {
		if !includeDisabled && !rule.Enabled {
			continue
		}
		c, err := compileCleanupRule(rule)
		if err != nil {
			continue
		}
		matched := make([]AndroidScanEntry, 0, cleanupRulePreviewLimit)
		blocked := []map[string]any{}
		var total int64
		var matchedCount int
		rows, err := s.db.QueryContext(ctx, "SELECT "+androidScanEntryColumns+" FROM scan_entries WHERE scan_id=? ORDER BY logical_size_bytes DESC,path COLLATE NOCASE", scanID)
		if err != nil {
			return nil, err
		}
		for rows.Next() {
			if err := ctx.Err(); err != nil {
				rows.Close()
				return nil, err
			}
			e, err := scanAndroidEntry(rows)
			if err != nil {
				rows.Close()
				return nil, err
			}
			if !cleanupRuleMatches(e, session.RootPath, c, dup, residue, now) {
				continue
			}
			matchedCount++
			total += e.SizeBytes
			if len(matched) < cleanupRulePreviewLimit {
				matched = append(matched, e)
			}
		}
		if err := rows.Err(); err != nil {
			rows.Close()
			return nil, err
		}
		rows.Close()
		matched = collapseRuleEntries(matched)
		for _, e := range matched {
			if reason := cleanupPreviewBlockReason(e.Path, session.RootPath); reason != "" {
				blocked = append(blocked, map[string]any{"path": e.Path, "reason": reason})
			}
		}
		if matchedCount > cleanupRulePreviewLimit {
			blocked = append(blocked, map[string]any{"path": "", "reason": fmt.Sprintf("Preview is bounded to %d entries; refine this rule before review", cleanupRulePreviewLimit)})
		}
		matches = append(matches, map[string]any{"ruleId": rule.ID, "ruleName": rule.Name, "entries": matched, "exactBytes": total, "matchedCount": matchedCount, "truncated": matchedCount > cleanupRulePreviewLimit, "blocked": blocked})
	}
	return map[string]any{"scanId": scanID, "generatedAt": time.Now().UnixMilli(), "matches": matches, "scanGeneration": session.Generation}, nil
}

func compileCleanupRule(rule CleanupRuleInput) (cleanupRuleCompiled, error) {
	if strings.TrimSpace(rule.Name) == "" || rule.SchemaVersion != 2 {
		return cleanupRuleCompiled{}, fmt.Errorf("invalid_rule")
	}
	c := rule.Conditions
	if c.MinimumBytes < 0 || (c.MaximumBytes != nil && *c.MaximumBytes < c.MinimumBytes) {
		return cleanupRuleCompiled{}, fmt.Errorf("invalid_size_range")
	}
	if c.OlderThanDays != nil && *c.OlderThanDays < 0 {
		return cleanupRuleCompiled{}, fmt.Errorf("invalid_age")
	}
	if c.MaximumDepth != nil && *c.MaximumDepth < 0 {
		return cleanupRuleCompiled{}, fmt.Errorf("invalid_depth")
	}
	if rule.Imported && rule.ReviewedAt == nil && rule.Enabled {
		return cleanupRuleCompiled{}, fmt.Errorf("unreviewed_import")
	}
	out := cleanupRuleCompiled{rule: rule, extensions: map[string]bool{}}
	for _, raw := range c.IncludePaths {
		p, ok := normalizedAbsolute(raw)
		if !ok {
			return out, fmt.Errorf("invalid_include")
		}
		out.includes = append(out.includes, p)
	}
	for _, raw := range c.ExcludePaths {
		p, ok := normalizedAbsolute(raw)
		if !ok {
			return out, fmt.Errorf("invalid_exclude")
		}
		out.excludes = append(out.excludes, p)
	}
	for _, ext := range c.Extensions {
		ext = strings.ToLower(strings.TrimPrefix(strings.TrimSpace(ext), "."))
		if ext != "" {
			out.extensions[ext] = true
		}
	}
	if c.RegularExpression != nil && strings.TrimSpace(*c.RegularExpression) != "" {
		if len(*c.RegularExpression) > 192 {
			return out, fmt.Errorf("regex_too_long")
		}
		re, err := regexp.Compile(*c.RegularExpression)
		if err != nil {
			return out, err
		}
		out.re = re
	}
	return out, nil
}

func cleanupRuleMatches(e AndroidScanEntry, scanRoot string, c cleanupRuleCompiled, duplicate map[string]bool, residue map[string]int, now int64) bool {
	cond := c.rule.Conditions
	p, ok := normalizedAbsolute(e.Path)
	if !ok {
		return false
	}
	switch cond.EntryType {
	case "FILES":
		if e.IsDirectory {
			return false
		}
	case "DIRECTORIES":
		if !e.IsDirectory {
			return false
		}
	}
	if len(c.includes) > 0 {
		hit := false
		for _, r := range c.includes {
			if sameOrDescendant(p, r) {
				hit = true
				break
			}
		}
		if !hit {
			return false
		}
	}
	for _, r := range c.excludes {
		if pathIntersects(p, r) {
			return false
		}
	}
	if len(c.extensions) > 0 {
		ext := strings.ToLower(strings.TrimPrefix(filepath.Ext(e.Name), "."))
		if !c.extensions[ext] {
			return false
		}
	}
	if e.SizeBytes < cond.MinimumBytes {
		return false
	}
	if cond.MaximumBytes != nil && e.SizeBytes > *cond.MaximumBytes {
		return false
	}
	if cond.OlderThanDays != nil {
		cutoff := now - int64(*cond.OlderThanDays)*86400
		m := e.ModifiedAt
		if m > 100000000000 {
			m /= 1000
		}
		if m <= 0 || m > cutoff {
			return false
		}
	}
	if cond.Category != nil && fileCategory(e.Name) != *cond.Category {
		return false
	}
	if cond.PackageName != nil {
		pkg, _ := packageIdentityFromPath(p)
		if pkg != *cond.PackageName {
			return false
		}
	}
	if cond.MaximumDepth != nil && ruleRelativeDepth(p, scanRoot, c.includes) > *cond.MaximumDepth {
		return false
	}
	if c.re != nil && !c.re.MatchString(limitString(p, 4096)) {
		return false
	}
	if cond.DuplicateOnly && !duplicate[p] {
		return false
	}
	need := residueRank(cond.ResidueConfidence)
	if need > 0 {
		actual := 0
		for root, rank := range residue {
			if sameOrDescendant(p, root) && rank > actual {
				actual = rank
			}
		}
		if actual < need {
			return false
		}
	}
	return true
}

func (s *SQLiteStore) verifiedDuplicateDeletePaths(ctx context.Context, scanID string, generation int64) (map[string]bool, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT c.path FROM cleanup_duplicate_candidates c JOIN cleanup_duplicate_groups g ON g.id=c.group_id WHERE g.scan_id=? AND g.scan_generation=? AND c.keep=0 AND c.verification_state='verified'`, scanID, generation)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]bool{}
	for rows.Next() {
		var p string
		if err := rows.Scan(&p); err != nil {
			return nil, err
		}
		out[p] = true
	}
	return out, rows.Err()
}
func (s *SQLiteStore) residueConfidenceByPath(ctx context.Context) (map[string]int, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT path,confidence FROM cleanup_residue_evidence`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]int{}
	for rows.Next() {
		var p, c string
		if err := rows.Scan(&p, &c); err != nil {
			return nil, err
		}
		out[p] = residueRank(c)
	}
	return out, rows.Err()
}
func residueRank(v string) int {
	switch strings.ToUpper(v) {
	case "HIGH", "CANDIDATE":
		return 3
	case "PROBABLE":
		return 2
	case "UNCERTAIN", "AMBIGUOUS":
		return 1
	}
	return 0
}
func normalizedAbsolute(raw string) (string, bool) {
	if raw == "" || !strings.HasPrefix(raw, "/") || strings.ContainsRune(raw, '\x00') {
		return "", false
	}
	p := filepath.Clean(raw)
	return p, strings.HasPrefix(p, "/")
}
func sameOrDescendant(p, r string) bool {
	p = filepath.Clean(p)
	r = filepath.Clean(r)
	return p == r || strings.HasPrefix(p, strings.TrimRight(r, "/")+"/")
}
func pathIntersects(a, b string) bool { return sameOrDescendant(a, b) || sameOrDescendant(b, a) }
func ruleRelativeDepth(path, scanRoot string, includes []string) int {
	base := filepath.Clean(scanRoot)
	for _, r := range includes {
		if sameOrDescendant(path, r) && len(r) > len(base) {
			base = r
		}
	}
	rel := strings.Trim(strings.TrimPrefix(filepath.Clean(path), strings.TrimRight(base, "/")), "/")
	if rel == "" {
		return 0
	}
	return len(strings.Split(rel, "/"))
}
func cleanupPreviewBlockReason(path, root string) string {
	p, ok := normalizedAbsolute(path)
	if !ok {
		return "Path is not absolute and normalized"
	}
	r, ok := normalizedAbsolute(root)
	if !ok {
		return "Scan root is invalid"
	}
	if !sameOrDescendant(p, r) {
		return "Path is outside the reviewed scan root"
	}
	if p == "/" || p == "/data" || p == "/system" || p == "/vendor" || p == "/product" || p == "/metadata" || p == "/data/adb" {
		return "Protected system/root boundary"
	}
	return ""
}
func collapseRuleEntries(in []AndroidScanEntry) []AndroidScanEntry {
	out := []AndroidScanEntry{}
	for _, e := range in {
		covered := false
		for _, x := range out {
			if sameOrDescendant(e.Path, x.Path) {
				covered = true
				break
			}
		}
		if !covered {
			out = append(out, e)
		}
	}
	return out
}
func limitString(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}
