package state

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
)

const (
	AutomationProtocol         = "dustui.automation.activity.v2"
	notificationTTLMaxMS int64 = 15 * 60 * 1000
)

var terminalAutomationStates = map[string]bool{
	"success":           true,
	"failure":           true,
	"cancelled":         true,
	"interrupted":       true,
	"recovery_required": true,
}

type DurableAdmission struct {
	Protocol            string   `json:"protocol"`
	OperationID         string   `json:"operationId"`
	RequestID           string   `json:"requestId"`
	Sources             []string `json:"sources"`
	Kind                string   `json:"kind"`
	Generation          int64    `json:"generation"`
	State               string   `json:"state"`
	TerminalTruth       string   `json:"terminalTruth"`
	LastSequence        int64    `json:"lastSequence"`
	RecoveryRequired    bool     `json:"recoveryRequired"`
	EvidenceCorrelation string   `json:"evidenceCorrelation"`
	AcceptedNotSuccess  bool     `json:"acceptedNotSuccess"`
}

type ActivityRun struct {
	Protocol            string          `json:"protocol"`
	OperationID         string          `json:"operationId"`
	RequestID           string          `json:"requestId"`
	Kind                string          `json:"kind"`
	Sources             []string        `json:"sources"`
	State               string          `json:"state"`
	Active              bool            `json:"active"`
	History             bool            `json:"history"`
	RecoveryRequired    bool            `json:"recoveryRequired"`
	TerminalTruth       string          `json:"terminalTruth"`
	Progress            json.RawMessage `json:"progress"`
	Result              json.RawMessage `json:"result"`
	Verification        json.RawMessage `json:"verification"`
	RetryGate           string          `json:"retryGate"`
	CancelGate          string          `json:"cancelGate"`
	ReconcileGate       string          `json:"reconcileGate"`
	EvidenceCorrelation string          `json:"evidenceCorrelation"`
	UpdatedAtMS         int64           `json:"updatedAtMs"`
}

type NotificationCapability struct {
	Protocol         string `json:"protocol"`
	Nonce            string `json:"nonce"`
	OperationID      string `json:"operationId"`
	RequestID        string `json:"requestId"`
	Action           string `json:"action"`
	ExpiresAtMS      int64  `json:"expiresAtMs"`
	OperationBinding string `json:"operationBinding"`
}

type NotificationClaim struct {
	Accepted    bool   `json:"accepted"`
	Reason      string `json:"reason"`
	OperationID string `json:"operationId,omitempty"`
	RequestID   string `json:"requestId,omitempty"`
}

type OperationAction struct {
	Allowed     bool   `json:"allowed"`
	Reason      string `json:"reason"`
	OperationID string `json:"operationId"`
	RequestID   string `json:"requestId"`
	State       string `json:"state"`
}

func normalizeAutomationSource(source string) (string, error) {
	source = strings.TrimSpace(strings.ToLower(source))
	switch source {
	case "ui", "workmanager", "tasker", "notification", "recovery":
		return source, nil
	default:
		return "", errors.New("automation_source_invalid")
	}
}

func normalizeAutomationKind(kind string) (string, error) {
	kind = strings.TrimSpace(strings.ToLower(kind))
	switch kind {
	case "scan", "status", "inspect", "analysis", "duplicate-analysis":
		return kind, nil
	default:
		return "", errors.New("automation_kind_invalid")
	}
}

func terminalTruthForState(state string) string {
	if terminalAutomationStates[state] {
		return state
	}
	return "pending"
}

func automationGates(kind, state string, recovery bool) (retry, cancel, reconcile string) {
	terminal := terminalAutomationStates[state]
	if kind == "scan" && (state == "failure" || state == "interrupted" || state == "recovery_required") {
		retry = "allowed"
	} else {
		retry = "blocked"
	}
	if kind != "scan" && kind != "duplicate-analysis" {
		cancel = "blocked_kind"
	} else if terminal || state == "cancelling" {
		cancel = "blocked"
	} else {
		cancel = "allowed"
	}
	if recovery || state == "recovery_required" || !terminal {
		reconcile = "allowed"
	} else {
		reconcile = "blocked"
	}
	return
}

