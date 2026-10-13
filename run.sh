#!/bin/sh

set -eu

IMAGE="lowswoo/semaphore-test-container:1.0-arm"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

env_args=
for name in API_BASE_URL API_USERNAME API_PASSWORD UI_BASE_URL TEST_REPOSITORY FIXTURE_GIT_PORT \
  APP_IMAGE APP_SOURCE APP_BRANCH APP_REPOSITORY APP_PR APP_SHA GH_TOKEN GITHUB_TOKEN; do
  # `-e NAME` without a value forwards the host value, so secrets never appear in argv.
  eval "is_set=\${$name+x}"
  [ -z "$is_set" ] || env_args="$env_args -e $name"
done

tty_args=
[ ! -t 0 ] || [ ! -t 1 ] || tty_args=-it

docker pull $IMAGE

exec docker run --rm \
  --add-host host.docker.internal:host-gateway \
  $tty_args \
  $env_args \
  -v "$SCRIPT_DIR:$SCRIPT_DIR" \
  -w "$SCRIPT_DIR" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$IMAGE" \
  "$SCRIPT_DIR/scripts/runner-entrypoint.sh" \
  "$SCRIPT_DIR/test-environment/profile" "$@"
