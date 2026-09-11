#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

: "${API_BASE_URL:?Set API_BASE_URL to the external Semaphore URL ending with /api/}"
: "${API_USERNAME:?Set API_USERNAME for the external Semaphore instance}"
: "${API_PASSWORD:?Set API_PASSWORD through the environment or a secret store}"
: "${EXTERNAL_MUTATIONS_ALLOWED:?Set EXTERNAL_MUTATIONS_ALLOWED=true to allow task launches}"
: "${EXTERNAL_MANAGED_PROJECT_ID:?Set the preconfigured Semaphore project ID}"
: "${EXTERNAL_MANAGED_TEMPLATE_A_ID:?Set the first preconfigured task-template ID}"
: "${EXTERNAL_MANAGED_TEMPLATE_B_ID:?Set the second preconfigured task-template ID}"
: "${EXTERNAL_MANAGED_MARKER_A:?Set the expected output marker for the first template}"
: "${EXTERNAL_MANAGED_MARKER_B:?Set the expected output marker for the second template}"

case "$API_BASE_URL" in
  http://*/api/|https://*/api/) ;;
  *)
    printf '%s\n' 'run-external-managed-tests: API_BASE_URL must use http(s) and end with /api/' >&2
    exit 2
    ;;
esac

if [ "$EXTERNAL_MUTATIONS_ALLOWED" != true ]; then
  printf '%s\n' 'run-external-managed-tests: EXTERNAL_MUTATIONS_ALLOWED must be exactly true' >&2
  exit 2
fi

for id in "$EXTERNAL_MANAGED_PROJECT_ID" "$EXTERNAL_MANAGED_TEMPLATE_A_ID" "$EXTERNAL_MANAGED_TEMPLATE_B_ID"; do
  case "$id" in
    ''|*[!0-9]*|0)
      printf '%s\n' 'run-external-managed-tests: project and template IDs must be positive integers' >&2
      exit 2
      ;;
  esac
done

if [ "$EXTERNAL_MANAGED_TEMPLATE_A_ID" = "$EXTERNAL_MANAGED_TEMPLATE_B_ID" ]; then
  printf '%s\n' 'run-external-managed-tests: template IDs must be different' >&2
  exit 2
fi

if [ "$EXTERNAL_MANAGED_MARKER_A" = "$EXTERNAL_MANAGED_MARKER_B" ]; then
  printf '%s\n' 'run-external-managed-tests: output markers must be different' >&2
  exit 2
fi

cd "$repository_dir"
exec ./gradlew externalManagedTest "$@"
