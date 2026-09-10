#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
state_dir="/tmp/bookwright-process-cleanup/${SEMAPHORE_TASK_DETAILS_ID}"

mkdir -p -- "$state_dir"

bash "$script_dir/resistant-process.sh" "$state_dir" &

for _ in {1..10}; do
    [[ -s "$state_dir/child.pid" ]] && break
    sleep 0.1
done

if [[ ! -s "$state_dir/child.pid" ]]; then
    echo "Background child did not record its identity" >&2
    exit 1
fi

# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'semaphore-process-cleanup-stdout-marker'
printf 'semaphore-process-cleanup-stderr-marker' >&2
