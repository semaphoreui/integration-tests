#!/bin/sh
#
# Resolves which version of the Semaphore application the tests must run against.
#
# Two independent settings exist in this repository:
#
#   * the test source   - git.fixtures.repository / git.fixtures.branch (TEST_REPOSITORY /
#     TEST_BRANCH). It selects which fixtures and test cases are used and is untouched here.
#   * the application source - resolved by this script.
#
# Three application sources exist, in order of precedence:
#
#   * Docker image    - APP_IMAGE names an already published image, or APP_BRANCH=none asks this
#                       script to resolve nothing and leave the choice to the caller (the profile
#                       manifest). Nothing is cloned or built.
#   * Pull request    - the test run is explicitly linked to a pull request of the application
#                       repository. The image is built from that pull request HEAD commit.
#   * Branch (default)- no explicit link: the image is built from the HEAD commit of the
#                       application branch APP_BRANCH, which defaults to "develop". This is what
#                       both local runs and CI test when nothing else is declared, so the tests
#                       always run against the current state of the application.
#
# Both built sources are content addressed by the commit they were built from, so an image is
# built once and then reused by every later run of the same commit.
#
# Usage:
#   scripts/app-source.sh link      Resolve only the explicit link (no GitHub API, no registry).
#   scripts/app-source.sh resolve   Resolve the application source and print a human readable
#                                   report plus KEY=value lines on stdout.
#   scripts/app-source.sh env       Print only the KEY=value lines.
#   scripts/app-source.sh ensure    Resolve, then build the application image when it does not
#                                   exist yet. Prints the same report.
#
# The link is declared in the description of the test pull request, as a single trailer line:
#
#   Application-PR: semaphoreui/semaphore#123
#
# Accepted equally: "#123", "123" and the full pull request URL. The repository defaults to
# semaphoreui/semaphore. The key is case insensitive and also accepts "Application PR:" and
# "Application_PR:". Text inside HTML comments is ignored, so a pull request template may carry
# a commented-out example.
#
# The description is deliberately the only place a developer declares the link: unlike a file in
# the repository it never reaches the default branch when the test pull request is merged, so a
# forgotten link cannot turn the normal pipeline into a build of some old application pull
# request. The description reaches this script through APP_LINK_BODY or APP_LINK_BODY_FILE.
#
# APP_PR / APP_REPOSITORY stay available as CI plumbing: the application pull request trigger
# uses them to start a run for a branch, where no pull request description is in context. They
# take precedence over the description.
#
# Without a declared pull request the script falls back to branch mode. It never infers the
# application pull request from branch names. A closed or merged application pull request also
# falls back to branch mode, because it has no version left to test.
#
# Where the built image lives depends on APP_BUILD_PUSH, which defaults to true on GitHub Actions
# and to false everywhere else: CI builds once and pushes to the temporary registry namespace so
# that every job of the run can pull it, while a developer machine keeps the image in its own
# Docker store. A local run still reuses a registry image when it can read one.

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

DEFAULT_APP_REPOSITORY=semaphoreui/semaphore
DEFAULT_APP_BRANCH=develop
DEFAULT_PR_IMAGE_TAG_PREFIX=ci-pr
DEFAULT_APP_DOCKERFILE=deployment/docker/server/Dockerfile

fail() {
  printf 'app-source: %s\n' "$1" >&2
  exit 1
}

