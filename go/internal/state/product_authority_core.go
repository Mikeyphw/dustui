package state

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"sort"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

// ReplacePackageEvidence makes Go SQLite the durable package/storage authority.
// Android supplies platform observations; normalization and evidence identity are Go-owned.
func (s *SQLiteStore) ReplacePackageEvidence(ctx context.Context, snapshot packageevidence.NormalizedSnapshot, collector string, degradation []string) error {
	if snapshot.SnapshotID == "" || snapshot.Generation <= 0 || snapshot.EvidenceHash == "" {
		return errors.New("package_snapshot_identity_required")
	}
	if collector == "" {
		collector = "android-platform-adapter"
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	reasons, _ := json.Marshal(degradation)
	if _, err = tx.ExecContext(ctx, `INSERT OR REPLACE INTO android_package_evidence_snapshots(id,generation,collector,completeness,degradation_reasons_json,evidence_hash,created_at_ms) VALUES(?,?,?,?,?,?,?)`, snapshot.SnapshotID, snapshot.Generation, collector, snapshot.Completeness, string(reasons), snapshot.EvidenceHash, time.Now().UnixMilli()); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM android_package_records WHERE snapshot_id=?`, snapshot.SnapshotID); err != nil {
		return err
	}
	for _, r := range snapshot.Records {
		paths, _ := json.Marshal(r.Paths)
		deg, _ := json.Marshal(r.DegradationReasons)
		var code, data, cache, native any
		if r.CodeBytes.Known {
			code = r.CodeBytes.Bytes
		}
		if r.DataBytes.Known {
			data = r.DataBytes.Bytes
		}
		if r.CacheBytes.Known {
			cache = r.CacheBytes.Bytes
		}
		if r.NativeBytes.Known {
			native = r.NativeBytes.Bytes
		}
		if _, err = tx.ExecContext(ctx, `INSERT INTO android_package_records(snapshot_id,package_name,user_id,installed,archived,system_app,enabled_state,completeness,ui_state,degradation_reasons_json,paths_json,code_bytes_known,code_bytes,data_bytes_known,data_bytes,cache_bytes_known,cache_bytes,native_bytes_known,native_bytes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`, snapshot.SnapshotID, r.PackageName, r.UserID, boolInt(r.Installed), boolInt(r.Archived), boolInt(r.SystemApp), r.EnabledState, r.CollectionCompleteness, r.UIState, string(deg), string(paths), boolInt(r.CodeBytes.Known), code, boolInt(r.DataBytes.Known), data, boolInt(r.CacheBytes.Known), cache, boolInt(r.NativeBytes.Known), native); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) LatestPackageEvidence(ctx context.Context) (*packageevidence.NormalizedSnapshot, error) {
	var out packageevidence.NormalizedSnapshot
	err := s.db.QueryRowContext(ctx, `SELECT id,generation,completeness,evidence_hash FROM android_package_evidence_snapshots ORDER BY created_at_ms DESC LIMIT 1`).Scan(&out.SnapshotID, &out.Generation, &out.Completeness, &out.EvidenceHash)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	out.Protocol = packageevidence.Protocol
	rows, err := s.db.QueryContext(ctx, `SELECT package_name,user_id,installed,archived,system_app,enabled_state,completeness,ui_state,degradation_reasons_json,paths_json,code_bytes_known,code_bytes,data_bytes_known,data_bytes,cache_bytes_known,cache_bytes,native_bytes_known,native_bytes FROM android_package_records WHERE snapshot_id=? ORDER BY user_id,package_name`, out.SnapshotID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var r packageevidence.NormalizedRecord
		var installed, archived, system, ck, dk, cak, nk int
		var deg, paths string
		var cb, dbb, cab, nb sql.NullInt64
		if err := rows.Scan(&r.PackageName, &r.UserID, &installed, &archived, &system, &r.EnabledState, &r.CollectionCompleteness, &r.UIState, &deg, &paths, &ck, &cb, &dk, &dbb, &cak, &cab, &nk, &nb); err != nil {
			return nil, err
		}
		r.Key = packageevidence.PackageKey(r.PackageName, r.UserID)
		r.Installed = installed != 0
		r.Archived = archived != 0
		r.SystemApp = system != 0
		_ = json.Unmarshal([]byte(deg), &r.DegradationReasons)
		_ = json.Unmarshal([]byte(paths), &r.Paths)
		r.CodeBytes = byteEvidence(ck, cb)
		r.DataBytes = byteEvidence(dk, dbb)
		r.CacheBytes = byteEvidence(cak, cab)
		r.NativeBytes = byteEvidence(nk, nb)
		out.Records = append(out.Records, r)
	}
	return &out, rows.Err()
}

func byteEvidence(known int, value sql.NullInt64) packageevidence.ByteEvidence {
	if known != 0 && value.Valid {
		return packageevidence.KnownBytes(value.Int64)
	}
	return packageevidence.UnknownBytes()
}
func boolInt(v bool) int {
	if v {
		return 1
	}
	return 0
}

func (s *SQLiteStore) ReplaceDuplicateGroups(ctx context.Context, scanID string, generation int64, groups []intelligence.DuplicateGroup) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, `SELECT id FROM cleanup_duplicate_groups WHERE scan_id=? AND scan_generation=?`, scanID, generation)
	if err != nil {
		return err
	}
	var ids []string
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return err
		}
		ids = append(ids, id)
	}
	rows.Close()
	for _, id := range ids {
		if _, err := tx.ExecContext(ctx, `DELETE FROM cleanup_duplicate_groups WHERE id=?`, id); err != nil {
			return err
		}
	}
	now := time.Now().UnixMilli()
	for _, g := range groups {
		if g.ScanID != scanID || g.ScanGeneration != generation {
			return errors.New("duplicate_generation_mismatch")
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO cleanup_duplicate_groups(id,scan_id,scan_generation,logical_size_bytes,content_hash,verification_state,canonical_keep_path,retained_copy_proof,source_context_hash,group_hash,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?)`, g.GroupID, g.ScanID, g.ScanGeneration, g.LogicalSizeBytes, g.ContentHash, g.VerificationState, g.CanonicalKeepPath, g.RetainedCopyProof, g.SourceContextHash, g.GroupHash, now); err != nil {
			return err
		}
		for _, c := range g.Candidates {
			ev, _ := json.Marshal(c.Evidence)
			if _, err := tx.ExecContext(ctx, `INSERT INTO cleanup_duplicate_candidates(group_id,path,expected_identity,content_hash,logical_size_bytes,allocated_size_bytes,keep,actionable_evidence_json,verification_state) VALUES(?,?,?,?,?,?,?,?,?)`, g.GroupID, c.Path, c.ExpectedIdentity, c.ContentHash, c.LogicalSizeBytes, c.AllocatedSizeBytes, boolInt(c.Keep), string(ev), c.VerificationState); err != nil {
				return err
			}
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) PersistResidueEvidence(ctx context.Context, items []intelligence.ResidueEvidence) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	now := time.Now().UnixMilli()
	for _, v := range items {
		amb, _ := json.Marshal(v.AmbiguityReasons)
		ev, _ := json.Marshal(v.Evidence)
		id := stableProductID("residue", v.Candidate.PackageKey, v.Candidate.Path, v.Candidate.EvidenceHash)
		if _, err := tx.ExecContext(ctx, `INSERT OR REPLACE INTO cleanup_residue_evidence(id,package_key,package_name,user_id,path,evidence_hash,confidence,ambiguity_reasons_json,special_path_context,protected,requires_recheck,actionable_evidence_json,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)`, id, v.Candidate.PackageKey, v.Candidate.PackageName, v.Candidate.UserID, v.Candidate.Path, v.Candidate.EvidenceHash, v.Confidence, string(amb), v.SpecialContext, boolInt(v.Evidence.ProtectedState != "unprotected"), boolInt(v.Candidate.RequiresRecheck), string(ev), now); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) PersistRecommendations(ctx context.Context, scanID string, generation int64, items []intelligence.Recommendation) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `DELETE FROM cleanup_recommendations WHERE scan_id=? AND scan_generation=?`, scanID, generation); err != nil {
		return err
	}
	now := time.Now().UnixMilli()
	for _, v := range items {
		proof, _ := json.Marshal(map[string]any{"proof": v.Proof, "evidence": v.Evidence})
		if _, err := tx.ExecContext(ctx, `INSERT INTO cleanup_recommendations(id,scan_id,scan_generation,recommendation_kind,evidence_hash,proof_json,bounded_import,can_authorize,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?)`, v.ID, scanID, generation, v.Kind, v.EvidenceHash, string(proof), boolInt(v.BoundedImport), boolInt(v.CanAuthorize), now); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *SQLiteStore) PersistRuleResults(ctx context.Context, planID string, items []intelligence.RuleResult) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `DELETE FROM cleanup_rule_results WHERE plan_id=?`, planID); err != nil {
		return err
	}
	for _, v := range items {
		prov, _ := json.Marshal(map[string]any{"provenance": v.Provenance})
		ev, _ := json.Marshal(v.Evidence)
		if _, err := tx.ExecContext(ctx, `INSERT INTO cleanup_rule_results(plan_id,rule_id,path,pattern_hash,provenance_json,bounded_regex,decision,actionable_evidence_json) VALUES(?,?,?,?,?,?,?,?)`, v.PlanID, v.RuleID, v.Evidence.Path, v.PatternHash, string(prov), 1, v.Decision, string(ev)); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func stableProductID(parts ...string) string {
	sort.Strings(parts)
	sum := sha256.Sum256([]byte(strings.Join(parts, "\x00")))
	return hex.EncodeToString(sum[:])
}
