#!/bin/sh

# Runs test-environment/profile inside the runner container.
#
#   run.sh <profile action> [args]        one-shot: a throwaway runner container per call
#   run.sh start [<profile action> ...]   starts the shared runner container of a multi-task
#                                         pipeline, then runs the profile action in it, if given
#   run.sh exec <profile action> [args]   runs a profile action in the shared runner container
#   run.sh publish <profile>=<dir> ...    runs scripts/publish-external-results.sh in the shared
#                                         runner container, then stops it
#   run.sh stop                           stops and removes the shared runner container
#
# Every Semaphore template clones the repository into its own directory, so the tasks of one
# pipeline cannot share build/ through their checkouts. The shared runner container mounts the
# checkout of the task that started it, and the later tasks only run commands in it, so up, test,
# cleanup and publication see the same generated fixtures, application image state and Allure
# results. All tasks of the pipeline must therefore run on the same Docker host.

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# The whole task environment reaches the runner container, except the variables that describe
# this host and would break the container: its paths, user, locale, Docker client settings, and
# the settings of this script itself.
host_only_name() {
  case "$1" in
  PATH | HOME | PWD | OLDPWD | SHLVL | _ | SHELL | USER | LOGNAME | HOSTNAME | TERM | TMPDIR | \
    MAIL | DISPLAY | LANG | LANGUAGE | LC_* | TMP | TEMP | JAVA_HOME | GRADLE_USER_HOME | DOCKER_* | \
    SSH_* | XDG_* | RUNNER_IMAGE | RUNNER_CONTAINER) return 0 ;;
  *) return 1 ;;
  esac
}

# Semaphore passes variable-type secrets, extra variables and survey variables to a shell task
# as NAME=value arguments, the secrets before the template arguments. Each one becomes an
# environment variable, as an environment-type secret would, so it reaches the container and
# neither the mode check below nor Gradle. Values are never printed. Arguments named after a
# profile (the <profile>=<dir> pairs of publish) are kept.
argument_count=$#
while [ "$argument_count" -gt 0 ]; do
  argument=$1
  shift
  argument_count=$((argument_count - 1))
  name=${argument%%=*}
  case "$argument" in
  [A-Za-z_]*=*) ;;
  *) set -- "$@" "$argument"; continue ;;
  esac
  case "$name" in
  *[!A-Za-z0-9_]*) set -- "$@" "$argument"; continue ;;
  esac
  if [ -f "$SCRIPT_DIR/test-environment/profiles/$name/profile.yaml" ]; then
    set -- "$@" "$argument"
    continue
  fi
  if host_only_name "$name" && [ "$name" != RUNNER_IMAGE ] && [ "$name" != RUNNER_CONTAINER ]; then
    printf 'run.sh: skipping task argument %s: it describes the host\n' "$name"
    continue
  fi
  export "$argument"
  printf 'run.sh: %s taken from the task arguments\n' "$name"
done

# RUNNER_IMAGE overrides the runner image, for example with one rebuilt from the current Dockerfile.
IMAGE=${RUNNER_IMAGE:-lowswoo/semaphore-test-container:1.0-arm}

# The tasks of one Semaphore workflow run share SEMAPHORE_WORKFLOW_RUN_ID; tasks started outside
# a workflow find the runner by a fixed name, which works because one stand runs on a host at a
# time anyway. RUNNER_CONTAINER overrides both.
container=${RUNNER_CONTAINER:-semaphore-tests${SEMAPHORE_WORKFLOW_RUN_ID:+-$SEMAPHORE_WORKFLOW_RUN_ID}}
container_label=io.semaphoreui.integration-tests.runner=shared

# `-e NAME` without a value forwards the host value, so secrets never appear in argv. awk lists
# the names exactly, even when a value spans several lines.
env_args=
for name in $(awk 'BEGIN { for (name in ENVIRON) print name }' |
  grep -E '^[A-Za-z_][A-Za-z0-9_]*$' | sort); do
  host_only_name "$name" || env_args="$env_args -e $name"
done

tty_args=
[ ! -t 0 ] || [ ! -t 1 ] || tty_args=-it

fail() {
  printf 'run.sh: %s\n' "$1" >&2
  exit 1
}

container_running() {
  [ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null)" = true ]
}

# Runs a command in the shared runner container, in the checkout it was started from. The
# environment is forwarded on every call, because each task of the pipeline has its own.
in_container() {
  container_running || fail "runner container $container is not running; start it with: run.sh start"
  docker exec $tty_args $env_args "$container" "$@"
}

# Takes a container name or id, so start can also remove the runners of earlier pipelines.
stop_container() {
  if ! docker inspect "$1" >/dev/null 2>&1; then
    printf 'Runner container %s is not running\n' "$1"
    return 0
  fi
  # The runner works as root; hand its output back to the checkout owner, or Semaphore cannot
  # remove the checkout when a pull fails and it clones the repository again.
  if [ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null)" = true ]; then
    docker exec "$1" chown -R "$(id -u):$(id -g)" build .gradle 2>/dev/null || true
  fi
  docker rm --force "$1" >/dev/null
  printf 'Stopped runner container %s\n' "$1"
}

case "${1:-}" in
start)
  shift
  # Only one stand runs on a host at a time (they all publish port 3000), so a runner left by an
  # earlier pipeline that never reached its publish or stop task is no longer needed.
  for leftover in $(docker ps --all --quiet --filter "label=$container_label"); do
    stop_container "$leftover"
  done
  docker pull "$IMAGE"
  docker run --detach --init \
    --name "$container" \
    --label "$container_label" \
    --add-host host.docker.internal:host-gateway \
    $env_args \
    -v "$SCRIPT_DIR:$SCRIPT_DIR" \
    -w "$SCRIPT_DIR" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    "$IMAGE" \
    "$SCRIPT_DIR/scripts/runner-entrypoint.sh" sleep infinity >/dev/null
  printf 'Started runner container %s in %s\n' "$container" "$SCRIPT_DIR"
  # build/allure-results accumulates across runs; this pipeline publishes only its own results.
  in_container rm -rf build/allure-results build/run-conclusion
  [ "$#" -eq 0 ] || in_container test-environment/profile "$@"
  ;;
exec)
  shift
  case "${1:-}" in
  test | upgrade-test | encryption-rotation-test)
    # The outcome is kept for the publish task, which runs later and cannot see this exit status.
    # One failed test action fails the whole run.
    in_container sh -c '
      test-environment/profile "$@"
      status=$?
      mkdir -p build
      if [ "$status" -ne 0 ]; then
        printf failure > build/run-conclusion
      elif [ "$(cat build/run-conclusion 2>/dev/null)" != failure ]; then
        printf success > build/run-conclusion
      fi
      exit "$status"
    ' sh "$@"
    ;;
  *)
    in_container test-environment/profile "$@"
    ;;
  esac
  ;;
publish)
  shift
  status=0
  in_container scripts/publish-external-results.sh "$@" || status=$?
  stop_container "$container"
  exit "$status"
  ;;
stop)
  stop_container "$container"
  ;;
*)
  docker pull "$IMAGE"

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
  ;;
esac