# The leading comment block is the documentation; it is printed from line 3 up to the first line
# that is no longer a comment, so editing the block cannot leave the help text behind.
usage() {
  awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^#[[:space:]]?/, ""); print }' "$0"
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

# Extracts every "Application-PR:" trailer from the pull request description. Text inside HTML
# comments is stripped first, so the commented-out example of a pull request template is not
# mistaken for a real declaration.
BODY_LINK_AWK='
{
  line = $0
  sub(/\r$/, "", line)
  visible = ""
  rest = line
  while (rest != "") {
    if (in_comment) {
      position = index(rest, "-->")
      if (position == 0) { rest = ""; break }
      rest = substr(rest, position + 3)
      in_comment = 0
    } else {
      position = index(rest, "<!--")
      if (position == 0) { visible = visible rest; rest = "" }
      else {
        visible = visible substr(rest, 1, position - 1)
        rest = substr(rest, position + 4)
        in_comment = 1
      }
    }
  }
  if (tolower(visible) ~ /^[ \t]*application[ _-]?pr[ \t]*:/) {
    value = substr(visible, index(visible, ":") + 1)
    gsub(/^[ \t]+|[ \t]+$/, "", value)
    gsub(/^[`"'"'"']+|[`"'"'"']+$/, "", value)
    if (value != "") print value
  }
}'

read_body_links() {
  if [ -n "${APP_LINK_BODY_FILE:-}" ]; then
    [ -f "$APP_LINK_BODY_FILE" ] \
      || fail "APP_LINK_BODY_FILE does not exist: $APP_LINK_BODY_FILE"
    awk "$BODY_LINK_AWK" "$APP_LINK_BODY_FILE"
  elif [ -n "${APP_LINK_BODY:-}" ]; then
    printf '%s\n' "$APP_LINK_BODY" | awk "$BODY_LINK_AWK"
  fi
}

# Accepts owner/name#123, #123, 123 and https://github.com/owner/name/pull/123.
parse_pr_reference() {
  reference=$1
  body_repository=
  body_pr=

  case "$reference" in
    http://*|https://*)
      reference=${reference%/}
      case "$reference" in
        */pull/*) ;;
        *) fail "Application-PR in the pull request description is not a pull request URL: $1" ;;
      esac
      number=${reference##*/pull/}
      number=${number%%/*}
      body_repository=$(normalise_repository "${reference%/pull/*}")
      ;;
    */*"#"*)
      body_repository=$(normalise_repository "${reference%%#*}")
      number=${reference##*#}
      ;;
    *)
      number=${reference#"#"}
      ;;
  esac

  number=${number%%[!0-9]*}
  case "$number" in
    ''|0) fail "Application-PR in the pull request description is not a pull request reference: $1" ;;
  esac
  body_pr=$number
}

resolve_body_link() {
  body_repository=
  body_pr=

  references=$(read_body_links)
  [ -n "$references" ] || return 0

  reference_count=$(printf '%s\n' "$references" | wc -l | tr -d ' ')
  [ "$reference_count" -eq 1 ] \
    || fail "the pull request description declares Application-PR $reference_count times; keep exactly one"

  parse_pr_reference "$references"
}

# A branch name reaches a Docker tag and a git refspec, so it is restricted to what both accept.
normalise_branch() {
  value=$1
  value=${value#refs/heads/}
  case "$value" in
    ''|-*|*..*|*[!A-Za-z0-9._/-]*) fail "invalid application branch: $1" ;;
  esac
  printf '%s' "$value"
}

# develop -> develop, release/2.20 -> release-2.20. Only used inside the image tag.
branch_slug() {
  printf '%s' "$1" | tr '/' '-'
}

resolve_link() {
  app_repository=${APP_REPOSITORY:-}
  app_pr=${APP_PR:-}
  app_branch=
  app_link_source=none
  [ -z "$app_pr" ] || app_link_source=ci-input

  if [ -z "$app_pr" ]; then
    resolve_body_link
    if [ -n "$body_pr" ]; then
      app_pr=$body_pr
      app_link_source=pull-request-body
      [ -n "$app_repository" ] || app_repository=$body_repository
    fi
  fi

  case "$app_pr" in
    '') app_source= ;;
    *[!0-9]*|0) fail "invalid application pull request number: $app_pr" ;;
    *) app_source=pull-request ;;
  esac

  if [ -z "$app_source" ]; then
    # No declared pull request. An explicitly named image wins over everything, and APP_BRANCH=none
    # asks for the image the profile manifest pins; otherwise the application branch is built.
    if [ -n "${APP_IMAGE:-}" ] || [ "${APP_BRANCH:-}" = "none" ]; then
      app_source=docker-image
      app_repository=
      app_link_source=none
      return 0
    fi
    app_source=branch
    app_branch=$(normalise_branch "${APP_BRANCH:-$DEFAULT_APP_BRANCH}")
    # The label describes which branch was chosen, not who passed it: a caller that repeats the
    # default (the profile manifest does) is still testing the default branch.
    app_link_source=ci-input
    [ "$app_branch" != "$DEFAULT_APP_BRANCH" ] || app_link_source=default-branch
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

