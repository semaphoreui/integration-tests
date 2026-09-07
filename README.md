# Semaphore UI test automation

Test project for [Semaphore UI](https://github.com/semaphoreui/semaphore), built on top of [Bookwright v1.4.0](https://github.com/dantro86/bookwright/releases/tag/v1.4.0) (`b30d7e6`).

The current Semaphore release matrix `v2.19.12` is fully confirmed in Linux CI: 11 configuration
profiles passed on 2026-09-04. The `v2.19.8 → v2.19.12` upgrade separately passed on SQLite and
PostgreSQL.

## Stack

- Java 21;
- Gradle;
- JUnit 5;
- Retrofit and OkHttp;
- Playwright;
- Guice;
- AssertJ;
- Allure;
- Awaitility.

The framework is adapted for Semaphore while preserving the Bookwright v1.4.0 architecture: API and steps are separated as `target/domain`, scenario data belongs to typed fixtures, and precondition state is read only through the typed `TestStore`.

## Local environment

```bash
test-environment/profile up core-sqlite-local
```

Semaphore will be available at <http://localhost:3000>.

## First API smoke

```bash
test-environment/profile test core-sqlite-local
```

The command checks readiness on its own and adds the exact environment configuration to the Allure environment. Stop while keeping the SQLite volume: `test-environment/profile down core-sqlite-local`. Removing the state completely requires the explicit command `test-environment/profile clean core-sqlite-local --yes`.

The core suite also guards the project deletion lifecycle: after a task is stopped, the project and
its dependent resources are deleted correctly. A known-defect canary for `v2.19.8` shows that deletion
while `running` incorrectly returns `204`, leaves the executor running and ends with FK errors.
The reproduction and source boundary are described in
`test-environment/project-deletion-running-task-defect.md`.

Static inventory is verified in both standard formats — INI `static` and YAML `static-yaml`.
For each format the scenario saves two groups with different host aliases and runs a template with a
default `limit`. The task output confirms that only the selected group was executed.

The password-login security test compares responses for an existing and an unknown account, verifies
the absence of a session cookie and the correct rejection of an empty password. On `v2.19.8` five consecutive
failures remain without throttling or an audit warning; this security gap is described in
`test-environment/password-login-brute-force-protection-gap.md`.

The same core profile runs plan-only scenarios on the Terraform 1.11.3 and OpenTofu 1.11.0 bundled in
the release image. The minimal local module does not download providers; workspace inventories of types
`terraform-workspace` and `tofu-workspace` are confirmed by the real output of each tool. The attached
Variable Group passes a secret of type `env` with the `TF_VAR_` prefix: the module compares its SHA-256 and
prints only a safe marker, while the test excludes plaintext from the API, structured/raw output and Allure.

A short browser smoke on the same environment verifies password login, launching a prepared executable template through the UI, and that the project name is required before the request is submitted:

```bash
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
```

The same suite runs without copying tests on PostgreSQL, MySQL and MariaDB. The profiles share a common port and are started sequentially:

```bash
test-environment/profile down core-sqlite-local
test-environment/profile up core-postgres-local
test-environment/profile test core-postgres-local

test-environment/profile down core-postgres-local
test-environment/profile up core-mysql-local
test-environment/profile test core-mysql-local

test-environment/profile down core-mysql-local
test-environment/profile up core-mariadb-local
test-environment/profile test core-mariadb-local
```

The production-like variant executes tasks in a separate persistent runner:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

The runner registers automatically, stores a long-lived token in a separate volume and is assigned as the default runner through the admin API. The API tests confirm `active`, `registered`, `is_default`, `online`, heartbeat, exact tag routing by the persisted `used_runner_id` and capacity `1`: the second task stays in `waiting` while the first one occupies the runner.

When no suitable active runner is available, the behaviour differs from the capacity case: `v2.19.8` moves the task to `error: no runners available` instead of keeping it in the queue. This was reproduced for a temporarily disabled matching runner and for a non-existent tag; details are in `test-environment/runner-unavailable-routing-defect.md`.

Another remote execution difference: `v2.19.8` loses secret survey variables before dispatch, so the task receives an undefined variable. The profile contains a safe known-defect canary that does not print the value; the upstream fix #4086 is already included in `v2.20.0-alpha1`. The evidence and the criterion for switching to a positive regression are described in `test-environment/remote-runner-survey-secrets-defect.md`.

The SSH feature profile verifies an encrypted access key on two client boundaries at once: Git clone over SSH and an Ansible connection to a remote target:

```bash
test-environment/profile down prod-postgres-runner
test-environment/profile up feature-ssh-local
test-environment/profile test feature-ssh-local
```

Two isolated SSH servers are reachable only inside the Compose network and accept different generated keys. The positive scenario confirms remote playbook execution, the negative one confirms useful clone diagnostics with a wrong key. The rotation scenario first gets a rejection from the second server with the old key, updates the secret of the same access key through the API and then confirms a successful Git clone and Ansible SSH. All scenarios verify that the private key and passphrase are absent from the API and task output.

A private Git over HTTPS is verified separately, through a local NGINX with self-signed TLS and mandatory Basic Auth:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-git-https
test-environment/profile test feature-git-https
```

The profile passes the trusted CA to Git processes through the standard `SEMAPHORE_FORWARDED_ENV_VARS`. The positive scenario runs a playbook after an authenticated clone, the negative one confirms rejection without an access key. The password does not leak into API/Allure diagnostics, and the login and password are absent from the structured/raw task output.

The OIDC feature profile performs a full browser login through a local Dex and verifies the callback, session, return path, external user provisioning, repeated login, logout, a conflict with a local email and rejection of an unavailable provider:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-oidc-local
test-environment/profile test feature-oidc-local
```

The production-like OIDC variant repeats the same contract through a pinned NGINX, HTTPS and the non-root public URL `/semaphore` on PostgreSQL:

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-proxy-oidc
test-environment/profile test feature-proxy-oidc
```

The TLS certificate and JVM truststore are generated locally in the ignored `build/test-fixtures`. The test additionally verifies the `Secure`, `HttpOnly` and path attributes of the session cookie, API/assets routing through the subpath and the return of the OIDC callback to the public HTTPS origin.

Rotation of the database encryption keyring is verified by a separate three-phase lifecycle on PostgreSQL:

```bash
test-environment/profile encryption-rotation-test feature-encryption-rotation
```

The scenario switches the primary without a restart, confirms simultaneous reading of the old and writing of the new ciphertext, runs `vault check`/`vault rekey`, removes the retired key and re-runs the saved template. Test-only key material is generated in the ignored `build/test-fixtures/encryption-rotation`.

The LDAPS feature profile brings up a pinned OpenLDAP, performs a service search and user bind over TLS, and then verifies external user provisioning/reuse, logout, a wrong password and protection of the local account:

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-ldap-tls
test-environment/profile test feature-ldap-tls
```

TOTP MFA is verified by a separate self-contained profile:

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-totp-local
test-environment/profile test feature-totp-local
```

The shared `totpTest` covers API self-enrollment, `TOTP_REQUIRED`, an invalid and a valid RFC 6238 passcode, recovery, repeated enrollment and rejection of an already used recovery code. The browser scenario separately verifies the Security settings, QR/recovery-code rendering, the challenge and the recovery form. OTP material is redacted in HTTP and raw Allure JSON, and sensitive browser artifacts are not published on failure.

The dynamic runner profile verifies the start/finish webhook, launching a separate one-off runner and real task execution:

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

On `v2.19.8` the task finishes successfully, but the one-off runner does not exit after the terminal progress. The profile is kept as a manual red reproducer and is not part of the stable CI matrix. The analysis and probable cause are in `test-environment/dynamic-runner-one-off-exit-defect.md`.

Short Bash commands and a background child are verified by a separate shell-output profile:

```bash
test-environment/profile down feature-dynamic-runner
test-environment/profile up feature-shell-output
test-environment/profile test feature-shell-output
```

On `v2.19.12` the task gets `success`, but the saved output may lose the whole `stdout` or
`stderr`. The strict test of both streams is kept as a manual red reproducer and is not part of the stable
PR/nightly gate. The cause, CI evidence and two upstream fixes are described in
`test-environment/shell-output-loss-defect.md`.

The experimental schedule profile reproduces real cron/`run_at` execution in a non-UTC timezone:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

On `v2.19.8` both scenarios reproduce the defect locally: the active schedule is saved, but no task is created. The profile is not yet included in the CI matrix; the evidence and expected behaviour are in `test-environment/schedule-execution-defect.md`.

Verification of upgrading the published images on a preserved SQLite or PostgreSQL database is launched by a separate command:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

The current upgrade path is `v2.19.8 → v2.19.12`. It successfully confirmed preservation of resources,
access keys and task output on SQLite and PostgreSQL in Linux CI on 2026-09-04. The previous pair
`v2.19.7 → v2.19.8` passed on both DBMSs on 2026-08-19. The upgrade remains a separate observed
gate: it verifies migration of the preserved state, not just a clean installation.
The diagnostics are recorded in `test-environment/v2.19.8-regression-report.md`; the historical
schema defect of the `v2.19.6 → v2.19.7` pair is in `test-environment/upgrade-report.md`.

## CI

GitHub Actions are split by cost and purpose:

- `CI` runs for every pull request and push to `main`: it first runs the framework quality gate, then the core API suite and a short Chromium UI smoke on `core-sqlite-local`;
- `Configuration matrix` runs daily at `01:30 UTC` and manually, and verifies PostgreSQL, MySQL, MariaDB, production-like PostgreSQL with a persistent runner, SSH, private HTTPS Git, direct and HTTPS/subpath OIDC, LDAPS, TOTP and database encryption keyring rotation;
- `Release upgrade` runs weekly on Sundays at `03:30 UTC` and manually, and verifies the `v2.19.8 → v2.19.12` upgrade on SQLite and PostgreSQL;
- `Application PR trigger` accepts `repository_dispatch` from the main repository and launches CI for test PRs explicitly linked to the changed application PR;
- `Cleanup temporary application images` runs daily at `04:00 UTC` and removes temporary images of closed and merged application PRs.

Matrix jobs use separate GitHub-hosted runners and run in parallel with `fail-fast: false`. JUnit, HTML reports, Allure results and container diagnostics on failure are saved as artifacts. The upgrade workflow is not part of the PR gate; a green job must mean both data preservation and full finalization of the task output.

### Test source and application source

The two settings are independent. `TEST_REPOSITORY` / `TEST_BRANCH` (`git.fixtures.repository` /
`git.fixtures.branch`) still determine only which fixtures and tests to use. The fixtures are not
taken from the checkout — the application clones them by URL — so `CI` sets this pair itself: a pull
request is tested with its own head branch (for a fork, with the repository of that fork), and a push
to `main` or a manual run with the branch the run was started on. Locally the defaults from
`MainConfig` remain in force.
A separate group `APP_REPOSITORY` / `APP_PR` determines which version of the application to test.

If the application PR is not set, the behaviour does not change: the main repository is not cloned,
the application is not built, no temporary Docker image is created, and the image from the profile
manifest is used. To run the tests against a specific PR of the main repository, it is enough to add
one line to the **test PR description**:

```text
Application-PR: semaphoreui/semaphore#123
```

The PR description was chosen deliberately: unlike a file in the repository, it does not land in `main` on
merge, so a forgotten link cannot affect regular runs.

CI determines the HEAD SHA of that PR, reuses the already published
`ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-<sha>` and builds the application only
when an image for that commit does not exist yet. A change to the tests alone does not trigger a
rebuild. The full description, including auto-triggering, authorization and cleanup of temporary images, is
in [`docs/application-pr-testing.md`](docs/application-pr-testing.md).

When `Configuration matrix` is launched manually, the inputs `include_schedule_investigation`
and/or `include_shell_output_investigation` can be enabled. Then, for that run only, the corresponding
known red defect profiles are added to the matrix to confirm the problem on Linux and collect the
standard artifacts; the daily run remains a green gate without expected failures.

After every CI, nightly matrix or release-upgrade run, Allure is automatically assembled into a ready-made HTML site and uploaded as the artifact `allure-html-<run>-<attempt>`. Every Allure report is built in single-file mode: after downloading, it is enough to unpack the archive and open `index.html` with a double click — no local HTTP server is needed. For a matrix run the start page contains a separate report for each profile, so the results of different DBMSs are not mixed in retries.

Completed runs of the trusted `main` branch are additionally published on [GitHub Pages](https://semaphoreui.github.io/integration-tests/). The mini-site keeps at most 60 runs from the last 30 days, groups them by date and allows filtering by workflow and Semaphore version. For each run the final status, commit, profiles, test distribution and individual Allure reports are available. PR and external-environment runs are deliberately not published. The generated history is stored in the `gh-pages` branch; it should not be edited manually. Before the first deployment the repository owner must select `Settings → Pages → Source → GitHub Actions` once.

The test verifies health, an invalid and a valid login, creates an isolated project and the main resource chain:

```text
project → access key → local Git repository → inventory → task template
→ task execution → success status → output marker
→ inactive cron schedule → schedule verification
→ guest RBAC → assigned project access → forbidden mutation
→ unassigned project isolation
```

After the test, Bookwright LIFO cleanup removes the project data in reverse order. For RBAC a single stable fixture user `bookwright-rbac-guest` is used: repeated runs reuse it, because Semaphore does not allow deleting a user after a login session has been created.

A separate RBAC suite pins the built-in `manager` and `task_runner` contracts. A manager can create project resources and run tasks, but cannot delete the project or manage members. A task runner can run tasks, but gets `403` when modifying resources, the project and the membership.

The API token suite creates a time-limited token, verifies prefix-only listing, authenticates a separate Retrofit session through the Bearer header and creates a project. After revocation the same token gets `401`; creating an already expired token is rejected with `400`. The full value does not leak into the URL, step parameters or HTTP/Allure attachments: the creation response is deliberately hidden, Authorization is redacted, and delete uses the public eight-character prefix.

The user lifecycle suite verifies the sequence create → update → delete → absence → recreate supported by the Community API on a disposable typed fixture. The user model in the current Semaphore has no `active/disabled` field and no deactivate/reactivate endpoints, so such a contract is not imitated by substituting password/delete.

The file inventory suite creates a repository-backed `type=file`, runs a playbook through an inventory file from the trusted Git fixture and verifies the saved `repository_id`. A separate safe canary pins a `v2.19.8` defect: create accepts the traversal path `../…`, although update correctly returns `400`; such an inventory is not executed.

A separate security smoke creates a `login_password` access key with a unique marker, uses it as the inventory credential during task execution and verifies the absence of plaintext in the create/get/list API, structured and raw task output, Allure and JUnit artifacts.

The Variable Group suite creates a mixed group with JSON extra vars, ENV and secrets of types `var`/`env`, renames the saved secret without replacing its value and really runs `variables.yml`. The playbook verifies the secrets by SHA-256 under `no_log` and prints only a safe marker; the test separately checks the create/get/list API and structured/raw output. The same contract passed on SQLite and PostgreSQL `v2.19.8`; an empty ENV variable name gets a diagnosable `400`.

The Terraform/OpenTofu suite separately verifies the native `TF_VAR_*` contract: a unique secret is stored
in a Variable Group as `env`, attached to both templates and really read as a Terraform
input variable. The provider-free module publishes the marker only after the hash matches; plaintext does not
appear in the Variable Group API, task output or HTTP/Allure diagnostics.

The Build/Deploy suite creates a linked pair of Ansible templates and manually selects a successful build task
when launching the deploy. Semaphore assigns the build a `start_version`, passes it as
`SEMAPHORE_TASK_TARGET_VERSION`, saves `build_task_id` on the deploy and passes the same version as
`SEMAPHORE_TASK_INCOMING_VERSION`. Both playbooks compare the API metadata with the executor environment and print
safe version markers. The deploy detail API stores the link, while the displayed version is taken from the
nested `build_task` in the task history — the deploy task has no `version` field of its own.

The survey/task override suite saves enum, integer, string, env-target and secret survey variables in the template, then runs `survey-overrides.yml` with overridden values, template/task arguments and Ansible `limit`/`tags`/`skip_tags`/`diff`/`skip_galaxy_install`. The task really runs on SQLite and PostgreSQL with local execution, the secret is verified by SHA-256 under `no_log` and is absent from the structured/raw output. The persistent runner on `v2.19.8` loses the survey secret before dispatch; this is covered by a separate canary until the move to upstream #4086. An unsupported survey target gets `400`. A `v2.19.8` gap is also recorded: an enum default outside the list is accepted by the backend; the fix is already in upstream `v2.20.0-alpha1`.

The webhook integration suite creates a token-authenticated searchable integration, a project alias, a header matcher and extractors from the JSON body/header. Requests with a wrong token or event do not launch a task, while a valid webhook returns task identifiers, saves the link through `integration_id` and really passes the extracted values to the Ansible playbook. The token is stored in a `login_password` access key and is redacted in API/Allure diagnostics.

The project backup/restore suite exports the configuration with access keys, repository, inventory, template and schedule after a real task execution. The backup contains no plaintext authentication secret and no task history; the restored project gets new IDs with correctly relinked resources, after which its template runs successfully again. Workflows and external Secret Storage management are not imitated on the Community image: both capabilities are disabled by feature flags and require a Pro test subscription for an honest e2e.

Negative restore checks confirm that the operation is forbidden for a non-admin and that a missing repository reference is rejected. On `v2.19.8` a general off-by-one defect of duplicate validation was found: a document with two identical repository names is accepted and creates both resources; the canary and source boundary are described in `test-environment/project-backup-restore-validation-defect.md`.

The concurrency suite creates a template with `allow_parallel_tasks=true` so as not to mix the project limit with the template lock. With `max_parallel_tasks=1` the first task reaches the marker, while the second one reliably stays in `waiting`; after the slot is freed it starts. After the project is updated through the API to a limit of `2`, two tasks reach the marker simultaneously and both stop correctly.

The Git suite verifies task execution from an explicitly selected branch, a diagnosable failure for a missing branch and for an unreachable HTTPS remote. For an authenticated clone it additionally verifies that the login/password do not leak into the structured and raw task output.

The SSH suite uses a separate typed fixture and verifies a successful Git clone over SSH, playbook execution on an SSH target, a safe failure with a wrong key and secret rotation without replacing the key ID. Two encrypted test key pairs are generated in the ignored `build/test-fixtures/ssh`; private keys are included neither in Git nor in the Docker build context. Strict `known_hosts` checking is not tested on the pinned `v2.19.8`, because the corresponding configuration is present only in the newer upstream `develop`.

The task lifecycle suite launches a safe long-running playbook, waits for the marker of actual execution and verifies a regular stop and a force-stop. In both cases the task moves to `stopped`, and the step after the pause is not executed.

Ansible code is taken only from the trusted `test-environment/fixtures/ansible`, packaged by Compose into a local read-only Git volume with the `main` and `bookwright-fixture-ref` branches. No external code is executed on an API-triggered run.

The full set of Bookwright infrastructure self-tests and Semaphore product tests:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
./gradlew spotlessCheck test -DSTAND=semaphore
```

## Research materials

- `semaphore-ui-testing-assessment-plan.md` — overall plan;
- `semaphore-testing-component-map.md` — component map;
- `outputs/issues-assessment/` — full issue registry;
- `test-environment/api-map.md` — API map and automation priorities;
- `test-environment/legacy-qa-review.md` — review of the legacy UI tests and manual scenarios;
- `test-environment/configuration-testing-overview.md` — matrix of client configurations and reference profiles;
- `test-environment/smoke-report.md` — environment verification results.
- `test-environment/known-defects.md` — summary of current defects with priorities and reproduction steps.
- `test-environment/schedule-execution-defect.md` — reproducible cron/run-at execution defect.
- `test-environment/dynamic-runner-one-off-exit-defect.md` — reproducible one-off runner exit defect.
- `test-environment/runner-unavailable-routing-defect.md` — fail-fast instead of a recoverable queue when no matching runner is available.
- `test-environment/remote-runner-survey-secrets-defect.md` — loss of secret survey variables on remote dispatch.
- `test-environment/survey-default-validation-defect.md` — missing enum default validation in `v2.19.8`.

The Semaphore source code is stored locally in `/semaphore/` and is excluded from this repository.

## Safe verification of an external Semaphore

`externalTest` runs only read-only scenarios tagged `external`: health, password login,
system info and the list of available projects. It does not create projects, users, templates or tasks and
does not depend on local Git/SSH/runner fixtures.

The address and credentials are set explicitly through the environment, so that the full local
suite is not accidentally pointed at a user environment and the password is not passed as a launcher script argument:

```bash
export API_BASE_URL=https://semaphore.example.test/api/
export API_USERNAME=qa-reader
export API_PASSWORD='set-from-secret-storage'
scripts/run-external-tests.sh
```

Additional Gradle arguments are passed after the script name. For self-signed TLS the existing
`-Dbookwright.test.ssl.trustStore=...` and
`-Dbookwright.test.ssl.trustStorePassword=...` can be passed. The regular `apiTest` remains the local full suite and
is never invoked by the external environment launcher.

## [Testing Pull Requests of the Application Repository](docs/application-pr-testing.md)

## Two modes

### Normal mode (default)

The test PR description contains no `Application-PR:` line. The application repository is not cloned,
the application is not built, and no temporary Docker image is created. The image from the profile manifest
(`test-environment/profiles/<profile>/profile.yaml`, key `semaphore_image`) is used — exactly as before.

```text
clone tests → pull semaphore_image → start application → run tests
```

No additional steps are required for regular test development.

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
to test: its commits are already in the application's main branch. The pipeline loudly reports the reason and
falls back to normal mode — it takes the image from the profile manifest instead of pinning the tests
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

The tag of the temporary image contains the PR number and the full SHA of its HEAD commit:

```text
ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-abc123456789...
```

* two different commits of the same PR produce different images;
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
| No `Application-PR:` in the description | no cloning, no build, no temporary image |
| Application PR is closed or merged | fallback to normal mode, no build |
| The run is not a test PR | no description in the context, normal mode |

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

Resolving without any side effects:

```bash
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
manifest otherwise. Useful build variables: `APP_BUILD_PLATFORM` (defaults to
`linux/amd64`), `APP_DOCKERFILE` (defaults to `deployment/docker/server/Dockerfile`),
`APP_BUILD_PUSH`.

## Logging

Normal mode:

```text
Application source: Docker image
Application image: semaphoreui/semaphore:v2.19.12
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
`application.pull.request`, `semaphore.image`, `semaphore.source.commit`.

## Error handling

| Situation | Behavior |
| --- | --- |
| The application PR does not exist | `Application PR #123 not found in <repo>`, the pipeline fails |
| No access to the repository | `Unable to access application repository <repo>`, the pipeline fails |
| The SHA could not be determined | `Unable to resolve the HEAD SHA of application PR #123`, the pipeline fails |
| Two `Application-PR:` lines in the description | the pipeline fails, no choice between them is made |
| `Application-PR:` does not look like a PR reference | the pipeline fails, reporting the original value |
| The PR received a new commit during the build | the build is aborted with an explicit out-of-sync message |
| The image could not be built | the pipeline fails, the Docker build logs remain in the step output |
| The image could not be pushed | the pipeline fails after verifying that the image is really missing from the registry |
| The image is not available for pull | the tag is treated as missing, a build and push are performed |


## Local development

The following environment variables can be used to specify a custom test repository and branch:

| Environment Variable | Description                                | Default                                                |
| -------------------- | ------------------------------------------ | ------------------------------------------------------ |
| `TEST_REPOSITORY`    | URL or path to the test repository         | `https://github.com/semaphoreui/integration-tests.git` |
| `TEST_BRANCH`        | Branch containing the test fixtures to use | `main`                                                 |

### Running tests

Set the required environment variables and start the test environment:

```bash
./test-environment/profile up core-sqlite-local
```

Then run the tests:

```bash
./test-environment/profile test core-sqlite-local
```

### Cleaning up

To stop the environment and remove all resources created for the test environment, run:

```bash
./test-environment/profile clean core-sqlite-local --yes
```

