package query

type PageRequest struct {
	Generation int64  `json:"generation"`
	Token      string `json:"token"`
	Limit      int    `json:"limit"`
}

func (r PageRequest) BoundedLimit(max int) int {
	if r.Limit <= 0 || r.Limit > max {
		return max
	}
	return r.Limit
}
