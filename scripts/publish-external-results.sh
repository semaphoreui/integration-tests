#!/bin/sh

# Publishes raw Allure results of a run outside GitHub runners (Orbantix) to GitHub Pages.
#
# The results are pushed as a single orphan commit to the orbantix-results/<run id> branch, then
# publish-pages.yml is dispatched: it builds the report, adds it to the gh-pages history with the
# Orbantix source, deploys Pages and deletes the branch. The server needs only git and curl.
#
# Usage: scripts/publish-external-results.sh <profile>=<allure results dir> [<profile>=<dir>...]
#
# <profile> must be a folder of test-environment/profiles; other arguments are skipped, because
# Semaphore appends the environment's extra variables as NAME=value arguments.
#
# Environment:
#   GH_TOKEN          token with Contents and Actions read/write on the repository (required)
#   RUN_ID            numeric run id (default: generated from the current time)
#   RUN_URL           link to the run on the external server (required)
#   RUN_CONCLUSION    success, failure, cancelled, timed_out, neutral or skipped (required)
#   RUN_WORKFLOW      workflow name on the Pages site (default: Orbantix)
#   RUN_TITLE         run card title (default: Orbantix run <UTC start time>)
#   GITHUB_REPOSITORY owner/name (default: semaphoreui/integration-tests)
#   HEAD_SHA          tested commit (default: HEAD of this checkout)

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

echo "SEMAPHORE_TASK_ID=$SEMAPHORE_TASK_ID"
echo "SEMAPHORE_PROJECT_ID=$SEMAPHORE_PROJECT_ID"
echo "SEMAPHORE_WORKFLOW_ID=$SEMAPHORE_WORKFLOW_ID"
echo "SEMAPHORE_WORKFLOW_RUN_ID=$SEMAPHORE_WORKFLOW_RUN_ID"
echo "SEMAPHORE_WORKFLOW_URL=$SEMAPHORE_WORKFLOW_URL"
echo "PATH:=$(pwd)"

RUN_URL=$SEMAPHORE_WORKFLOW_URL

fail() {
  printf 'publish-external-results: %s\n' "$1" >&2
  exit 1
}

[ "$#" -gt 0 ] || fail "usage: $0 <profile>=<allure results dir> [<profile>=<dir>...]"

: "${GH_TOKEN:?Set GH_TOKEN to a token with Contents and Actions read/write on the repository}"
: "${RUN_URL:?Set RUN_URL to the link of the run on the external server}"
: "${RUN_CONCLUSION:?Set RUN_CONCLUSION to the final status of the run}"
repository=${GITHUB_REPOSITORY:-semaphoreui/integration-tests}
workflow_name=${RUN_WORKFLOW:-Orbantix}
head_sha=${HEAD_SHA:-$(git -C "$repository_dir" rev-parse HEAD)}
created_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
display_title=${RUN_TITLE:-"Orbantix run $(date -u '+%Y-%m-%d %H:%M UTC')"}
# Epoch seconds plus three random digits: numeric as the Pages history requires, and unique
# enough that two servers publishing in the same second do not share a results branch.
if [ -z "${RUN_ID:-}" ]; then
  RUN_ID=$(date -u '+%s')$(printf '%03d' "$(($(od -An -N2 -tu2 /dev/urandom | tr -d ' ') % 1000))")
  printf 'Generated RUN_ID=%s\n' "$RUN_ID"
fi
results_ref="orbantix-results/$RUN_ID"

case "$RUN_ID" in
'' | *[!0-9]*) fail "RUN_ID must be numeric: $RUN_ID" ;;
esac
case "$RUN_CONCLUSION" in
success | failure | cancelled | timed_out | neutral | skipped) ;;
*) fail "unsupported RUN_CONCLUSION: $RUN_CONCLUSION" ;;
esac
case "$RUN_URL" in
http://* | https://*) ;;
*) fail "RUN_URL must use http(s): $RUN_URL" ;;
esac
printf '%s' "$head_sha" | grep -Eq '^[0-9a-f]{40}$' || fail "HEAD_SHA must be a full commit SHA: $head_sha"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

# Semaphore passes extra variables, survey variables and variable-type secrets of the environment
# as NAME=value arguments too, so only arguments named after a profile are results; the rest are
# skipped by name, never printing the value, which may be a secret.
published=0
for pair in "$@"; do
  profile=${pair%%=*}
  results_dir=${pair#*=}
  case "$profile" in
  '' | *[!A-Za-z0-9._-]*) profile= ;;
  esac
  if [ "$profile" = "$pair" ] || [ -z "$profile" ] || [ ! -f "$repository_dir/test-environment/profiles/$profile/profile.yaml" ]; then
    printf 'Skipping argument %s: not <profile>=<dir> with a profile from test-environment/profiles\n' "${pair%%=*}"
    continue
  fi
  [ -d "$results_dir" ] || fail "results directory does not exist: $results_dir"
  find "$results_dir" -maxdepth 1 -type f -name '*-result.json' | grep -q . ||
    fail "no *-result.json files in $results_dir"
  cp -R "$results_dir" "$work_dir/allure-results-$profile"
  published=$((published + 1))
done
[ "$published" -gt 0 ] || fail "no <profile>=<allure results dir> argument names a profile from test-environment/profiles"

# The token goes through a header, never the remote URL, so it does not reach git output or logs.
auth_header="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$GH_TOKEN" | base64 | tr -d '\n')"

git -C "$work_dir" init -q
git -C "$work_dir" add --all
git -C "$work_dir" -c user.name=orbantix -c user.email=orbantix@users.noreply.github.com \
  commit -q -m "Raw Allure results of Orbantix run $RUN_ID"
git -C "$work_dir" -c "http.https://github.com/.extraheader=$auth_header" \
  push -q --force "https://github.com/$repository.git" "HEAD:refs/heads/$results_ref"
printf 'Pushed raw results to %s\n' "$results_ref"

payload=$(python3 -c 'import json, sys; print(json.dumps({"ref": "main", "inputs": dict(zip(sys.argv[1::2], sys.argv[2::2]))}))' \
  results_ref "$results_ref" \
  run_id "$RUN_ID" \
  run_url "$RUN_URL" \
  workflow_name "$workflow_name" \
  display_title "$display_title" \
  conclusion "$RUN_CONCLUSION" \
  head_sha "$head_sha" \
  created_at "$created_at")

status=$(curl -sS -o "$work_dir/response" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H 'Accept: application/vnd.github+json' \
  -H 'X-GitHub-Api-Version: 2022-11-28' \
  "https://api.github.com/repos/$repository/actions/workflows/publish-pages.yml/dispatches" \
  -d "$payload")
[ "$status" = 204 ] || {
  cat "$work_dir/response" >&2
  fail "workflow dispatch failed with HTTP $status; the results stay in $results_ref"
}

printf 'Started publishing: https://github.com/%s/actions/workflows/publish-pages.yml\n' "$repository"
