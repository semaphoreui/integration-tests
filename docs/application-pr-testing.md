# Testing Pull Requests of the Application Repository

The test repository and the application repository remain independent: no submodules are used, the tests
are not moved into the application repository, and the application is not moved into the test repository.
Two independent settings are kept separate.

| What it determines | Setting | Where it is set |
| --- | --- | --- |
| **Which tests to run** | `TEST_REPOSITORY` / `TEST_BRANCH` (`git.fixtures.repository` / `git.fixtures.branch`) | [MainConfig.java](../src/main/java/io/bookwright/config/MainConfig.java), stand properties, `-D` parameters; default is the local `fixture-git` Compose service serving the fixtures of the checkout, so CI does not set them |
| **Which application version to test** | the application branch (`develop` by default), or the `Application-PR:` line in the test PR description | `APP_BRANCH` / `APP_IMAGE`; PR description; `APP_REPOSITORY` / `APP_PR` as an internal CI mechanism |

The semantics of `TEST_REPOSITORY` / `TEST_BRANCH` have not changed.

## Three modes

### Branch mode (default)

The test PR description contains no `Application-PR:` line. The tests then run on the current state of the
application: the HEAD commit of its `develop` branch. The pipeline resolves that commit, reuses the image
already built for it and builds one only when no run has built that commit yet. Local runs and CI behave
identically, because both go through the same script.

```text
develop HEAD → image exists? → (no: checkout branch → build) → start application → run tests
```

The profile manifests declare this as `semaphore_image: branch:develop`
(`test-environment/profiles/<profile>/profile.yaml`). No additional steps are required for regular test
development; the first run after a new application commit pays for one build, every later run reuses it.

`APP_BRANCH` selects another branch of the application repository. To test an already published image
instead, name it in `APP_IMAGE`.

### Docker image mode

`APP_IMAGE` names an already published image; `scripts/app-source.sh` additionally accepts `APP_BRANCH=none`,
which resolves nothing and leaves the choice to the caller. The application repository is not cloned, nothing
is built and no temporary image is created.

### PR mode

The test run is explicitly linked to a Pull Request of the application repository. The pipeline determines the
HEAD SHA of that PR, computes the tag of the temporary image, reuses it if it exists, and builds it only if
it is missing.

```text
APP_PR → HEAD SHA → image exists? → (no: checkout PR → build → push) → start application → run tests
```

## Linking a test PR to an application PR

The link is always **explicit**. It is never inferred from the branch name, the word `feature`, matching
branch names, or the mere fact that the test branch has changed.

The only place where a developer declares it is the **test PR description**. Adding a single line is
enough:

```text
Application-PR: semaphoreui/semaphore#123
```

That is all. CI takes care of the rest.

### Why the PR description

The PR description is not part of the repository contents and **does not land in `main` on merge**.
Therefore a forgotten link physically cannot turn a normal `main` run into a build of a long-closed
application PR, and branches cut from `main` inherit nothing. A file in the repository provides no
such guarantee — it gets merged together with the PR.

### Accepted forms

| Entry | Meaning |
| --- | --- |
| `Application-PR: semaphoreui/semaphore#123` | repository and number explicitly |
| `Application-PR: #123` | default repository — `semaphoreui/semaphore` |
| `Application-PR: 123` | the same |
| `Application-PR: https://github.com/semaphoreui/semaphore/pull/123` | full link, optionally with `/files` |

The key is case-insensitive and also accepts `Application PR:` and `Application_PR:`. The entry must
start a line of the description — a mention of `Application-PR:` inside a sentence is not treated as a link.
Text inside HTML comments is ignored, so the PR template may contain a
commented-out example.

Two or more `Application-PR:` lines are a pipeline error, not a silent choice of one of them.

Editing the description re-runs CI: `ci.yml` subscribes to the `edited` event type in addition to
`opened`/`synchronize`/`reopened`. Without this, a line added after the PR was opened would not be
picked up, and a manual re-run would not help — it replays the original payload with the old description.

### What happens after the application PR is merged

While the test PR is open, its application PR may get merged. Such a PR no longer has a version
to test: its commits are already in the application's own branch. The pipeline loudly reports the reason and
falls back to branch mode — it tests the current `develop` instead of pinning the tests
to an outdated commit forever. The line should be removed from the description after that.

### CI variables

`APP_REPOSITORY` and `APP_PR` are an internal CI mechanism, not a way to declare the link manually. Through
them the auto-trigger workflow starts a run for a branch that has no PR context (and therefore no description).
They take precedence over the description. The same path is available manually:

