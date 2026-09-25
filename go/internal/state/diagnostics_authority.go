package state

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/mikeyphw/dustui/go/internal/diagnostics"
)

// RecordDiagnostic redacts before persistence and immediately enforces bounded retention.
func (s *SQLiteStore) RecordDiagnostic(ctx context.Context, event diagnostics.DiagnosticEvent) (diagnostics.DiagnosticEvent, error) {
	policy := diagnostics.DefaultDiagnosticPolicy()
	event = diagnostics.RedactEvent(event, policy)
	if strings.TrimSpace(event.SessionID) == "" {
		event.SessionID = "android-app"
	}
	if strings.TrimSpace(event.Area) == "" {
		event.Area = "app"
	}
	if strings.TrimSpace(event.Action) == "" {
		return diagnostics.DiagnosticEvent{}, errors.New("diagnostic_action_required")
	}
	if event.CreatedAtMS <= 0 {
		event.CreatedAtMS = time.Now().UnixMilli()
	}
	raw, _ := json.Marshal(event.Values)
	eventID := stableProductID("diagnostic", event.SessionID, event.OperationID, event.Area, event.Action, fmt.Sprint(event.CreatedAtMS))
	if _, err := s.db.ExecContext(ctx, `INSERT OR REPLACE INTO diagnostic_events(event_id,session_id,operation_id,parent_id,area,severity,action,result,values_redacted_json,raw_values_persisted,auto_upload,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)`,
		eventID, event.SessionID, event.OperationID, event.ParentID, event.Area, event.Severity, event.Action, event.Result, string(raw), 0, 0, event.CreatedAtMS); err != nil {
		return diagnostics.DiagnosticEvent{}, err
	}
	_, err := s.PruneDiagnostics(ctx, event.CreatedAtMS)
	return event, err
}

func (s *SQLiteStore) DiagnosticEvents(ctx context.Context, limit int) ([]diagnostics.DiagnosticEvent, error) {
	if limit <= 0 || limit > 1000 {
		limit = 200
	}
	rows, err := s.db.QueryContext(ctx, `SELECT session_id,COALESCE(operation_id,''),COALESCE(parent_id,''),area,severity,action,result,values_redacted_json,created_at_ms FROM diagnostic_events ORDER BY created_at_ms DESC,event_id DESC LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []diagnostics.DiagnosticEvent{}
	for rows.Next() {
		var e diagnostics.DiagnosticEvent
		var raw string
		if err := rows.Scan(&e.SessionID, &e.OperationID, &e.ParentID, &e.Area, &e.Severity, &e.Action, &e.Result, &raw, &e.CreatedAtMS); err != nil {
			return nil, err
		}
		_ = json.Unmarshal([]byte(raw), &e.Values)
		out = append(out, e)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) ClearDiagnosticEvents(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM diagnostic_events`)
	return err
}

func (s *SQLiteStore) upsertRetentionRules(ctx context.Context) error {
	for _, r := range diagnostics.RetentionMatrix() {
		if _, err := s.db.ExecContext(ctx, `INSERT OR REPLACE INTO privacy_retention_rules(privacy_class,retain_days,prune_with_evidence,export_redaction,backup_transfer_eligible) VALUES(?,?,?,?,?)`, r.Class, r.RetainDays, boolInt(r.PruneWithEvidence), r.ExportRedaction, boolInt(r.BackupTransferEligible)); err != nil {
			return err
		}
	}
	return nil
}

func rowsAffected(result sql.Result) int64 {
	if result == nil {
		return 0
	}
	n, _ := result.RowsAffected()
	return n
}

