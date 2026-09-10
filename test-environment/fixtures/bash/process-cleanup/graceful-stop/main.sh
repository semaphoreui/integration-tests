#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
state_dir="/tmp/bookwright-process-cleanup/${SEMAPHORE_TASK_DETAILS_ID}"

mkdir -p -- "$state_dir"

handle_term() {
    printf 'semaphore-process-cleanup-main-term\n'

    # Give the child time to finish its TERM handler before the main process exits.
    for _ in {1..10}; do
        [[ -e "$state_dir/child-term" ]] && break
        sleep 0.1
    done
    # Preserve the conventional exit status for termination by SIGTERM.
    exit 143
}
trap handle_term TERM

bash "$script_dir/child.sh" "$state_dir" &
child_pid=$!

for _ in {1..10}; do
    [[ -s "$state_dir/children.pids" ]] && break
    sleep 0.1
done

if [[ ! -s "$state_dir/children.pids" ]]; then
    echo "Child did not record its PID" >&2
    exit 1
fi

printf 'semaphore-process-cleanup-term-ready\n'
wait "$child_pid"