func (s *SQLiteStore) AdmitAutomation(ctx context.Context, requestID, source, kind string, generation int64, fingerprint string) (DurableAdmission, error) {
	requestID, fingerprint = strings.TrimSpace(requestID), strings.TrimSpace(fingerprint)
	if requestID == "" || generation <= 0 || fingerprint == "" {
		return DurableAdmission{}, errors.New("automation_identity_required")
	}
	var err error
	if source, err = normalizeAutomationSource(source); err != nil {
		return DurableAdmission{}, err
	}
	if kind, err = normalizeAutomationKind(kind); err != nil {
		return DurableAdmission{}, err
	}

	opID := "op-" + stableProductID(requestID, kind, fmt.Sprint(generation), fingerprint)[:24]
	now := time.Now().UnixMilli()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return DurableAdmission{}, err
	}
	defer tx.Rollback()

	var existingID, existingFingerprint, existingKind string
	var existingGeneration int64
	lookupErr := tx.QueryRowContext(ctx, `SELECT o.id,o.request_fingerprint,o.generation,COALESCE(a.automation_kind,'') FROM operations o LEFT JOIN automation_admissions a ON a.operation_id=o.id WHERE o.request_id=?`, requestID).Scan(&existingID, &existingFingerprint, &existingGeneration, &existingKind)
	if lookupErr != nil && !errors.Is(lookupErr, sql.ErrNoRows) {
		return DurableAdmission{}, lookupErr
	}
	if lookupErr == nil {
		if existingFingerprint != fingerprint || existingGeneration != generation || existingKind != kind {
			return DurableAdmission{}, errors.New("automation_request_reuse_mismatch")
		}
		opID = existingID
	} else {
		if _, err := tx.ExecContext(ctx, `INSERT INTO operations(id,request_id,request_fingerprint,generation,state,last_sequence,recovery_required,created_at_ms,updated_at_ms) VALUES(?,?,?,?,?,?,?,?,?)`, opID, requestID, fingerprint, generation, "accepted", 1, 0, now, now); err != nil {
			return DurableAdmission{}, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO operation_events(operation_id,sequence,generation,state,reason,message,created_at_ms) VALUES(?,?,?,?,?,?,?)`, opID, 1, generation, "accepted", "admitted", "operation accepted; not terminal success", now); err != nil {
			return DurableAdmission{}, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO automation_admissions(operation_id,request_id,source,automation_kind,generation,accepted_not_success,terminal_truth,evidence_correlation,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?)`, opID, requestID, source, kind, generation, 1, "pending", fingerprint, now); err != nil {
			return DurableAdmission{}, err
		}
		retryGate, cancelGate, reconcileGate := automationGates(kind, "accepted", false)
		if _, err := tx.ExecContext(ctx, `INSERT INTO activity_runs(operation_id,active,history,recovery_required,terminal_truth,progress_json,result_json,verification_json,retry_gate,cancel_gate,reconcile_gate,evidence_correlation,updated_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)`, opID, 1, 0, 0, "pending", `{"state":"accepted"}`, `{}`, `{"state":"pending"}`, retryGate, cancelGate, reconcileGate, fingerprint, now); err != nil {
			return DurableAdmission{}, err
		}
	}
	if _, err := tx.ExecContext(ctx, `INSERT OR REPLACE INTO automation_sources(operation_id,source,adapter_role,thin_adapter,last_observed_state,last_observed_sequence,terminal_reported) VALUES(?,?,?,?,COALESCE((SELECT state FROM operations WHERE id=?),'accepted'),COALESCE((SELECT last_sequence FROM operations WHERE id=?),1),COALESCE((SELECT CASE WHEN terminal_sequence IS NULL THEN 0 ELSE 1 END FROM operations WHERE id=?),0))`, opID, source, "platform-adapter", 1, opID, opID, opID); err != nil {
		return DurableAdmission{}, err
	}
	if err := tx.Commit(); err != nil {
		return DurableAdmission{}, err
	}
	return s.ObserveAutomation(ctx, opID)
}