func (s *SQLiteStore) PruneDiagnostics(ctx context.Context, nowMS int64) (diagnostics.CompactionReport, error) {
	if nowMS <= 0 {
		nowMS = time.Now().UnixMilli()
	}
	policy := diagnostics.DefaultDiagnosticPolicy()
	if err := s.upsertRetentionRules(ctx); err != nil {
		return diagnostics.CompactionReport{}, err
	}
	report := diagnostics.CompactionReport{}
	eventCutoff := time.UnixMilli(nowMS).AddDate(0, 0, -policy.DiagnosticRetainDays).UnixMilli()
	result, err := s.db.ExecContext(ctx, `DELETE FROM diagnostic_events WHERE created_at_ms < ?`, eventCutoff)
	if err != nil {
		return report, err
	}
	report.ExpiredEvents = rowsAffected(result)
	if _, err := s.db.ExecContext(ctx, `DELETE FROM significant_problem_records WHERE created_at_ms < ?`, eventCutoff); err != nil {
		return report, err
	}
	bundleCutoff := time.UnixMilli(nowMS).AddDate(0, 0, -policy.SupportBundleRetainDays).UnixMilli()
	result, err = s.db.ExecContext(ctx, `DELETE FROM support_bundles WHERE created_at_ms < ?`, bundleCutoff)
	if err != nil {
		return report, err
	}
	report.ExpiredBundles = rowsAffected(result)

	// Bound individual sessions.
	sessions, err := s.db.QueryContext(ctx, `SELECT session_id,MAX(created_at_ms) AS newest FROM diagnostic_events GROUP BY session_id ORDER BY newest DESC`)
	if err != nil {
		return report, err
	}
	type sessionRow struct {
		id     string
		newest int64
	}
	var all []sessionRow
	for sessions.Next() {
		var r sessionRow
		if err := sessions.Scan(&r.id, &r.newest); err != nil {
			sessions.Close()
			return report, err
		}
		all = append(all, r)
	}
	sessions.Close()
	for i, r := range all {
		if i >= policy.MaxSessions {
			x, e := s.db.ExecContext(ctx, `DELETE FROM diagnostic_events WHERE session_id=?`, r.id)
			if e != nil {
				return report, e
			}
			report.RemovedSessions += rowsAffected(x)
			continue
		}
		x, e := s.db.ExecContext(ctx, `DELETE FROM diagnostic_events WHERE event_id IN (SELECT event_id FROM diagnostic_events WHERE session_id=? ORDER BY created_at_ms DESC,event_id DESC LIMIT -1 OFFSET ?)`, r.id, policy.MaxEventsPerSession)
		if e != nil {
			return report, e
		}
		report.OverflowEvents += rowsAffected(x)
	}
	result, err = s.db.ExecContext(ctx, `DELETE FROM support_bundles WHERE bundle_id IN (SELECT bundle_id FROM support_bundles ORDER BY created_at_ms DESC,bundle_id DESC LIMIT -1 OFFSET ?)`, policy.MaxSupportBundles)
	if err != nil {
		return report, err
	}
	report.OverflowBundles = rowsAffected(result)
	return report, nil
}

func scanEvidence(session *AndroidScanSession) diagnostics.EvidenceGeneration {
	if session == nil {
		return diagnostics.EvidenceGeneration{Subject: "scan", Health: diagnostics.EvidenceMissing, Reason: "No completed scan evidence"}
	}
	health := diagnostics.EvidenceCurrent
	reason := ""
	if session.Status != "COMPLETED" || session.IntegrityStatus != "COMPLETE" {
		health = diagnostics.EvidenceDegraded
		reason = "Scan is not complete and integrity-qualified"
	}
	return diagnostics.EvidenceGeneration{Subject: "scan", Generation: session.Generation, Hash: stableProductID("scan", session.ID, fmt.Sprint(session.Generation), session.Status, session.IntegrityStatus), Health: health, Reason: reason, AdvancedAtMS: session.StartedAt}
}

