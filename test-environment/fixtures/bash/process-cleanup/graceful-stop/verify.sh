#!/usr/bin/env bash
set -euo pipefail

completed_task_id=$1
state_dir="/tmp/bookwright-process-cleanup/${completed_task_id}"
result=gone

while read -r pid; do
    if kill -0 "$pid" 2>/dev/null; then
        result=alive
        kill -KILL "$pid"
    fi
done < "$state_dir/children.pids"

rm -rf -- "$state_dir"
printf 'semaphore-graceful-stop-descendants-%s\n' "$result"
