#!/usr/bin/env sh
set -eu
python3 -m pip install --user .
printf '\nInstalled. Run: dust-tui\n'
printf 'For the preferred backend, install dust with: cargo install du-dust\n'
