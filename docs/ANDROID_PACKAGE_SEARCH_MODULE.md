# DustUI Package Intelligence, Root Search, and Module Analyzer

This slice extends the root explorer with three analysis workbenches built from the existing scan index.

## Package intelligence

Package profiles aggregate every app-owned path resolved from `/data/user`, `/data/data`, `/data/app`, and external Android storage. Each profile reports private data, cache, APK/native code, databases, preferences, external data, OBB/media, logs, duplicate candidates, largest package-owned paths, installed/orphaned state, and large-cache hints.

## Root file search

The search panel queries the persisted root scan index instead of starting another privileged traversal. Supported query hints include ordinary names/paths, `*.ext`, `ext:apk`, `package:com.example.app`, and size filters such as `>100m`. Results open the explorer at the matching parent and retain root-bounded navigation.

## Module analyzer

The module analyzer summarizes `/data/adb/modules` footprints, detects module metadata and boot scripts, identifies WebUI folders, flags disabled/update-pending modules, lists overlay targets, and reports overlay conflicts where multiple modules target the same system path.

No arbitrary root shell endpoint is added. Cleanup remains gated by the Phase 4 review/quarantine workflow.

## Module-source diagnostics

Module analysis remains index-backed, but the UI no longer renders empty measurements without context. It identifies a wrong current scan root, inaccessible `/data/adb/modules`, failed or truncated module scans, and a genuine no-modules result separately. A dedicated module-directory scan action is available from the workbench.
