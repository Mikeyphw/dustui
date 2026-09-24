package scan

type AuthorityDecision struct {
	CanAnalyze               bool     `json:"canAnalyze"`
	CanCompare               bool     `json:"canCompare"`
	CanDriveDeletion         bool     `json:"canDriveDeletion"`
	CanDriveDuplicateHashing bool     `json:"canDriveDuplicateHashing"`
	CanDriveResidue          bool     `json:"canDriveResidue"`
	CanExportCompleteData    bool     `json:"canExportCompleteData"`
	CanShowAsFresh           bool     `json:"canShowAsFresh"`
	Reasons                  []string `json:"reasons"`
}

func DecideAuthority(current Session, previous *Session) AuthorityDecision {
	decision := AuthorityDecision{
		CanAnalyze:               current.CanAnalyze(),
		CanDriveDeletion:         current.CanDriveDeletion(),
		CanDriveDuplicateHashing: current.CanDriveDuplicateHashing(),
		CanDriveResidue:          current.CanDriveResidue(),
		CanExportCompleteData:    current.CanExportCompleteData(),
		CanShowAsFresh:           current.CanShowAsFresh(),
		Reasons:                  []string{},
	}
	if previous != nil {
		compat := current.CompatibilityWith(*previous)
		decision.CanCompare = compat.Comparable
		decision.Reasons = append(decision.Reasons, compat.Reasons...)
	} else {
		decision.CanCompare = false
		decision.Reasons = append(decision.Reasons, "no_previous_scan")
	}
	if current.Truncated {
		decision.Reasons = append(decision.Reasons, "truncated")
	}
	if current.MalformedRecordCount > 0 {
		decision.Reasons = append(decision.Reasons, "malformed_records")
	}
	if current.StaleAfterCleanup {
		decision.Reasons = append(decision.Reasons, "stale_after_cleanup")
	}
	if current.SourceCompleteness != SourceComplete {
		decision.Reasons = append(decision.Reasons, "source_incomplete")
	}
	return decision
}
