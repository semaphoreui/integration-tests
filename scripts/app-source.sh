#!/bin/sh
#
# Resolves which version of the Semaphore application the tests must run against.
#
# Two independent settings exist in this repository:
#
#   * the test source   - git.fixtures.repository / git.fixtures.branch (TEST_REPOSITORY /
#     TEST_BRANCH). It selects which fixtures and test cases are used and is untouched here.
#   * the application source - resolved by this script. By default the profile manifest image
#     is used and the application repository is never cloned or built. When a test run is
#     explicitly linked to a pull request of the application repository, the image is built
#     from that pull request HEAD commit and reused across runs.
#
# Usage:
#   scripts/app-source.sh link      Resolve only the explicit link (no GitHub API, no registry).
#   scripts/app-source.sh resolve   Resolve the application source and print a human readable
#                                   report plus KEY=value lines on stdout.
#   scripts/app-source.sh env       Print only the KEY=value lines.
#   scripts/app-source.sh ensure    Resolve, then build and push the application image when it
#                                   does not exist yet. Prints the same report.
#
# The explicit link between a test run and an application pull request is taken from, in order
# of precedence:
#
#   1. the APP_PR / APP_REPOSITORY environment variables (CI inputs);
#   2. the declarative application-under-test.yml file of this repository.
#
# When neither defines a pull request the script stays in normal mode. It never infers the
# application pull request from branch names.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

DEFAULT_APP_REPOSITORY=semaphoreui/semaphore
DEFAULT_IMAGE_TAG_PREFIX=ci-pr
DEFAULT_APP_DOCKERFILE=deployment/docker/server/Dockerfile

fail() {
  printf 'app-source: %s\n' "$1" >&2
  exit 1
}

usage() {
  sed -n '3,32p' "$0" | sed 's/^#\{0,1\} \{0,1\}//'
}

# semaphoreui/semaphore, https://github.com/semaphoreui/semaphore.git and
# git@github.com:semaphoreui/semaphore.git all normalise to semaphoreui/semaphore.
normalise_repository() {
  value=$1
  value=${value%.git}
  case "$value" in
    http://*|https://*)
      value=${value#*://}
      value=${value#*/}
      ;;
    *@*:*)
      value=${value#*:}
      ;;
  esac
  value=${value#/}
  value=${value%/}

  case "$value" in
    ''|*/*/*|*[!A-Za-z0-9._/-]*) fail "invalid application repository: $1" ;;
    */*) ;;
    *) fail "invalid application repository: $1 (expected owner/name)" ;;
  esac
  printf '%s' "$value"
}

# Minimal reader for the fixed two-level shape of application-under-test.yml:
#
#   application:
#     repository: semaphoreui/semaphore
#     pull_request: 123
#
# Comments and blank lines are ignored; anything else is reported as an error rather than
# silently skipped, so a malformed link never degrades into a normal-mode run.
read_declaration() {
  key=$1
  file=$2
  awk -v wanted="$key" '
    { line = $0; sub(/[[:space:]]+$/, "", line) }
    line ~ /^[[:space:]]*#/ { next }
    line == "" { next }
    line == "application:" { inside = 1; next }
    line ~ /^[^[:space:]]/ { inside = 0; next }
    inside && line ~ /^  [A-Za-z_]+:/ {
      key = line
      sub(/^  /, "", key)
      sub(/:.*$/, "", key)
      value = line
      sub(/^  [A-Za-z_]+:[[:space:]]*/, "", value)
      gsub(/^["'"'"']|["'"'"']$/, "", value)
      if (key == wanted) { print value }
      next
    }
    inside { printf "app-source: unexpected line in %s: %s\n", FILENAME, $0 > "/dev/stderr"; exit 3 }
  ' "$file"
}

resolve_link() {
  app_repository=${APP_REPOSITORY:-}
  app_pr=${APP_PR:-}

  declaration_file=${APP_SOURCE_FILE:-$repository_dir/application-under-test.yml}
  if [ -f "$declaration_file" ]; then
    if [ -z "$app_pr" ]; then
      app_pr=$(read_declaration pull_request "$declaration_file")
    fi
    if [ -z "$app_repository" ]; then
      app_repository=$(read_declaration repository "$declaration_file")
    fi
  fi

  case "$app_pr" in
    '') app_source=docker-image ;;
    *[!0-9]*|0) fail "invalid application pull request number: $app_pr" ;;
    *) app_source=pull-request ;;
  esac

  if [ "$app_source" = "docker-image" ]; then
    app_repository=
    return 0
  fi

  [ -n "$app_repository" ] || app_repository=$DEFAULT_APP_REPOSITORY
  app_repository=$(normalise_repository "$app_repository")
}

