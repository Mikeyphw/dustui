#!/usr/bin/env sh
set -eu
PYTHONPATH=src exec python3 -m dust_tui "$@"
