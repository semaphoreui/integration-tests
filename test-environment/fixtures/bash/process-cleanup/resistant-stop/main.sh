#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
state_dir="/tmp/bookwright-process-cleanup/${SEMAPHORE_TASK_DETAILS_ID}"

mkdir -p -- "$state_dir"
printf '%s\n' "$$" > "$state_dir/main.pid"

handle_term() {
    printf 'semaphore-resistant-stop-main-term\n'
}
trap handle_term TERM

bash "$script_dir/child.sh" "$state_dir" &
child_pid=$!

for _ in {1..10}; do
    [[ -s "$state_dir/sleep.pid" ]] && break
    sleep 0.1
done

if [[ ! -s "$state_dir/sleep.pid" ]]; then
    echo "Child did not become ready" >&2
    exit 1
fi

printf 'semaphore-resistant-stop-ready\n'
while true; do
    wait "$child_pid" || true
done
