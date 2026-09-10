#!/usr/bin/env bash
set -euo pipefail

state_dir=$1

handle_term() {
    printf 'semaphore-resistant-stop-child-term\n'
}
trap handle_term TERM

printf '%s\n' "$$" > "$state_dir/child.pid"

# Keep this script alive by starting a new sleep after SIGTERM stops the current one.
while true; do
    sleep 120 &
    sleep_pid=$!
    printf '%s\n' "$sleep_pid" > "$state_dir/sleep.pid"
    wait "$sleep_pid" || true
done
