#!/bin/sh

# Entry point of the test runner container started by run.sh.
#
# The profile script starts the stand through the host Docker daemon, so the stand publishes its
# ports on the host, while the manifests, stand.properties and tests address it as localhost.
# Inside the runner localhost is the runner itself, so every published port is forwarded from the
# runner's loopback to the host. The host network mode is not an option: on Docker Desktop it
# sends connections to 127.0.0.1 to the macOS host, which breaks Gradle's own loopback
# connections to its daemon and test workers.

set -eu

# Host ports published by the compose files under test-environment; keep in sync with them.
forwarded_ports="3000 3003 3443 5556 ${FIXTURE_GIT_PORT:-3080}"
forward_host=${RUNNER_FORWARD_HOST:-host.docker.internal}

command -v socat >/dev/null 2>&1 \
  || { printf 'runner-entrypoint: socat is missing; rebuild the runner image\n' >&2; exit 1; }

for port in $forwarded_ports; do
  socat "TCP-LISTEN:$port,bind=127.0.0.1,fork,reuseaddr" "TCP:$forward_host:$port" 2>/dev/null &
done

exec "$@"
