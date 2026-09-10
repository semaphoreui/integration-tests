#!/usr/bin/env bash
set -euo pipefail

completed_task_id=$1
state_dir="/tmp/bookwright-process-cleanup/${completed_task_id}"
process_file="$state_dir/process"
result=missing

# file exists and not empty
if [[ -s "$process_file" ]]; then
    read -r pid < "$process_file"
    state=$(awk '{print $3}' "/proc/$pid/stat" 2>/dev/null || true)

    if [[ -n "$state" && "$state" != "Z" ]]; then
        result=alive
        # Always clean up a surviving fixture before reporting the regression.
        kill -KILL "$pid" 2>/dev/null || true
    else
        result=gone
    fi
fi

rm -rf -- "$state_dir"
printf 'semaphore-process-cleanup-child-%s\n' "$result"
