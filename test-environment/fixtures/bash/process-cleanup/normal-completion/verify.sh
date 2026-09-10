#!/usr/bin/env bash
set -euo pipefail

completed_task_id=$1
state_dir="/tmp/bookwright-process-cleanup/${completed_task_id}"
pid=$(< "$state_dir/child.pid")
result=gone

if kill -0 "$pid" 2>/dev/null; then
    result=alive
    kill -KILL "$pid"
fi

rm -rf -- "$state_dir"
printf 'semaphore-resistant-process-%s\n' "$result"
