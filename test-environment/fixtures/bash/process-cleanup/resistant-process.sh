#!/usr/bin/env bash
set -euo pipefail

state_dir=$1

# Ignore graceful termination so only process-group cleanup can remove this process.
#
# https://man7.org/linux/man-pages/man7/signal.7.html
# > ... the dispositions of ignored signals are left unchanged.
# https://pubs.opengroup.org/onlinepubs/9799919799/functions/exec.html
# > Signals set to SIG_IGN remain ignored, while signals with caught handlers are reset to their default action.
trap '' TERM HUP

# Publish atomically so the verifier cannot read a partially written PID.
printf '%s\n' "$$" > "$state_dir/process.tmp"
mv -- "$state_dir/process.tmp" "$state_dir/process"

# Keep the task's inherited stdout and stderr open after its main process exits.
exec sleep 120
