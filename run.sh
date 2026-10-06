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

forwarded_names="API_BASE_URL API_USERNAME API_PASSWORD UI_BASE_URL TEST_REPOSITORY FIXTURE_GIT_PORT
  APP_IMAGE APP_SOURCE APP_BRANCH APP_REPOSITORY APP_PR APP_SHA GH_TOKEN GITHUB_TOKEN
  RUN_ID RUN_URL RUN_CONCLUSION RUN_WORKFLOW RUN_TITLE GITHUB_REPOSITORY HEAD_SHA
  SEMAPHORE_TASK_ID SEMAPHORE_PROJECT_ID SEMAPHORE_WORKFLOW_ID SEMAPHORE_WORKFLOW_RUN_ID
  SEMAPHORE_WORKFLOW_URL"

# Semaphore passes variable-type secrets, extra variables and survey variables to a shell task
# as NAME=value arguments, the secrets before the template arguments. A forwarded name becomes
# an environment variable, as an environment-type secret would; any other one is dropped, so it
# reaches neither the mode check below nor Gradle. Values are never printed. Arguments named
# after a profile (the <profile>=<dir> pairs of publish) are kept.
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
  case " $(echo $forwarded_names) RUNNER_IMAGE RUNNER_CONTAINER " in
  *" $name "*)
    export "$argument"
    printf 'run.sh: %s taken from the task arguments\n' "$name"
    ;;
  *)
    printf 'run.sh: skipping task argument %s\n' "$name"
    ;;
  esac
done

# RUNNER_IMAGE overrides the runner image, for example with one rebuilt from the current Dockerfile.
IMAGE=${RUNNER_IMAGE:-lowswoo/semaphore-test-container:1.0-arm}

# One per pipeline run; RUNNER_CONTAINER overrides it outside Semaphore.
container=${RUNNER_CONTAINER:-semaphore-tests-${SEMAPHORE_WORKFLOW_RUN_ID:-local}}
container_label=io.semaphoreui.integration-tests.runner=shared

env_args=
for name in $forwarded_names; do
  # `-e NAME` without a value forwards the host value, so secrets never appear in argv.
  eval "is_set=\${$name+x}"
  [ -z "$is_set" ] || env_args="$env_args -e $name"
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

stop_container() {
  if ! docker inspect "$container" >/dev/null 2>&1; then
    printf 'Runner container %s is not running\n' "$container"
    return 0
  fi
  # The runner works as root; hand its output back to the checkout owner, or Semaphore cannot
  # remove the checkout when it has to clone the repository again.
  if container_running; then
    docker exec "$container" chown -R "$(id -u):$(id -g)" build .gradle 2>/dev/null || true
  fi
  docker rm --force "$container" >/dev/null
  printf 'Stopped runner container %s\n' "$container"
}

case "${1:-}" in
start)
  shift
  # Only one stand runs on a host at a time (they all publish port 3000), so a runner left by an
  # earlier pipeline that never reached its publish or stop task is no longer needed.
  leftovers=$(docker ps --all --quiet --filter "label=$container_label")
  [ -z "$leftovers" ] || docker rm --force $leftovers >/dev/null
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
  stop_container
  exit "$status"
  ;;
stop)
  stop_container
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