# Branch mode deliberately does not use the GitHub CLI: a developer machine has git and needs no
# GitHub credentials for a public branch, while gh would have to be installed and authenticated.
resolve_branch_sha() {
  command -v git >/dev/null 2>&1 || fail "git is required to resolve the application branch $app_branch"

  if ! ls_remote_output=$(git ls-remote "https://github.com/$app_repository.git" \
    "refs/heads/$app_branch" 2>&1); then
    printf '%s\n' "$ls_remote_output" >&2
    fail "Unable to access application repository $app_repository"
  fi

  app_sha=$(printf '%s\n' "$ls_remote_output" | sed -n '1s/[[:space:]].*$//p')
  case "$app_sha" in
    '') fail "application branch $app_branch does not exist in $app_repository" ;;
    *[!0-9a-f]*) fail "Unable to resolve the HEAD SHA of $app_repository@$app_branch (unexpected value: $app_sha)" ;;
  esac
  [ "${#app_sha}" -eq 40 ] \
    || fail "Unable to resolve the HEAD SHA of $app_repository@$app_branch (unexpected value: $app_sha)"
}

resolve_sha() {
  command -v gh >/dev/null 2>&1 || fail "the GitHub CLI (gh) is required to resolve application PR #$app_pr"

  if ! api_error=$(gh api "repos/$app_repository/pulls/$app_pr" \
    --jq '[.head.sha, .state] | @tsv' 2>&1 >"$sha_file"); then
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

  app_pr_state=$(cut -f2 "$sha_file")
  app_sha=$(cut -f1 "$sha_file")
  case "$app_sha" in
    ''|null) fail "Unable to resolve the HEAD SHA of application PR #$app_pr" ;;
    *[!0-9a-f]*) fail "Unable to resolve the HEAD SHA of application PR #$app_pr (unexpected value: $app_sha)" ;;
  esac
  [ "${#app_sha}" -eq 40 ] \
    || fail "Unable to resolve the HEAD SHA of application PR #$app_pr (unexpected value: $app_sha)"
}

# CI builds on one machine and tests on another, so there the image must be pushed. A developer
# machine builds and runs in the same Docker store and usually cannot push to the namespace.
build_push_enabled() {
  build_push_default=false
  [ "${GITHUB_ACTIONS:-}" != "true" ] || build_push_default=true
  [ "${APP_BUILD_PUSH:-$build_push_default}" = "true" ]
}

image_exists() {
  command -v docker >/dev/null 2>&1 || fail "docker is required to inspect $app_image"
  if build_push_enabled; then
    # The registry is the authority: build and test run on different CI machines, so a locally
    # present image says nothing about what the test job will be able to pull.
    docker manifest inspect "$app_image" >/dev/null 2>&1
  else
    # Without a push the image stays on this machine, so the local store is the authority.
    docker image inspect "$app_image" >/dev/null 2>&1
  fi
}

# A local run that cannot push can still save the whole build by pulling an image CI already
# published for this exact commit. Failure to do so is not an error: the build follows.
pull_existing_image() {
  command -v docker >/dev/null 2>&1 || return 1
  docker manifest inspect "$app_image" >/dev/null 2>&1 || return 1
  printf 'Pulling the application image published for this commit...\n'
  docker pull --quiet "$app_image" >/dev/null 2>&1
}

# refs/pull/<n>/head for a pull request, refs/heads/<branch> for a branch.
application_ref() {
  if [ "$app_source" = "pull-request" ]; then
    printf 'refs/pull/%s/head' "$app_pr"
  else
    printf 'refs/heads/%s' "$app_branch"
  fi
}

application_description() {
  if [ "$app_source" = "pull-request" ]; then
    printf 'PR #%s' "$app_pr"
  else
    printf 'branch %s' "$app_branch"
  fi
}

