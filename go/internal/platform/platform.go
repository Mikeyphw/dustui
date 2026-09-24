package platform

type Runtime struct {
	OS      string `json:"os"`
	Arch    string `json:"arch"`
	Android bool   `json:"android"`
}

func ARM64FirstProductionABI() string { return "arm64-v8a" }
