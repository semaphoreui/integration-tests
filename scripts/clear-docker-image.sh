#!/bin/sh

set -eu

IMAGE=ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-develop-f04b63f72248ff1bd8dd0160b8e20b0836b64a54

docker rmi $IMAGE
