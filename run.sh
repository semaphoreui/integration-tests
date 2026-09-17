#!/bin/sh

set -eu

IMAGE="lowswoo/semaphore-test-container:1.0"
SCRIPT="/workspace/test-environment/profile"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

docker run --rm \
  -v "$SCRIPT_DIR:$SCRIPT_DIR" \
  -w "$SCRIPT_DIR" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$IMAGE" \
  "$SCRIPT_DIR/test-environment/profile" "$@"
