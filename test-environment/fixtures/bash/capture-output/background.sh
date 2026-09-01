#!/usr/bin/env bash

# Keep inherited stdout/stderr open to verify that output draining is bounded.
sleep 10 &
# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'stdout'
printf 'stderr' >&2
