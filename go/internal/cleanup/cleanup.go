package cleanup

type Manifest struct {
	ID            string `json:"id"`
	OperationID   string `json:"operationId"`
	Hash          string `json:"hash"`
	ImmutableJSON string `json:"immutableJson"`
}

func (m Manifest) ValidImmutableEvidence() bool {
	return m.ID != "" && m.OperationID != "" && m.Hash != "" && m.ImmutableJSON != ""
}
