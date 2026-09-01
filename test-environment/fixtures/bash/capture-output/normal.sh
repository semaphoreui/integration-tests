#!/usr/bin/env bash

# Keep writes short and newline-free so output may remain buffered until EOF.
printf 'stdout'
printf 'stderr' >&2
