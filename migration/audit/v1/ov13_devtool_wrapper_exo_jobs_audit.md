# OV13 wrapper/EXO jobs audit

- Replaces the prior compatibility stance that target-local jobs were unsupported.
- Adds only supported Devtool schema documented by `docs/EXO_TARGET_LOCAL_JOBS.md` in the uploaded Devtool snapshot.
- Keeps wrapper launchers thin and Devtool-owned.
- Keeps Gate D mandatory and does not hide existing Android unit/lint failures.
- Provides static and dynamic validation through `scripts/validate_ov13_devtool_wrapper_exo_jobs.py`.
