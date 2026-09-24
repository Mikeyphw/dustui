# DustUI Android Root Scan Diagnostics

This repair makes root scanning and module analysis fail loudly and explainably instead of presenting inaccessible storage as a valid zero-file result.

## Root service identity

The privileged service reports and the UI retains:

- UID, GID, and PID;
- detected KernelSU, Magisk, APatch, or generic-su backend;
- packaged Go root-worker version;
- SELinux enforcing state and process context;
- mount namespace identity.

The service is considered ready only when it is running as UID 0 and the packaged Go root worker is available. A normal app shell with a separate interactive `su` grant is not reported as ready.

## Scan-root preflight

Every scan is checked twice, once by the foreground pipeline and again by the root service immediately before Go root-worker execution. The preflight verifies:

- absolute normalized path;
- successful `lstat`;
- directory type;
- non-symlink root;
- successful directory open in the root-service process;
- canonical path;
- mount point, filesystem, and options;
- whether at least one direct child is visible.

The Scan screen also provides **Check access** so a custom path can be diagnosed without starting a scan.

## Fatal versus recoverable failures

Failure to inspect or open the requested scan root is fatal. Nested unreadable directories remain recoverable warnings so the rest of the tree can still be analyzed.

Native events retain diagnostic code, phase, path, errno, and message. A root scanner that exits without a terminal event is treated as failed, never silently promoted to completed.

## Completion evidence

Each scan retains a bounded diagnostic record containing:

- emitted files, directories, and total entries;
- warning details;
- result-limit truncation;
- depth-limited directory count;
- filesystem-boundary skip count;
- root-open success;
- root identity and preflight context.

A completed zero-file scan explains whether the root was empty, directories-only mode was selected, filters excluded the files, depth was zero, or no records were emitted despite visible children.

## Module analyzer

Module values are derived from the current scan index. The Modules page now distinguishes:

- current scan does not include `/data/adb/modules`;
- root service cannot open the module directory;
- module scan failed;
- results were truncated;
- no modules were indexed despite successful access;
- module data is ready.

The page offers a dedicated `/data/adb/modules` scan with hidden files, deeper traversal, and a larger result budget.
