#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

usage() {
  echo "Usage: $0 {up|test|clean} {external|core-sqlite-local|core-postgres-local}"
  exit 1
}

if [ "$#" -ne 2 ]; then
  echo "Error: exactly 2 arguments are required"
  usage
fi

ACTION=$1
PROFILE=$2

case "$ACTION" in
up | test | clean)
  ;;
*)
  echo "Error: invalid action '$ACTION'"
  echo "Allowed values: up, test, clean"
  exit 1
  ;;
esac

case "$PROFILE" in
external | core-sqlite-local | core-postgres-local)
  ;;
*)
  echo "Error: invalid profile '$PROFILE'"
  echo "Allowed values: external, core-sqlite-local, core-postgres-local"
  exit 1
  ;;
esac

: "${API_BASE_URL:?Set API_BASE_URL to the external Semaphore URL ending with /api/}"
: "${API_USERNAME:?Set API_USERNAME for the external Semaphore instance}"
: "${API_PASSWORD:?Set API_PASSWORD through the environment or a CI secret}"

case "$ACTION" in
up)
  echo "Running up for $PROFILE..."
  "$repository_dir/test-environment/profile" up "$PROFILE"
  ;;

test)
  echo "Running tests for $PROFILE..."
  "$repository_dir/test-environment/profile" test "$PROFILE"
  ;;

clean)
  echo "Cleaning $PROFILE..."
  "$repository_dir/test-environment/profile" clean "$PROFILE" --yes
  ;;
esac
