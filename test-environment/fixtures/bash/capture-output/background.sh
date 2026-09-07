#!/usr/bin/env bash

# Keep inherited stdout/stderr open to verify that output draining is bounded.
sleep 60 &
# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'semaphore-shell-stdout-marker'
printf 'semaphore-shell-stderr-marker' >&2