```bash
gh workflow run ci.yml --ref feature/BOOK-123 \
  --field application_repository=semaphoreui/semaphore \
  --field application_pull_request=123
```

## Identification and isolation of temporary images

The tag of the temporary image contains the full SHA of the commit it was built from, prefixed by the
branch it came from or by the PR number:

```text
ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-develop-abc123456789...
ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-abc123456789...
```

* two different commits of the same branch or PR produce different images;
* a branch image and a PR image never collide, even for the same commit;
* multiple application/test PR pairs never share a single image;
* temporary images live in a separate GHCR namespace of the test repository, so the release tags of
  `semaphoreui/semaphore` are not read, not overwritten, and not touched at all.

The namespace is overridden with the `APP_IMAGE_REPOSITORY` variable, the tag prefix with `APP_IMAGE_TAG_PREFIX`.

## Image reuse

Before building, the pipeline checks whether an image for the computed SHA exists:

| Situation | Behavior |
| --- | --- |
| Only the test PR changed, the application SHA is the same | image exists → `pull → test`, no build is performed |
| A new commit appeared in the application PR | new tag → `build → push → test` |
| No `Application-PR:` in the description | the `develop` HEAD is resolved; its image is reused or built once |
| `develop` has not moved since the previous run | image exists → `pull → test`, no build is performed |
| Application PR is closed or merged | fallback to branch mode |
| `APP_IMAGE` is set | no cloning, no build, no temporary image |

## Automatic triggering

### When the application PR changes

The [`application-pr.yml`](../.github/workflows/application-pr.yml) workflow accepts the
`repository_dispatch` event of type `application-pr-updated`, reads the descriptions of all open test PRs and
finds **those that explicitly declared a link to this application PR**, then starts CI for them. Test PRs without a link or linked to
a different application PR are not started, and a change to an arbitrary branch of the application repository
starts nothing.

To enable auto-triggering, add `.github/workflows/notify-integration-tests.yml` to the application repository
`semaphoreui/semaphore` once:

```yaml
name: Notify integration tests

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read

jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Notify the test repository
        env:
          GH_TOKEN: ${{ secrets.INTEGRATION_TESTS_DISPATCH_TOKEN }}
        run: |
          gh api repos/semaphoreui/integration-tests/dispatches \
            --field event_type=application-pr-updated \
            --field 'client_payload[repository]=${{ github.repository }}' \
            --field 'client_payload[pull_request]=${{ github.event.pull_request.number }}' \
            --field 'client_payload[sha]=${{ github.event.pull_request.head.sha }}'
```

`INTEGRATION_TESTS_DISPATCH_TOKEN` is a token with `contents: write` permission on the test repository
(a fine-grained PAT or a GitHub App installation token). The token is stored only in secrets and is not
passed via command-line parameters.

The same workflow can be started manually:

```bash
gh workflow run application-pr.yml \
  --field application_repository=semaphoreui/semaphore \
  --field application_pull_request=123
```

**Fork limitation**: for a test PR from a fork, `GITHUB_TOKEN` is read-only, so
such a PR can neither be started via `workflow_dispatch` (its branch does not exist in the test repository) nor
be used to push a temporary image. Such PRs continue to be checked by their own
`pull_request` event in normal mode; they are explicitly marked in the `Application PR trigger` log. For
PR mode, the test PR branch must be kept in the test repository itself.

### When the test PR changes

The regular `pull_request` event of the [`ci.yml`](../.github/workflows/ci.yml) workflow. It passes the
PR description to the `Application source` job, which resolves the link, reuses the existing image and starts the tests. If
the application SHA has not changed, no build is performed.

## Authorization

| Secret / variable | Purpose | Required |
| --- | --- | --- |
| `GITHUB_TOKEN` (built-in) | reading the public application repository, pushing the temporary image to the test repository's GHCR | yes, issued automatically |
| `APPLICATION_REPOSITORY_TOKEN` | reading and checking out the application repository if it is private | only for private |
| `GHCR_CLEANUP_TOKEN` | deleting temporary images (`delete:packages`) | only for cleanup |
| `vars.APPLICATION_REPOSITORY` | application repository for cleanup, defaults to `semaphoreui/semaphore` | no |

Tokens are passed only via environment variables and secrets. When checking out a PR, a
git credential helper that reads the token from the environment is used, so the token ends up neither in the command
line nor in the repository.