checkout_application() {
  checkout_dir=$1
  application_ref_name=$(application_ref)
  mkdir -p "$checkout_dir"

  # The token is read from the environment by the credential helper instead of being passed on
  # the command line or written into the repository.
  git -C "$checkout_dir" init --quiet
  git -C "$checkout_dir" remote add origin "https://github.com/$app_repository.git"
  if ! git -C "$checkout_dir" \
    -c "credential.helper=" \
    -c "credential.helper=!f() { test \"\$1\" = get && printf 'username=x-access-token\npassword=%s\n' \"\${GH_TOKEN:-\${GITHUB_TOKEN:-}}\"; }; f" \
    fetch --quiet --depth 1 origin "$application_ref_name"; then
    fail "Unable to access application repository $app_repository (fetch of $application_ref_name failed)"
  fi
  git -C "$checkout_dir" checkout --quiet FETCH_HEAD

  fetched_sha=$(git -C "$checkout_dir" rev-parse HEAD)
  # The image is named after the commit it contains, so a source that moved between the
  # resolution and the fetch must not be built under the name of the previous commit.
  [ "$fetched_sha" = "$app_sha" ] \
    || fail "application $(application_description) moved during the run: expected $app_sha, fetched $fetched_sha; re-run to build the new commit"
}

build_and_push() {
  command -v docker >/dev/null 2>&1 || fail "docker is required to build $app_image"

  build_root=$(mktemp -d "${TMPDIR:-/tmp}/app-source.XXXXXX")
  # shellcheck disable=SC2064
  trap "rm -rf '$build_root'" EXIT INT TERM
  checkout_dir="$build_root/source"
  checkout_application "$checkout_dir"

  dockerfile=${APP_DOCKERFILE:-$DEFAULT_APP_DOCKERFILE}
  [ -f "$checkout_dir/$dockerfile" ] \
    || fail "application Dockerfile $dockerfile does not exist in $app_repository@$app_sha"

  set -- buildx build \
    --file "$checkout_dir/$dockerfile" \
    --tag "$app_image" \
    --provenance false
  # CI pins the platform its runners test on. A local build follows the machine it runs on,
  # because an emulated foreign platform would make every local run unusably slow.
  if [ -n "${APP_BUILD_PLATFORM:-}" ]; then
    set -- "$@" --platform "$APP_BUILD_PLATFORM"
  elif build_push_enabled; then
    set -- "$@" --platform linux/amd64
  fi
  if [ "${APP_BUILD_CACHE:-}" = "gha" ]; then
    set -- "$@" --cache-from type=gha --cache-to type=gha,mode=max
  fi
  if build_push_enabled; then
    set -- "$@" --push
  else
    set -- "$@" --load
  fi
  set -- "$@" "$checkout_dir"

  if ! docker "$@"; then
    fail "Unable to build the application image $app_image; see the Docker build logs above"
  fi

  if ! image_exists; then
    if build_push_enabled; then
      fail "Unable to push the application image $app_image"
    fi
    fail "the application image $app_image is missing from the local Docker store after the build"
  fi

  rm -rf "$build_root"
  trap - EXIT INT TERM
}

report_image_state() {
  if [ "$app_image_exists" = "true" ]; then
    printf 'Application image already exists\n'
    printf 'Application build: skipped\n'
  else
    printf 'Application image not found\n'
    printf 'Building application...\n'
  fi
}

report_closed_pull_request() {
  [ -n "$app_pr" ] || return 0
  [ -n "$app_pr_state" ] && [ "$app_pr_state" != "open" ] || return 0
  printf 'Application PR: #%s in %s is %s\n' "$app_pr" "$app_repository" "$app_pr_state"
  printf 'A closed or merged pull request has no version left to test.\n'
  if [ "$app_link_source" = "pull-request-body" ]; then
    printf 'Remove the Application-PR line from the pull request description to silence this.\n'
  fi
}

report() {
  if [ "$app_source" = "docker-image" ]; then
    report_closed_pull_request
    printf 'Application source: Docker image\n'
    printf 'Application image: %s\n' "${app_image:-profile manifest default}"
    printf 'Application build: skipped\n'
    return 0
  fi

  if [ "$app_source" = "branch" ]; then
    report_closed_pull_request
    printf 'Application source: Branch\n'
    printf 'Application repository: %s\n' "$app_repository"
    printf 'Application branch: %s\n' "$app_branch"
    printf 'Application SHA: %s\n' "$app_sha"
    printf 'Application image: %s\n' "$app_image"
    report_image_state
    return 0
  fi

  printf 'Application source: Pull Request\n'
  printf 'Application repository: %s\n' "$app_repository"
  printf 'Application PR: #%s\n' "$app_pr"
  printf 'Application SHA: %s\n' "$app_sha"
  printf 'Application image: %s\n' "$app_image"
  report_image_state
}

