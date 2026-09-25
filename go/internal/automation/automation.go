package automation

// Package automation exposes the product contract for the durable automation/activity
// authority. The implementation lives in internal/state and is persisted in Go-owned
// SQLite. This package intentionally contains no in-memory controller: a second runtime
// authority would allow adapters to disagree after process death.

const Protocol = "dustui.automation.activity.v2"

type ContractReport struct {
	Protocol                    string   `json:"protocol"`
	Phase                       string   `json:"phase"`
	GoAuthority                 bool     `json:"goAuthority"`
	Persistence                 string   `json:"persistence"`
	AcceptedIsSuccess           bool     `json:"acceptedIsSuccess"`
	QueuedIsSuccess             bool     `json:"queuedIsSuccess"`
	RunningIsSuccess            bool     `json:"runningIsSuccess"`
	Adapters                    []string `json:"adapters"`
	OperationKinds              []string `json:"operationKinds"`
	AllowedAutomationOperations []string `json:"allowedAutomationOperations"`
	NotificationGuards          []string `json:"notificationGuards"`
	ActivitySurfaces            []string `json:"activitySurfaces"`
	SafetyRules                 []string `json:"safetyRules"`
}

func Contract() ContractReport {
	return ContractReport{
		Protocol:                    Protocol,
		Phase:                       "AUD-FIX-04",
		GoAuthority:                 true,
		Persistence:                 "go-owned-sqlite",
		AcceptedIsSuccess:           false,
		QueuedIsSuccess:             false,
		RunningIsSuccess:            false,
		Adapters:                    []string{"ui", "workmanager", "tasker", "notification", "recovery"},
		OperationKinds:              []string{"scan", "analysis", "status", "inspect"},
		AllowedAutomationOperations: []string{"scan", "analysis", "status", "cancel", "inspect", "reconcile", "retry"},
		NotificationGuards:          []string{"nonce", "expiry", "single_use", "operation_binding", "replay_rejection"},
		ActivitySurfaces:            []string{"active", "history", "run_details", "progress", "result", "verification", "recovery_required", "retry_gate", "cancel_gate", "reconcile_gate", "operation_evidence_correlation"},
		SafetyRules:                 []string{"accepted_not_success", "queued_not_success", "running_not_success", "thin_adapters", "single_durable_sqlite_authority", "process_death_does_not_orphan_job", "cancellation_is_operation_transition", "non_destructive_automation_only"},
	}
}
