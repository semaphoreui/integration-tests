#!/bin/sh

set -e


script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

cd "$repository_dir"

external() {
  docker compose run --remove-orphans test-runner ./scripts/run-external-tests.sh --no-daemon
}

main() {
  command="$1"
  shift || true

  case "$command" in
    external)
    external
  ;;
    test)
    test
  ;;
    curl)
    curl
  ;;
  esac
}

main "$@"
