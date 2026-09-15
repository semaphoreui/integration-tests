#!/usr/bin/env bash
set -euo pipefail

state_dir=$1

handle_term() {
    printf 'semaphore-process-cleanup-child-term\n'
    : > "$state_dir/child-term"
    exit 0
}
trap handle_term TERM

sleep 120 &
sleep_pid=$!

printf '%s\n%s\n' "$$" "$sleep_pid" > "$state_dir/children.pids"

wait "$sleep_pid"