# Temporary images live in their own registry namespace so that release tags of
# semaphoreui/semaphore are never read, written or overwritten by this pipeline.
resolve_image_repository() {
  if [ -n "${APP_IMAGE_REPOSITORY:-}" ]; then
    printf '%s' "$APP_IMAGE_REPOSITORY"
    return 0
  fi
  tests_repository=${GITHUB_REPOSITORY:-semaphoreui/integration-tests}
  printf 'ghcr.io/%s/semaphore-ci' "$(printf '%s' "$tests_repository" | tr '[:upper:]' '[:lower:]')"
}

resolve_sha() {
  command -v gh >/dev/null 2>&1 || fail "the GitHub CLI (gh) is required to resolve application PR #$app_pr"

  if ! api_error=$(gh api "repos/$app_repository/pulls/$app_pr" --jq '.head.sha' 2>&1 >"$sha_file"); then
    case "$api_error" in
      *"Not Found"*|*"404"*)
        # GitHub answers 404 both for a missing pull request and for a repository the token
        # cannot see, so probe the repository itself to report the accurate reason.
        if gh api "repos/$app_repository" >/dev/null 2>&1; then
          fail "Application PR #$app_pr not found in $app_repository"
        fi
        fail "Unable to access application repository $app_repository"
        ;;
      *"Bad credentials"*|*"401"*|*"403"*|*"HTTP 403"*|*"gh auth login"*|*"authentication"*)
        fail "Unable to access application repository $app_repository"
        ;;
      *)
        printf '%s\n' "$api_error" >&2
        fail "Unable to resolve the HEAD SHA of application PR #$app_pr"
        ;;
    esac
  fi

  app_sha=$(cat "$sha_file")
  case "$app_sha" in
    ''|null) fail "Unable to resolve the HEAD SHA of application PR #$app_pr" ;;
    *[!0-9a-f]*) fail "Unable to resolve the HEAD SHA of application PR #$app_pr (unexpected value: $app_sha)" ;;
  esac
  [ "${#app_sha}" -eq 40 ] \
    || fail "Unable to resolve the HEAD SHA of application PR #$app_pr (unexpected value: $app_sha)"
}

image_exists() {
  command -v docker >/dev/null 2>&1 || fail "docker is required to inspect $app_image"
  docker manifest inspect "$app_image" >/dev/null 2>&1
}

checkout_pull_request() {
  checkout_dir=$1
  mkdir -p "$checkout_dir"

  # The token is read from the environment by the credential helper instead of being passed on
  # the command line or written into the repository.
  git -C "$checkout_dir" init --quiet
  git -C "$checkout_dir" remote add origin "https://github.com/$app_repository.git"
  if ! git -C "$checkout_dir" \
    -c "credential.helper=" \
    -c "credential.helper=!f() { test \"\$1\" = get && printf 'username=x-access-token\npassword=%s\n' \"\${GH_TOKEN:-\${GITHUB_TOKEN:-}}\"; }; f" \
    fetch --quiet --depth 1 origin "refs/pull/$app_pr/head"; then
    fail "Unable to access application repository $app_repository (fetch of refs/pull/$app_pr/head failed)"
  fi
  git -C "$checkout_dir" checkout --quiet FETCH_HEAD

  fetched_sha=$(git -C "$checkout_dir" rev-parse HEAD)
  [ "$fetched_sha" = "$app_sha" ] \
    || fail "application PR #$app_pr moved during the run: expected $app_sha, fetched $fetched_sha"
}

build_and_push() {
  command -v docker >/dev/null 2>&1 || fail "docker is required to build $app_image"

  build_root=$(mktemp -d "${TMPDIR:-/tmp}/app-source.XXXXXX")
  # shellcheck disable=SC2064
  trap "rm -rf '$build_root'" EXIT INT TERM
  checkout_dir="$build_root/source"
  checkout_pull_request "$checkout_dir"

  dockerfile=${APP_DOCKERFILE:-$DEFAULT_APP_DOCKERFILE}
  [ -f "$checkout_dir/$dockerfile" ] \
    || fail "application Dockerfile $dockerfile does not exist in $app_repository@$app_sha"

  set -- buildx build \
    --file "$checkout_dir/$dockerfile" \
    --platform "${APP_BUILD_PLATFORM:-linux/amd64}" \
    --tag "$app_image" \
    --provenance false
  if [ "${APP_BUILD_CACHE:-}" = "gha" ]; then
    set -- "$@" --cache-from type=gha --cache-to type=gha,mode=max
  fi
  if [ "${APP_BUILD_PUSH:-true}" = "true" ]; then
    set -- "$@" --push
  else
    set -- "$@" --load
  fi
  set -- "$@" "$checkout_dir"

  if ! docker "$@"; then
    fail "Unable to build the application image $app_image; see the Docker build logs above"
  fi

  if [ "${APP_BUILD_PUSH:-true}" = "true" ] && ! image_exists; then
    fail "Unable to push the application image $app_image"
  fi

  rm -rf "$build_root"
  trap - EXIT INT TERM
}

