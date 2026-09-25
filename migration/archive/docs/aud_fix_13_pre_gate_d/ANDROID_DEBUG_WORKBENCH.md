# Android Debug Workbench — historical predecessor

This file name is retained only for historical links. The Debug Workbench is **not** a current DustUI product or acceptance surface.

AUD-FIX-11 supersedes it with **System → Diagnostics** (`system/diagnostics`) backed by the durable Go ProblemGraph. Diagnostics leads with user-comprehensible root causes, evidence health/freshness, recovery state, guidance, remediation, and safe support export. Structured events, probes, protocol/runtime details, and the opt-in debug overlay remain available only under **Advanced diagnostics**.

Current executable acceptance is owned by `scripts/validate_audfix11_contract.py`, `android/scripts/validate-debug-workbench-contract.py` (successor-aware despite the historical filename), Android unit tests, and `dustui_android#aud_fix_11`. Historical Tools/Debug destination tokens are not requirements.
