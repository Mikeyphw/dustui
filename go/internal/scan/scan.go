package scan

type Session struct {
	ID         string `json:"id"`
	Generation int64  `json:"generation"`
	Integrity  string `json:"integrity"`
}

func (s Session) CanDriveDeletion() bool { return false }