print_env() {
  printf 'APP_SOURCE=%s\n' "$app_source"
  printf 'APP_REPOSITORY=%s\n' "$app_repository"
  printf 'APP_BRANCH=%s\n' "$app_branch"
  printf 'APP_PR=%s\n' "$app_pr"
  printf 'APP_PR_STATE=%s\n' "$app_pr_state"
  printf 'APP_LINK_SOURCE=%s\n' "$app_link_source"
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
    printf 'app_branch=%s\n' "$app_branch"
    printf 'app_pr=%s\n' "$app_pr"
    printf 'app_pr_state=%s\n' "$app_pr_state"
    printf 'app_link_source=%s\n' "$app_link_source"
    printf 'app_sha=%s\n' "$app_sha"
    printf 'app_image=%s\n' "$app_image"
    printf 'app_image_exists=%s\n' "$app_image_exists"
    printf 'app_build_required=%s\n' "$app_build_required"
    printf 'app_build_performed=%s\n' "$app_build_performed"
  } >> "$GITHUB_OUTPUT"
}

resolve() {
  app_sha=
  app_pr_state=
  # A manually provided APP_IMAGE stays untouched in Docker image mode; it is the documented
  # escape hatch for running against an arbitrary already published image.
  app_image=${APP_IMAGE:-}
  app_image_exists=false
  app_build_required=false
  app_build_performed=false

  resolve_link

  if [ "$app_source" = "pull-request" ]; then
    work_dir=$(mktemp -d "${TMPDIR:-/tmp}/app-source.XXXXXX")
    sha_file="$work_dir/sha"
    resolve_sha
    rm -rf "$work_dir"

    # A closed or merged application pull request has no version left to test: its commits are
    # either abandoned or already on the application branch that is built by default. Building
    # it would pin the tests to a stale commit forever, so the run falls back to the mode it
    # would have used without any link at all. This is what makes a declaration left behind by
    # a merge harmless on any branch that inherits it.
    if [ "$app_pr_state" != "open" ]; then
      app_sha=
      resolve_fallback_after_closed_pull_request
      [ "$app_source" = "branch" ] || return 0
    fi
  fi

  case "$app_source" in
    docker-image) return 0 ;;
    branch) resolve_branch_sha ;;
  esac

  app_image="$(resolve_image_repository):$(image_tag)"
  if image_exists; then
    app_image_exists=true
  elif ! build_push_enabled && pull_existing_image; then
    app_image_exists=true
  else
    app_build_required=true
  fi
}

# The pull request is gone, so the run continues the way it would have without the declaration:
# against the application branch, unless an explicit image or APP_BRANCH=none asks otherwise.
resolve_fallback_after_closed_pull_request() {
  app_image=${APP_IMAGE:-}
  if [ -n "${APP_IMAGE:-}" ] || [ "${APP_BRANCH:-}" = "none" ]; then
    app_source=docker-image
    return 0
  fi
  app_source=branch
  app_branch=$(normalise_branch "${APP_BRANCH:-$DEFAULT_APP_BRANCH}")
}

image_tag() {
  if [ "$app_source" = "pull-request" ]; then
    printf '%s-%s-%s' "${APP_IMAGE_TAG_PREFIX:-$DEFAULT_PR_IMAGE_TAG_PREFIX}" "$app_pr" "$app_sha"
  else
    printf '%s-%s' "${APP_IMAGE_TAG_PREFIX:-ci-$(branch_slug "$app_branch")}" "$app_sha"
  fi
}

action=${1:-}
case "$action" in
  link)
    # Link resolution only: no GitHub API call, no registry access. Used by CI to decide
    # whether any application-source work is needed at all.
    app_sha=
    app_pr_state=
    app_image=${APP_IMAGE:-}
    app_image_exists=false
    app_build_required=false
    app_build_performed=false
    resolve_link
    # The temporary image name is only known after the HEAD commit has been resolved.
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
