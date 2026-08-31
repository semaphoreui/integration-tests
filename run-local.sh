#!/usr/bin/env bash

set -euo pipefail

# Port 3000 may already be used by a local Semaphore service.
port="${PORT:-3001}"
semaphore_image="${SEMAPHORE_IMAGE:-semaphoreui/semaphore:local}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# By default, use an integration-test-fixtures checkout next to semaphore-test-api.
fixtures_host_path="${SEMAPHORE_FIXTURES_DIR:-$script_dir/../integration-test-fixtures}"
fixtures_mount_path="/integration-test-fixtures"
compose_file="$script_dir/test-environment/compose.yml"
base_url="http://localhost:$port"

compose() {
  SEMAPHORE_IMAGE="$semaphore_image" \
    docker compose \
    --file "$compose_file" \
    --file - \
    "$@" <<-YAML
			services:
			  semaphore:
			    ports: !override
			      - "127.0.0.1:$port:3000"
			    volumes:
			      - "$fixtures_host_path:$fixtures_mount_path:ro"
		YAML
}

cleanup() {
  compose down --remove-orphans
}

prepare_semaphore_stand() {
  compose up --detach

  local args=(
    --fail
    --silent
    --show-error
    --retry 60
    --retry-delay 1
    --retry-connrefused
    --retry-all-errors
    --retry-max-time 60
    "$base_url/api/ping"
  )
  local response

  if ! response="$(curl "${args[@]}")"; then
    echo "Semaphore did not become ready at $base_url" >&2
    compose logs --tail 100 semaphore >&2 || true
    return 1
  fi

  if [[ "$response" != "pong" ]]; then
    echo "Unexpected readiness response: $response" >&2
    return 1
  fi
}

run_integration_tests() {
  docker run --rm \
    --network host \
    --user "$(id -u):$(id -g)" \
    -e GRADLE_USER_HOME=/tmp/gradle \
    -v "$script_dir:/workspace" \
    -w /workspace \
    eclipse-temurin:21-jdk \
    ./gradlew apiTest \
      -DSTAND=semaphore \
      -DSEMAPHORE_PROFILE=core-sqlite-local \
      -Dapi.base.url="$base_url/api/" \
      -Dui.base.url="$base_url" \
      -Dsemaphore.repository.url="$fixtures_mount_path" \
      "$@"
}

trap cleanup EXIT
prepare_semaphore_stand
run_integration_tests "$@"
