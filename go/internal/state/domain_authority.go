package state

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/mikeyphw/dustui/go/internal/authority"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
)

type DomainAuthorityEnvelope struct {
	Authority authority.Meta `json:"authority"`
	Data      any            `json:"data"`
}

func (s *SQLiteStore) DomainAuthority(ctx context.Context, req authority.Request, options ProductInsightsOptions) (DomainAuthorityEnvelope, error) {
	if err := authority.ValidateRequest(req, req.Domain); err != nil {
		return DomainAuthorityEnvelope{}, err
	}
	session, err := s.AndroidScanSessionByID(ctx, req.ScanID)
	if err != nil {
		return DomainAuthorityEnvelope{}, err
	}
	if session == nil {
		return DomainAuthorityEnvelope{}, errors.New("domain_authority_scan_not_found")
	}
	if session.Generation != req.Generation {
		return DomainAuthorityEnvelope{}, fmt.Errorf("domain_authority_generation_mismatch:want=%d:got=%d", session.Generation, req.Generation)
	}

	insights, err := s.ProductInsights(ctx, req.ScanID, options)
	if err != nil {
		return DomainAuthorityEnvelope{}, err
	}

	packageDependent := map[string]bool{"package-storage": true, "residue": true, "cleanup-intelligence": true, "recommendations": true}
	freshness, evidence := authority.FreshnessFresh, authority.EvidenceSupported
	reasons := []string{}
	if packageDependent[req.Domain] {
		snap, err := s.PackageEvidenceForScan(ctx, req.ScanID, req.Generation)
		if err != nil {
			return DomainAuthorityEnvelope{}, err
		}
		if snap == nil {
			evidence, freshness = authority.EvidenceUnavailable, authority.FreshnessUnknown
			reasons = append(reasons, "package_evidence_missing")
		} else {
			evidence = normalizeEvidenceState(snap.Completeness)
			if snap.Generation != req.Generation {
				freshness = authority.FreshnessStale
				reasons = append(reasons, "package_evidence_generation_mismatch")
			}
			for _, r := range snap.Records {
				reasons = appendUnique(reasons, r.DegradationReasons...)
			}
		}
	}
	var data any
	switch req.Domain {
	case "package-storage":
		data = map[string]any{"appSummaries": insights["appSummaries"], "packageProfiles": insights["packageProfiles"], "appStorage": insights["appStorage"]}
	case "residue":
		data = insights["residue"]
	case "cleanup-intelligence":
		data = map[string]any{"advanced": insights["advanced"], "snapshotDelta": insights["snapshotDelta"]}
	case "recommendations":
		data = insights["recommendations"]
	case "growth":
		data = insights["growth"]
	case "modules":
		data = insights["modules"]
	default:
		return DomainAuthorityEnvelope{}, fmt.Errorf("domain_authority_unknown_domain:%s", req.Domain)
	}
	return DomainAuthorityEnvelope{Authority: authority.NewMeta(req.Domain, req.ScanID, req.Generation, freshness, evidence, reasons), Data: data}, nil
}

func normalizeEvidenceState(v string) string {
	switch v {
	case packageevidence.CompletenessSupported:
		return authority.EvidenceSupported
	case packageevidence.CompletenessPartial:
		return authority.EvidencePartial
	case packageevidence.CompletenessBlocked:
		return authority.EvidenceBlocked
	default:
		return authority.EvidenceUnavailable
	}
}
func appendUnique(dst []string, values ...string) []string {
	seen := map[string]bool{}
	for _, x := range dst {
		seen[x] = true
	}
	for _, x := range values {
		if x != "" && !seen[x] {
			dst = append(dst, x)
			seen[x] = true
		}
	}
	return dst
}

func (s *SQLiteStore) PersistVerificationReport(ctx context.Context, report VerificationReport, generation int64) error {
	if report.OperationID == "" || report.ScanID == "" || generation <= 0 {
		return errors.New("verification_report_identity_required")
	}
	raw, err := json.Marshal(report)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, `INSERT OR REPLACE INTO android_cleanup_verification_reports(operation_id,scan_id,generation,report_json,verification_attempts,updated_at_ms) VALUES(?,?,?,?,?,?)`, report.OperationID, report.ScanID, generation, string(raw), report.VerificationAttempts, time.Now().UnixMilli())
	return err
}
func (s *SQLiteStore) VerificationReport(ctx context.Context, operationID string) (*VerificationReport, int64, error) {
	var raw string
	var gen int64
	err := s.db.QueryRowContext(ctx, `SELECT report_json,generation FROM android_cleanup_verification_reports WHERE operation_id=?`, operationID).Scan(&raw, &gen)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, 0, nil
	}
	if err != nil {
		return nil, 0, err
	}
	var report VerificationReport
	if err := json.Unmarshal([]byte(raw), &report); err != nil {
		return nil, 0, err
	}
	return &report, gen, nil
}

type VerificationReportRecord struct {
	Report     VerificationReport `json:"report"`
	Generation int64              `json:"generation"`
}

func (s *SQLiteStore) VerificationReports(ctx context.Context, limit int) ([]VerificationReportRecord, error) {
	if limit <= 0 {
		limit = 100
	}
	if limit > 100 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `SELECT report_json,generation FROM android_cleanup_verification_reports ORDER BY updated_at_ms DESC, operation_id ASC LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	reports := make([]VerificationReportRecord, 0, limit)
	for rows.Next() {
		var raw string
		var generation int64
		if err := rows.Scan(&raw, &generation); err != nil {
			return nil, err
		}
		var report VerificationReport
		if err := json.Unmarshal([]byte(raw), &report); err != nil {
			return nil, err
		}
		reports = append(reports, VerificationReportRecord{Report: report, Generation: generation})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return reports, nil
}
