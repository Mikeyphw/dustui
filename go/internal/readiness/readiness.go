package readiness

import "github.com/mikeyphw/dustui/go/internal/version"

type Report struct {
	Name             string `json:"name"`
	MigrationPhase   string `json:"migrationPhase"`
	Protocol         string `json:"protocol"`
	ProductionEngine bool   `json:"productionEngine"`
	SQLiteAuthority  bool   `json:"sqliteAuthority"`
	MutationAuthority bool  `json:"mutationAuthority"`
	Message          string `json:"message"`
}

func Current() Report {
	return Report{
		Name:              version.Name,
		MigrationPhase:    version.MigrationPhase,
		Protocol:          version.Protocol,
		ProductionEngine:  false,
		SQLiteAuthority:   false,
		MutationAuthority: false,
		Message:           "OV01 Go skeleton is present for Devtool/EXO targeting only; no production authority has moved.",
	}
}
