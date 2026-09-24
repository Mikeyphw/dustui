package state

import (
	"fmt"

	"github.com/mikeyphw/dustui/go/internal/protocol"
)

type Migration struct {
	FromVersion int
	ToVersion   int
	Statements  []string
}

type MigrationRunner struct {
	Version int
	Journal []string
}

func OV02Migration() Migration {
	return Migration{FromVersion: 0, ToVersion: SchemaVersion, Statements: CreateStatements()}
}

func (runner MigrationRunner) Apply(m Migration, failAtStatement int) (MigrationRunner, error) {
	if runner.Version != m.FromVersion {
		return runner, protocol.NewError(protocol.ReasonSchemaMigrationFailed, fmt.Sprintf("unexpected source version %d", runner.Version))
	}
	working := runner
	working.Journal = append(append([]string{}, runner.Journal...), "BEGIN")
	for index, statement := range m.Statements {
		if failAtStatement == index {
			rolledBack := runner
			rolledBack.Journal = append(append([]string{}, runner.Journal...), "ROLLBACK")
			return rolledBack, protocol.NewError(protocol.ReasonSchemaMigrationFailed, "simulated failure rolled back migration")
		}
		working.Journal = append(working.Journal, statement)
	}
	working.Version = m.ToVersion
	working.Journal = append(working.Journal, "COMMIT")
	return working, nil
}