func validAutomationTransition(from, to string) bool {
	if terminalAutomationStates[from] {
		return false
	}
	if from == to {
		return true
	}
	switch from {
	case "accepted":
		return to == "running" || to == "cancelling" || to == "failure" || to == "recovery_required"
	case "running":
		return to == "cancelling" || terminalAutomationStates[to]
	case "cancelling":
		return to == "cancelled" || to == "recovery_required"
	default:
		return to == "recovery_required"
	}
}

func (s *SQLiteStore) AdvanceAutomation(ctx context.Context, opID, stateValue, reason, message string) (DurableAdmission, error) {
	opID, stateValue = strings.TrimSpace(opID), strings.TrimSpace(strings.ToLower(stateValue))
	if opID == "" {
		return DurableAdmission{}, errors.New("automation_operation_required")
	}
	allowedState := stateValue == "accepted" || stateValue == "running" || stateValue == "cancelling" || terminalAutomationStates[stateValue]
	if !allowedState {
		return DurableAdmission{}, errors.New("automation_state_invalid")
	}
	now := time.Now().UnixMilli()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return DurableAdmission{}, err
	}
	defer tx.Rollback()
	var seq, generation int64
	var current, kind string
	var recovery int
	if err := tx.QueryRowContext(ctx, `SELECT o.last_sequence,o.generation,o.state,o.recovery_required,a.automation_kind FROM operations o JOIN automation_admissions a ON a.operation_id=o.id WHERE o.id=?`, opID).Scan(&seq, &generation, &current, &recovery, &kind); err != nil {
		return DurableAdmission{}, err
	}
	if current == stateValue {
		// Repeated nonterminal reports are durable progress observations, not no-ops.
		// This lets Activity survive process death with the most recent progress message.
		if terminalAutomationStates[current] {
			if err := tx.Commit(); err != nil {
				return DurableAdmission{}, err
			}
			return s.ObserveAutomation(ctx, opID)
		}
		seq++
		if _, err := tx.ExecContext(ctx, `UPDATE operations SET last_sequence=?,updated_at_ms=? WHERE id=?`, seq, now, opID); err != nil {
			return DurableAdmission{}, err
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO operation_events(operation_id,sequence,generation,state,reason,message,created_at_ms) VALUES(?,?,?,?,?,?,?)`, opID, seq, generation, stateValue, reason, message, now); err != nil {
			return DurableAdmission{}, err
		}
		if _, err := tx.ExecContext(ctx, `UPDATE automation_sources SET last_observed_state=?,last_observed_sequence=? WHERE operation_id=?`, stateValue, seq, opID); err != nil {
			return DurableAdmission{}, err
		}
		if _, err := tx.ExecContext(ctx, `UPDATE activity_runs SET progress_json=?,updated_at_ms=? WHERE operation_id=?`, fmt.Sprintf(`{"state":%q,"sequence":%d,"reason":%q,"message":%q}`, stateValue, seq, reason, message), now, opID); err != nil {
			return DurableAdmission{}, err
		}
		if err := tx.Commit(); err != nil {
			return DurableAdmission{}, err
		}
		return s.ObserveAutomation(ctx, opID)
	}
	if !validAutomationTransition(current, stateValue) {
		return DurableAdmission{}, fmt.Errorf("automation_transition_invalid:%s->%s", current, stateValue)
	}
	terminal := terminalAutomationStates[stateValue]
	truth := terminalTruthForState(stateValue)
	seq++
	recoveryRequired := stateValue == "recovery_required" || recovery != 0
	retry, cancel, reconcile := automationGates(kind, stateValue, recoveryRequired)
	if _, err := tx.ExecContext(ctx, `UPDATE operations SET state=?,last_sequence=?,terminal_sequence=CASE WHEN ? THEN ? ELSE terminal_sequence END,recovery_required=?,updated_at_ms=? WHERE id=?`, stateValue, seq, boolInt(terminal), seq, boolInt(recoveryRequired), now, opID); err != nil {
		return DurableAdmission{}, err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO operation_events(operation_id,sequence,generation,state,reason,message,created_at_ms) VALUES(?,?,?,?,?,?,?)`, opID, seq, generation, stateValue, reason, message, now); err != nil {
		return DurableAdmission{}, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE automation_admissions SET terminal_truth=? WHERE operation_id=?`, truth, opID); err != nil {
		return DurableAdmission{}, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE automation_sources SET last_observed_state=?,last_observed_sequence=?,terminal_reported=? WHERE operation_id=?`, stateValue, seq, boolInt(terminal), opID); err != nil {
		return DurableAdmission{}, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE activity_runs SET active=?,history=?,recovery_required=?,terminal_truth=?,progress_json=?,result_json=?,verification_json=?,retry_gate=?,cancel_gate=?,reconcile_gate=?,updated_at_ms=? WHERE operation_id=?`, boolInt(!terminal), boolInt(terminal), boolInt(recoveryRequired), truth, fmt.Sprintf(`{"state":%q,"sequence":%d,"reason":%q,"message":%q}`, stateValue, seq, reason, message), fmt.Sprintf(`{"terminalTruth":%q,"reason":%q,"message":%q}`, truth, reason, message), fmt.Sprintf(`{"terminal":%t,"recoveryRequired":%t}`, terminal, recoveryRequired), retry, cancel, reconcile, now, opID); err != nil {
		return DurableAdmission{}, err
	}
	if err := tx.Commit(); err != nil {
		return DurableAdmission{}, err
	}
	return s.ObserveAutomation(ctx, opID)
}

func (s *SQLiteStore) ObserveAutomation(ctx context.Context, opID string) (DurableAdmission, error) {
	var v DurableAdmission
	var recovery, accepted int
	err := s.db.QueryRowContext(ctx, `SELECT o.id,o.request_id,a.automation_kind,o.generation,o.state,a.terminal_truth,o.last_sequence,o.recovery_required,a.evidence_correlation,a.accepted_not_success FROM operations o JOIN automation_admissions a ON a.operation_id=o.id WHERE o.id=?`, opID).Scan(&v.OperationID, &v.RequestID, &v.Kind, &v.Generation, &v.State, &v.TerminalTruth, &v.LastSequence, &recovery, &v.EvidenceCorrelation, &accepted)
	if err != nil {
		return DurableAdmission{}, err
	}
	v.Protocol, v.RecoveryRequired, v.AcceptedNotSuccess = AutomationProtocol, recovery != 0, accepted != 0
	rows, err := s.db.QueryContext(ctx, `SELECT source FROM automation_sources WHERE operation_id=? ORDER BY source`, opID)
	if err != nil {
		return DurableAdmission{}, err
	}
	defer rows.Close()
	for rows.Next() {
		var source string
		if err := rows.Scan(&source); err != nil {
			return DurableAdmission{}, err
		}
		v.Sources = append(v.Sources, source)
	}
	return v, rows.Err()
}

func (s *SQLiteStore) AutomationByRequest(ctx context.Context, requestID string) (DurableAdmission, error) {
	var opID string
	if err := s.db.QueryRowContext(ctx, `SELECT id FROM operations WHERE request_id=?`, requestID).Scan(&opID); err != nil {
		return DurableAdmission{}, err
	}
	return s.ObserveAutomation(ctx, opID)
}

func (s *SQLiteStore) CreateNotificationCapability(ctx context.Context, operationID, action string, ttlMS int64) (NotificationCapability, error) {
	if strings.TrimSpace(operationID) == "" || action != "cancel" {
		return NotificationCapability{}, errors.New("notification_action_invalid")
	}
	if ttlMS <= 0 || ttlMS > notificationTTLMaxMS {
		return NotificationCapability{}, errors.New("notification_ttl_invalid")
	}
	admission, err := s.ObserveAutomation(ctx, operationID)
	if err != nil {
		return NotificationCapability{}, err
	}
	if terminalAutomationStates[admission.State] {
		return NotificationCapability{}, errors.New("notification_action_terminal_operation")
	}
	nonceBytes := make([]byte, 24)
	if _, err := rand.Read(nonceBytes); err != nil {
		return NotificationCapability{}, err
	}
	nonce := hex.EncodeToString(nonceBytes)
	expires := time.Now().UnixMilli() + ttlMS
	binding := stableProductID(admission.OperationID, admission.RequestID, action, fmt.Sprint(admission.Generation), admission.EvidenceCorrelation)
	if _, err := s.db.ExecContext(ctx, `INSERT INTO notification_actions(nonce,operation_id,action,expires_at_ms,claimed_at_ms,replay_rejected,operation_binding) VALUES(?,?,?,?,NULL,0,?)`, nonce, admission.OperationID, action, expires, binding); err != nil {
		return NotificationCapability{}, err
	}
	return NotificationCapability{Protocol: AutomationProtocol, Nonce: nonce, OperationID: admission.OperationID, RequestID: admission.RequestID, Action: action, ExpiresAtMS: expires, OperationBinding: binding}, nil
}

func (s *SQLiteStore) ClaimNotificationCapability(ctx context.Context, nonce, operationID, action, binding string, nowMS int64) (NotificationClaim, error) {
	if nonce == "" || operationID == "" || action == "" || binding == "" {
		return NotificationClaim{Accepted: false, Reason: "missing_identity"}, nil
	}
	if nowMS <= 0 {
		nowMS = time.Now().UnixMilli()
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return NotificationClaim{}, err
	}
	defer tx.Rollback()
	var storedOperation, storedRequest, storedAction, storedBinding string
	var expires int64
	var claimed sql.NullInt64
	var replayRejected int
	err = tx.QueryRowContext(ctx, `SELECT n.operation_id,o.request_id,n.action,n.expires_at_ms,n.claimed_at_ms,n.operation_binding,n.replay_rejected FROM notification_actions n JOIN operations o ON o.id=n.operation_id WHERE n.nonce=?`, nonce).Scan(&storedOperation, &storedRequest, &storedAction, &expires, &claimed, &storedBinding, &replayRejected)
	if errors.Is(err, sql.ErrNoRows) {
		return NotificationClaim{Accepted: false, Reason: "unknown_nonce"}, nil
	}
	if err != nil {
		return NotificationClaim{}, err
	}
	reason, accepted := "accepted", true
	switch {
	case claimed.Valid || replayRejected != 0:
		accepted, reason = false, "replay"
	case nowMS >= expires:
		accepted, reason = false, "expired"
	case storedOperation != operationID || storedAction != action || storedBinding != binding:
		accepted, reason = false, "binding_mismatch"
	}
	if accepted {
		result, err := tx.ExecContext(ctx, `UPDATE notification_actions SET claimed_at_ms=? WHERE nonce=? AND claimed_at_ms IS NULL`, nowMS, nonce)
		if err != nil {
			return NotificationClaim{}, err
		}
		n, _ := result.RowsAffected()
		if n != 1 {
			accepted, reason = false, "replay"
		}
	}
	if !accepted {
		if _, err := tx.ExecContext(ctx, `UPDATE notification_actions SET replay_rejected=1,claimed_at_ms=COALESCE(claimed_at_ms,?) WHERE nonce=?`, nowMS, nonce); err != nil {
			return NotificationClaim{}, err
		}
	}
	if err := tx.Commit(); err != nil {
		return NotificationClaim{}, err
	}
	return NotificationClaim{Accepted: accepted, Reason: reason, OperationID: storedOperation, RequestID: storedRequest}, nil
}

func (s *SQLiteStore) ActivityRuns(ctx context.Context, limit int) ([]ActivityRun, error) {
	if limit <= 0 || limit > 500 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `SELECT r.operation_id,COALESCE(o.request_id,''),COALESCE(a.automation_kind,CASE WHEN EXISTS(SELECT 1 FROM android_cleanup_operations c WHERE c.id=r.operation_id) THEN 'cleanup' ELSE 'unknown' END),COALESCE(o.state,'unknown'),COALESCE((SELECT GROUP_CONCAT(source, ',') FROM automation_sources src WHERE src.operation_id=r.operation_id),''),r.active,r.history,r.recovery_required,r.terminal_truth,r.progress_json,r.result_json,r.verification_json,r.retry_gate,r.cancel_gate,r.reconcile_gate,r.evidence_correlation,r.updated_at_ms FROM activity_runs r LEFT JOIN operations o ON o.id=r.operation_id LEFT JOIN automation_admissions a ON a.operation_id=r.operation_id ORDER BY r.updated_at_ms DESC LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []ActivityRun{}
	for rows.Next() {
		var v ActivityRun
		var active, history, recovery int
		var progress, result, verification, sourceCSV string
		if err := rows.Scan(&v.OperationID, &v.RequestID, &v.Kind, &v.State, &sourceCSV, &active, &history, &recovery, &v.TerminalTruth, &progress, &result, &verification, &v.RetryGate, &v.CancelGate, &v.ReconcileGate, &v.EvidenceCorrelation, &v.UpdatedAtMS); err != nil {
			return nil, err
		}
		v.Protocol, v.Active, v.History, v.RecoveryRequired = AutomationProtocol, active != 0, history != 0, recovery != 0
		v.Progress, v.Result, v.Verification = json.RawMessage(progress), json.RawMessage(result), json.RawMessage(verification)
		if sourceCSV != "" {
			v.Sources = strings.Split(sourceCSV, ",")
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) ActivityRunByID(ctx context.Context, opID string) (ActivityRun, error) {
	var v ActivityRun
	var active, history, recovery int
	var progress, result, verification, sourceCSV string
	err := s.db.QueryRowContext(ctx, `SELECT r.operation_id,COALESCE(o.request_id,''),COALESCE(a.automation_kind,CASE WHEN EXISTS(SELECT 1 FROM android_cleanup_operations c WHERE c.id=r.operation_id) THEN 'cleanup' ELSE 'unknown' END),COALESCE(o.state,'unknown'),COALESCE((SELECT GROUP_CONCAT(source, ',') FROM automation_sources src WHERE src.operation_id=r.operation_id),''),r.active,r.history,r.recovery_required,r.terminal_truth,r.progress_json,r.result_json,r.verification_json,r.retry_gate,r.cancel_gate,r.reconcile_gate,r.evidence_correlation,r.updated_at_ms FROM activity_runs r LEFT JOIN operations o ON o.id=r.operation_id LEFT JOIN automation_admissions a ON a.operation_id=r.operation_id WHERE r.operation_id=?`, opID).Scan(&v.OperationID, &v.RequestID, &v.Kind, &v.State, &sourceCSV, &active, &history, &recovery, &v.TerminalTruth, &progress, &result, &verification, &v.RetryGate, &v.CancelGate, &v.ReconcileGate, &v.EvidenceCorrelation, &v.UpdatedAtMS)
	if err != nil {
		return ActivityRun{}, err
	}
	v.Protocol, v.Active, v.History, v.RecoveryRequired = AutomationProtocol, active != 0, history != 0, recovery != 0
	v.Progress, v.Result, v.Verification = json.RawMessage(progress), json.RawMessage(result), json.RawMessage(verification)
	if sourceCSV != "" {
		v.Sources = strings.Split(sourceCSV, ",")
	}
	return v, nil
}

func (s *SQLiteStore) automationSources(ctx context.Context, opID string) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT source FROM automation_sources WHERE operation_id=? ORDER BY source`, opID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var source string
		if err := rows.Scan(&source); err != nil {
			return nil, err
		}
		out = append(out, source)
	}
	return out, rows.Err()
}

func (s *SQLiteStore) RequestAutomationCancel(ctx context.Context, opID, source string) (OperationAction, error) {
	source, err := normalizeAutomationSource(source)
	if err != nil {
		return OperationAction{}, err
	}
	admission, err := s.ObserveAutomation(ctx, opID)
	if err != nil {
		return OperationAction{}, err
	}
	if admission.State == "cancelling" {
		return OperationAction{Allowed: true, Reason: "cancellation_already_authorized", OperationID: opID, RequestID: admission.RequestID, State: admission.State}, nil
	}
	retry, cancel, _ := automationGates(admission.Kind, admission.State, admission.RecoveryRequired)
	_ = retry
	if cancel != "allowed" {
		return OperationAction{Allowed: false, Reason: "cancel_gate_blocked", OperationID: opID, RequestID: admission.RequestID, State: admission.State}, nil
	}
	if _, err := s.db.ExecContext(ctx, `INSERT OR IGNORE INTO automation_sources(operation_id,source,adapter_role,thin_adapter,last_observed_state,last_observed_sequence,terminal_reported) VALUES(?,?,?,?,?,?,?)`, opID, source, "platform-adapter", 1, admission.State, admission.LastSequence, 0); err != nil {
		return OperationAction{}, err
	}
	updated, err := s.AdvanceAutomation(ctx, opID, "cancelling", "cancel_requested", "Go action gate authorized cancellation")
	if err != nil {
		return OperationAction{}, err
	}
	return OperationAction{Allowed: true, Reason: "cancel_authorized", OperationID: opID, RequestID: updated.RequestID, State: updated.State}, nil
}

func (s *SQLiteStore) ReconcileAutomation(ctx context.Context, opID string) (OperationAction, error) {
	admission, err := s.ObserveAutomation(ctx, opID)
	if err != nil {
		return OperationAction{}, err
	}
	_, _, gate := automationGates(admission.Kind, admission.State, admission.RecoveryRequired)
	if gate != "allowed" {
		return OperationAction{Allowed: false, Reason: "reconcile_gate_blocked", OperationID: opID, RequestID: admission.RequestID, State: admission.State}, nil
	}
	if admission.Kind == "scan" {
		session, err := s.AndroidScanSessionByID(ctx, admission.RequestID)
		if err != nil {
			return OperationAction{}, err
		}
		if session != nil {
			mapped := map[string]string{"COMPLETED": "success", "COMPLETED_TRUNCATED": "success", "FAILED": "failure", "FAILED_INTEGRITY": "failure", "CANCELLED": "cancelled", "INTERRUPTED": "interrupted"}[session.Status]
			if mapped != "" && !terminalAutomationStates[admission.State] {
				updated, err := s.AdvanceAutomation(ctx, opID, mapped, "reconciled_from_scan", "terminal scan truth recovered after reconnect")
				if err != nil {
					return OperationAction{}, err
				}
				return OperationAction{Allowed: true, Reason: "reconciled_terminal_truth", OperationID: opID, RequestID: updated.RequestID, State: updated.State}, nil
			}
		}
	}
	if !terminalAutomationStates[admission.State] && admission.State != "recovery_required" {
		updated, err := s.AdvanceAutomation(ctx, opID, "recovery_required", "reconcile_required", "definitive terminal truth unavailable after reconnect")
		if err != nil {
			return OperationAction{}, err
		}
		admission = updated
	}
	return OperationAction{Allowed: true, Reason: "recovery_required", OperationID: opID, RequestID: admission.RequestID, State: admission.State}, nil
}

func (s *SQLiteStore) RetryAutomation(ctx context.Context, opID, newRequestID, source string, newGeneration int64) (DurableAdmission, error) {
	previous, err := s.ObserveAutomation(ctx, opID)
	if err != nil {
		return DurableAdmission{}, err
	}
	if previous.Kind != "scan" {
		return DurableAdmission{}, errors.New("automation_retry_kind_unsupported")
	}
	retry, _, _ := automationGates(previous.Kind, previous.State, previous.RecoveryRequired)
	if retry != "allowed" {
		return DurableAdmission{}, errors.New("automation_retry_gate_blocked")
	}
	if strings.TrimSpace(newRequestID) == "" || newRequestID == previous.RequestID {
		return DurableAdmission{}, errors.New("automation_retry_requires_new_request_id")
	}
	if newGeneration <= previous.Generation {
		newGeneration = previous.Generation + 1
	}
	return s.AdmitAutomation(ctx, newRequestID, source, previous.Kind, newGeneration, previous.EvidenceCorrelation)
}
