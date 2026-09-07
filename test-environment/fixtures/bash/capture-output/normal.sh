#!/usr/bin/env bash

# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'semaphore-shell-stdout-marker'
printf 'semaphore-shell-stderr-marker' >&2