func (s *SQLiteStore) deriveEvidence(ctx context.Context) ([]diagnostics.EvidenceGeneration, error) {
	out := []diagnostics.EvidenceGeneration{}
	scan, err := s.LatestAndroidScanSession(ctx)
	if err != nil {
		return nil, err
	}
	out = append(out, scanEvidence(scan))
	pkg, err := s.LatestPackageEvidence(ctx)
	if err != nil {
		return nil, err
	}
	if pkg == nil {
		out = append(out, diagnostics.EvidenceGeneration{Subject: "package-storage", Health: diagnostics.EvidenceMissing, Reason: "Package/storage evidence unavailable"})
	} else {
		h := diagnostics.EvidenceCurrent
		reason := ""
		if pkg.Completeness != "supported" {
			h = diagnostics.EvidenceDegraded
			reason = "Package/storage collection is " + pkg.Completeness
		}
		out = append(out, diagnostics.EvidenceGeneration{Subject: "package-storage", Generation: pkg.Generation, Hash: pkg.EvidenceHash, Health: h, Reason: reason, AdvancedAtMS: time.Now().UnixMilli()})
	}
	runs, err := s.ActivityRuns(ctx, 100)
	if err != nil {
		return nil, err
	}
	opHealth := diagnostics.EvidenceCurrent
	opReason := ""
	generation := int64(1)
	for _, r := range runs {
		if r.UpdatedAtMS > generation {
			generation = r.UpdatedAtMS
		}
		if r.RecoveryRequired {
			opHealth = diagnostics.EvidenceDegraded
			opReason = "One or more durable operations require reconciliation"
			break
		}
	}
	out = append(out, diagnostics.EvidenceGeneration{Subject: "operations", Generation: generation, Hash: stableProductID("operations", fmt.Sprint(generation), string(opHealth)), Health: opHealth, Reason: opReason, AdvancedAtMS: generation})
	sort.Slice(out, func(i, j int) bool { return out[i].Subject < out[j].Subject })
	return out, nil
}

func problemForEvidence(e diagnostics.EvidenceGeneration) (diagnostics.Problem, bool) {
	h := diagnostics.EvaluateEvidence(e)
	if h == diagnostics.EvidenceCurrent {
		return diagnostics.Problem{}, false
	}
	id := "evidence_" + e.Subject + "_" + string(h)
	guidance := "Refresh the affected evidence before relying on dependent actions"
	route := "System/Diagnostics"
	if e.Subject == "scan" {
		guidance = "Run and complete a fresh scan, then review its integrity"
		route = "Overview/scan-history"
	}
	if e.Subject == "package-storage" {
		guidance = "Refresh app/package evidence; unavailable fields must remain explicit"
		route = "Apps/list"
	}
	if e.Subject == "operations" {
		guidance = "Open Activity and reconcile operations that lack terminal truth"
		route = "Activity/list"
	}
	return diagnostics.Problem{ID: id, Reason: firstNonBlank(e.Reason, "Evidence is "+string(h)), RecoveryRequired: h != diagnostics.EvidenceMissing, Risk: diagnostics.RiskLow, Guidance: guidance, RemediationActions: []diagnostics.RemediationAction{{Code: "OPEN_REMEDIATION", Label: "Review", Route: route}}}, true
}

func firstNonBlank(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return v
		}
	}
	return "Unknown diagnostic state"
}

func (s *SQLiteStore) latestRecoveryOperationID(ctx context.Context) string {
	var operationID string
	err := s.db.QueryRowContext(ctx, `SELECT operation_id FROM activity_runs WHERE recovery_required=1 ORDER BY updated_at_ms DESC,operation_id DESC LIMIT 1`).Scan(&operationID)
	if err != nil {
		return ""
	}
	return operationID
}

