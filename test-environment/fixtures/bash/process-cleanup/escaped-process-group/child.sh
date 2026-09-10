#!/usr/bin/env bash
set -euo pipefail

state_dir=$1

trap '' TERM HUP
printf '%s\n' "$$" > "$state_dir/child.pid"
exec sleep 120
