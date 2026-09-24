package diagnostics

type Problem struct {
	ID               string `json:"id"`
	Reason           string `json:"reason"`
	RecoveryRequired bool   `json:"recoveryRequired"`
}

func RecoveryProblem(id, reason string) Problem {
	return Problem{ID: id, Reason: reason, RecoveryRequired: true}
}
