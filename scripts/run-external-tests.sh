#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

: "${API_BASE_URL:?Set API_BASE_URL to the external Semaphore URL ending with /api/}"
: "${API_USERNAME:?Set API_USERNAME for the external Semaphore instance}"
: "${API_PASSWORD:?Set API_PASSWORD through the environment or a CI secret}"

case "$API_BASE_URL" in
  http://*/api/|https://*/api/) ;;
  *)
    printf '%s\n' 'run-external-tests: API_BASE_URL must use http(s) and end with /api/' >&2
    exit 2
    ;;
esac


cd "$repository_dir"
exec ./gradlew externalTest "$@"
