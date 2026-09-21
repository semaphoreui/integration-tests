#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
state_dir="/tmp/bookwright-process-cleanup/${SEMAPHORE_TASK_DETAILS_ID}"

mkdir -p -- "$state_dir"
setsid bash "$script_dir/child.sh" "$state_dir" &

for _ in {1..10}; do
    [[ -s "$state_dir/child.pid" ]] && break
    sleep 0.1
done

if [[ ! -s "$state_dir/child.pid" ]]; then
    echo "Child did not become ready" >&2
    exit 1
fi

printf 'semaphore-escaped-process-ready\n'