## Cleanup of temporary images

The [`cleanup-pr-images.yml`](../.github/workflows/cleanup-pr-images.yml) workflow runs
daily and deletes the versions of the `semaphore-ci` package whose tag corresponds to a closed or
merged application PR, after a grace period (`RETENTION_HOURS`, 24 hours by default).
Only tags of the form `ci-pr-<number>-<sha>` in the test repository's namespace are processed —
release images are not touched. Without the `GHCR_CLEANUP_TOKEN` secret the workflow runs in
dry-run mode and only reports deletion candidates.

## Running locally

By default nothing has to be set up: the profile resolves the `develop` HEAD, builds its image once and
reuses it afterwards.

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local
```

Outside GitHub Actions the image is built for the local platform and kept in the local Docker store
(`APP_BUILD_PUSH` defaults to `false` there). When the registry already holds the image of that exact
commit and the machine can read it, it is pulled instead of being built. The resolved image is remembered
in `build/test-environment/<profile>/application.env`, so `test`, `logs` and `down` keep talking about the
image the containers were actually started with; `clean` forgets it again.

Testing another branch of the application, or a published image:

```bash
APP_BRANCH=release/2.20                      test-environment/profile up core-sqlite-local
APP_IMAGE=semaphoreui/semaphore:v2.19.14     test-environment/profile up core-sqlite-local
```

Resolving without any side effects:

```bash
scripts/app-source.sh resolve
APP_LINK_BODY='Application-PR: semaphoreui/semaphore#123' scripts/app-source.sh resolve
```

The description can also be passed as a file — `APP_LINK_BODY_FILE=path`. For local experiments it is easier
to use the internal `APP_PR` / `APP_REPOSITORY`:

```bash
APP_PR=123 scripts/app-source.sh resolve
```

Building a local image without publishing it and running a profile against it:

```bash
export APP_PR=123
export APP_IMAGE_REPOSITORY=local/semaphore-ci
export APP_BUILD_PUSH=false
eval "$(scripts/app-source.sh ensure | grep '^APP_')"

test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local
```

`test-environment/profile` takes the image from `APP_IMAGE` if the variable is set, and from the profile
manifest otherwise; a manifest value of `branch:<name>` is resolved and built by `scripts/app-source.sh`.
Useful build variables: `APP_BRANCH` (defaults to `develop`),
`APP_BUILD_PLATFORM` (defaults to `linux/amd64` in CI and to the local platform elsewhere),
`APP_DOCKERFILE` (defaults to `deployment/docker/server/Dockerfile`), `APP_BUILD_PUSH` (defaults to
`true` in CI and to `false` elsewhere).

## Logging

Branch mode:

```text
Application source: Branch
Application repository: semaphoreui/semaphore
Application branch: develop
Application SHA: abc123456789...
Application image: ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-develop-abc123456789...
Application image already exists
Application build: skipped
```

Docker image mode:

```text
Application source: Docker image
Application image: profile manifest default
Application build: skipped
```

PR mode with reuse:

```text
Application source: Pull Request
Application repository: semaphoreui/semaphore
Application PR: #123
Application SHA: abc123456789...
Application image: ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-abc123456789...
Application image already exists
Application build: skipped
```

PR mode with a build:

```text
Application image not found
Building application...
Application build: completed
```

The mode is also recorded in the Allure environment: `application.source`, `application.repository`,
`application.branch`, `application.pull.request`, `semaphore.image`, `semaphore.source.commit`.

## Error handling

| Situation | Behavior |
| --- | --- |
| The application PR does not exist | `Application PR #123 not found in <repo>`, the pipeline fails |
| No access to the repository | `Unable to access application repository <repo>`, the pipeline fails |
| The SHA could not be determined | `Unable to resolve the HEAD SHA of application PR #123`, the pipeline fails |
| The application branch does not exist | `application branch <name> does not exist in <repo>`, the pipeline fails |
| Two `Application-PR:` lines in the description | the pipeline fails, no choice between them is made |
| `Application-PR:` does not look like a PR reference | the pipeline fails, reporting the original value |
| The branch or PR received a new commit during the build | the build is aborted with an explicit out-of-sync message |
| The image could not be built | the pipeline fails, the Docker build logs remain in the step output |
| The image could not be pushed | the pipeline fails after verifying that the image is really missing from the registry |
| The image is not available for pull | the tag is treated as missing, a build and push are performed |
