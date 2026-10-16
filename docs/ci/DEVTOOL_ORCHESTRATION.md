# dustui: Devtool DAG/EXO/Actions

**Snapshot:** 2026-10-15. Source-bound, first install. No toolchain download during overlay application.

This overlay retains existing project targets, jobs and any release/device gates. It adds `./devtoolw ci-preflight` for a read-only Devtool target-local source job, and `./devtoolw ci-local` for the dependency DAG. `devtool -r "$PWD" workflows --json` inspects job/workflow discovery and EXO execution.

The GitHub workflow is separate: run `devtool -r "$PWD" actions plan WORKFLOW_FILENAME --ref BRANCH --json`; after **committing and pushing** the workflow/source changes, run `devtool -r "$PWD" actions run WORKFLOW_FILENAME --ref BRANCH --yes --json`, then `actions runs/view/watch/artifacts`. Remote success is **not** package signing or device qualification.

Repository-specific boundaries:
Go authority and Android qualification DAGs are preserved. Hosted Go builds/Android debug outputs are candidates, not Gate E.
