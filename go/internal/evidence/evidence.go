package evidence

type Generation struct {
	ID         string `json:"id"`
	Subject    string `json:"subject"`
	Generation int64  `json:"generation"`
	Hash       string `json:"hash"`
}

func (g Generation) Valid() bool {
	return g.ID != "" && g.Subject != "" && g.Generation > 0 && g.Hash != ""
}
