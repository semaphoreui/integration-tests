#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
state_dir="/tmp/bookwright-process-cleanup/${SEMAPHORE_TASK_DETAILS_ID}"

rm -rf -- "$state_dir"
mkdir -p -- "$state_dir"

bash "$script_dir/resistant-process.sh" "$state_dir" &
child_pid=$!

for _ in $(seq 1 100); do
    if [[ -s "$state_dir/process" ]]; then
        break
    fi
    if ! kill -0 "$child_pid" 2>/dev/null; then
        echo "Background child exited before recording its identity" >&2
        exit 1
    fi
    sleep 0.01
done

if [[ ! -s "$state_dir/process" ]]; then
    echo "Background child did not record its identity" >&2
    exit 1
fi

# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'semaphore-process-cleanup-stdout-marker'
printf 'semaphore-process-cleanup-stderr-marker' >&2
