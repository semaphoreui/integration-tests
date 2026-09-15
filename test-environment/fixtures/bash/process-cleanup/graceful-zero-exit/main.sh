#!/usr/bin/env bash
set -euo pipefail

handle_term() {
	printf 'semaphore-graceful-zero-exit-term\n'
	exit 0
}
trap handle_term TERM

printf 'semaphore-graceful-zero-exit-ready\n'
while true; do
	sleep 1
done
