#!/usr/bin/env sh
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_dir/go"
install_dir=${DUSTUI_INSTALL_DIR:-"$HOME/.local/bin"}
mkdir -p "$install_dir"
go build -o "$install_dir/dustui-go" ./cmd/dustui-go
go build -o "$install_dir/dust-tui" ./cmd/dust-tui
go build -o "$install_dir/dust-space" ./cmd/dust-space
printf '\nInstalled Go host commands in %s\n' "$install_dir"
printf 'Run: dust-tui [PATH]  or  dustui-go help\n'
