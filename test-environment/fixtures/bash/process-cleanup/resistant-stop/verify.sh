#!/usr/bin/env bash
set -euo pipefail

completed_task_id=$1
state_dir="/tmp/bookwright-process-cleanup/${completed_task_id}"
result=gone

for pid_file in main.pid child.pid sleep.pid; do
    pid=$(< "$state_dir/$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
        result=alive
        kill -KILL "$pid"
    fi
done

rm -rf -- "$state_dir"
printf 'semaphore-resistant-stop-processes-%s\n' "$result"