report() {
  if [ "$app_source" = "docker-image" ]; then
    printf 'Application source: Docker image\n'
    printf 'Application image: %s\n' "${app_image:-profile manifest default}"
    printf 'Application build: skipped\n'
    return 0
  fi

  printf 'Application source: Pull Request\n'
  printf 'Application repository: %s\n' "$app_repository"
  printf 'Application PR: #%s\n' "$app_pr"
  printf 'Application SHA: %s\n' "$app_sha"
  printf 'Application image: %s\n' "$app_image"
  if [ "$app_image_exists" = "true" ]; then
    printf 'Application image already exists\n'
    printf 'Application build: skipped\n'
  else
    printf 'Application image not found\n'
    printf 'Building application...\n'
  fi
}

print_env() {
  printf 'APP_SOURCE=%s\n' "$app_source"
  printf 'APP_REPOSITORY=%s\n' "$app_repository"
  printf 'APP_PR=%s\n' "$app_pr"
  printf 'APP_SHA=%s\n' "$app_sha"
  printf 'APP_IMAGE=%s\n' "$app_image"
  printf 'APP_IMAGE_EXISTS=%s\n' "$app_image_exists"
  printf 'APP_BUILD_REQUIRED=%s\n' "$app_build_required"
  printf 'APP_BUILD_PERFORMED=%s\n' "$app_build_performed"
}

publish_github_outputs() {
  [ -n "${GITHUB_OUTPUT:-}" ] || return 0
  {
    printf 'app_source=%s\n' "$app_source"
    printf 'app_repository=%s\n' "$app_repository"
    printf 'app_pr=%s\n' "$app_pr"
    printf 'app_sha=%s\n' "$app_sha"
    printf 'app_image=%s\n' "$app_image"
    printf 'app_image_exists=%s\n' "$app_image_exists"
    printf 'app_build_required=%s\n' "$app_build_required"
    printf 'app_build_performed=%s\n' "$app_build_performed"
  } >> "$GITHUB_OUTPUT"
}

resolve() {
  app_sha=
  # A manually provided APP_IMAGE stays untouched in normal mode; it is the documented escape
  # hatch for running against an arbitrary already published image.
  app_image=${APP_IMAGE:-}
  app_image_exists=false
  app_build_required=false
  app_build_performed=false

  resolve_link
  [ "$app_source" = "pull-request" ] || return 0

  work_dir=$(mktemp -d "${TMPDIR:-/tmp}/app-source.XXXXXX")
  sha_file="$work_dir/sha"
  resolve_sha
  rm -rf "$work_dir"

  app_image="$(resolve_image_repository):${APP_IMAGE_TAG_PREFIX:-$DEFAULT_IMAGE_TAG_PREFIX}-$app_pr-$app_sha"
  if image_exists; then
    app_image_exists=true
  else
    app_build_required=true
  fi
}

action=${1:-}
case "$action" in
  link)
    # Link resolution only: no GitHub API call, no registry access. Used by CI to decide
    # whether any application-source work is needed at all.
    app_sha=
    app_image=${APP_IMAGE:-}
    app_image_exists=false
    app_build_required=false
    app_build_performed=false
    resolve_link
    # The temporary image name is only known after the HEAD SHA has been resolved.
    [ "$app_source" = "docker-image" ] || app_image=
    print_env
    publish_github_outputs
    ;;
  resolve)
    resolve
    report
    print_env
    publish_github_outputs
    ;;
  env)
    resolve
    print_env
    publish_github_outputs
    ;;
  ensure)
    resolve
    report
    if [ "$app_build_required" = "true" ]; then
      build_and_push
      app_image_exists=true
      app_build_required=false
      app_build_performed=true
      printf 'Application build: completed\n'
      printf 'Application image: %s\n' "$app_image"
    fi
    print_env
    publish_github_outputs
    ;;
  help|-h|--help)
    usage
    ;;
  '')
    usage >&2
    exit 2
    ;;
  *)
    usage >&2
    fail "unknown action: $action"
    ;;
esac