func (s *SQLiteStore) DiagnosticsSnapshot(ctx context.Context) (diagnostics.DiagnosticSnapshot, error) {
	now := time.Now().UnixMilli()
	if _, err := s.PruneDiagnostics(ctx, now); err != nil {
		return diagnostics.DiagnosticSnapshot{}, err
	}
	evidence, err := s.deriveEvidence(ctx)
	if err != nil {
		return diagnostics.DiagnosticSnapshot{}, err
	}
	graph := diagnostics.NewProblemGraph()
	recoveryOperationID := s.latestRecoveryOperationID(ctx)
	for _, e := range evidence {
		if p, ok := problemForEvidence(e); ok {
			if e.Subject == "operations" {
				p.OperationID = recoveryOperationID
			}
			_ = graph.Add(p)
		}
	}
	// Persist latest evidence/problem graph as durable explanatory state.
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return diagnostics.DiagnosticSnapshot{}, err
	}
	defer tx.Rollback()
	for _, e := range evidence {
		derived, _ := json.Marshal(map[string]any{})
		invalid, _ := json.Marshal(map[string]any{})
		if _, err := tx.ExecContext(ctx, `INSERT OR REPLACE INTO evidence_health_generations(subject,generation,evidence_hash,health,derived_from_json,invalidated_by_json,completed_alone_healthy,advanced_at_ms) VALUES(?,?,?,?,?,?,0,?)`, e.Subject, e.Generation, e.Hash, e.Health, string(derived), string(invalid), e.AdvancedAtMS); err != nil {
			return diagnostics.DiagnosticSnapshot{}, err
		}
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM problem_graph_nodes`); err != nil {
		return diagnostics.DiagnosticSnapshot{}, err
	}
	for _, p := range graph.VisibleProblems() {
		sev := "warning"
		if p.RecoveryRequired {
			sev = "error"
		}
		if _, err := tx.ExecContext(ctx, `INSERT OR REPLACE INTO problem_graph_nodes(problem_id,root_cause_id,stable_id,severity,risk,guidance,suppressed_by_root,created_at_ms) VALUES(?,?,?,?,?,?,0,?)`, p.ID, p.RootCauseID, p.ID, sev, p.Risk, p.Guidance, now); err != nil {
			return diagnostics.DiagnosticSnapshot{}, err
		}
		recordID := stableProductID("diagnostic-problem", p.ID, p.OperationID)
		if _, err := tx.ExecContext(ctx, `INSERT OR REPLACE INTO significant_problem_records(record_id,problem_id,operation_id,severity,risk,guidance,raw_event_ref,created_at_ms) VALUES(?,?,?,?,?,?,?,?)`, recordID, p.ID, p.OperationID, sev, p.Risk, p.Guidance, "", now); err != nil {
			return diagnostics.DiagnosticSnapshot{}, err
		}
	}
	if err := tx.Commit(); err != nil {
		return diagnostics.DiagnosticSnapshot{}, err
	}
	supportState := "available"
	var active int
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM activity_runs r LEFT JOIN automation_admissions a ON a.operation_id=r.operation_id WHERE a.automation_kind='support-export' AND r.active=1`).Scan(&active)
	if active > 0 {
		supportState = "exporting"
	}
	return diagnostics.DiagnosticSnapshot{Protocol: diagnostics.Protocol, GeneratedAtMS: now, EvidenceGenerations: evidence, Problems: graph.VisibleProblems(), Retention: diagnostics.DefaultDiagnosticPolicy(), SupportExportState: supportState}, nil
}

func (s *SQLiteStore) PersistSupportBundle(ctx context.Context, operationID string, b diagnostics.SupportBundle) error {
	b.OperationID = operationID
	bundleID := operationID
	manifestJSON, _ := json.Marshal(map[string]any{"manifest": b.Manifest, "protocol": b.Protocol, "operationId": operationID, "createdAtMs": b.CreatedAtMS, "expiresAtMs": b.ExpiresAtMS, "privacyClasses": b.PrivacyClasses})
	checks, _ := json.Marshal(b.Checksums)
	selected, _ := json.Marshal(map[string]any{"evidenceGenerations": b.EvidenceGenerations, "problems": b.Problems, "diagnosticEventCount": b.DiagnosticEventCount})
	_, err := s.db.ExecContext(ctx, `INSERT OR REPLACE INTO support_bundles(bundle_id,manifest_json,checksums_json,privacy_mode,selected_evidence_json,includes_file_content,automatic_upload,reproducible_export_key,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?)`, bundleID, string(manifestJSON), string(checks), b.PrivacyMode, string(selected), boolInt(b.IncludesFileContent), boolInt(b.AutomaticUpload), b.ReproducibleExportKey, b.CreatedAtMS)
	if err != nil {
		return err
	}
	_, err = s.PruneDiagnostics(ctx, time.Now().UnixMilli())
	return err
}

func (s *SQLiteStore) DeleteSupportBundle(ctx context.Context, operationID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM support_bundles WHERE bundle_id=?`, operationID)
	return err
}
