# Contributing

Thank you for helping improve the Semaphore UI integration tests. This guide covers everything
needed to run the suite locally, in all supported modes, and to get a change merged.

The repository is a black-box test suite. The application under test lives in
[`semaphoreui/semaphore`](https://github.com/semaphoreui/semaphore) and is consumed only as a
Docker image; this repository never contains application source code.

- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Running locally](#running-locally)
  - [1. Checks without Docker](#1-checks-without-docker)
  - [2. The profile lifecycle](#2-the-profile-lifecycle)
  - [3. Available profiles](#3-available-profiles)
  - [4. Running a subset of tests](#4-running-a-subset-of-tests)
  - [5. Browser (UI) tests](#5-browser-ui-tests)
  - [6. Special lifecycles: upgrade and key rotation](#6-special-lifecycles-upgrade-and-key-rotation)
  - [7. Choosing the Semaphore version](#7-choosing-the-semaphore-version)
  - [8. Test fixtures (playbooks, scripts)](#8-test-fixtures-playbooks-scripts)
  - [9. Testing an existing Semaphore instance](#9-testing-an-existing-semaphore-instance)
  - [10. Standalone checks](#10-standalone-checks)
  - [11. Running without Java on the host (`run.sh`, experimental)](#11-running-without-java-on-the-host-runsh-experimental)
  - [12. Reports](#12-reports)
  - [13. Troubleshooting](#13-troubleshooting)
- [Making changes](#making-changes)
- [Before you open a pull request](#before-you-open-a-pull-request)
- [Pull requests and Git conventions](#pull-requests-and-git-conventions)
- [Security rules](#security-rules)

## Prerequisites

| Tool | Needed for | Check |
| --- | --- | --- |
| Java 21 | everything that runs Gradle | `java -version` |
| Docker with Compose v2 (and `buildx`) | every profile; building the application image | `docker compose version` |
| `git` | resolving the application branch | `git --version` |
| `curl` | readiness checks in `test-environment/profile` | `curl --version` |
| Network access to GitHub | resolving/building the application image | |
| `ssh-keygen` | `feature-ssh-local` | |
| `openssl`, `keytool` | `feature-git-https`, `feature-proxy-oidc`, `feature-encryption-rotation` | |
| Playwright Chromium | `uiTest`, `totpTest`, OIDC profiles | `./gradlew playwrightInstallChromium` |
| Python 3.10+ | script unit tests, configuration contract check | `python3 --version` |
| `gh` CLI (optional) | testing an application pull request locally | `gh auth status` |
| Node.js (optional) | the standalone `api-smoke.mjs` | `node --version` |

Notes:

- Gradle is provided by the wrapper (`./gradlew`); do not install it separately.
- On an arm64 machine with only Docker installed you can skip Java and the other host tools and
  use the containerised runner instead; see
  [section 11](#11-running-without-java-on-the-host-runsh-experimental).
- On macOS, `test-environment/profile` falls back to `/opt/homebrew/opt/openjdk@21` if
  `JAVA_HOME` is not set. For direct Gradle calls set it yourself, for example
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- Every profile publishes Semaphore on `localhost:3000` (`feature-proxy-oidc` additionally on
  `3443`). Keep these ports free; only one profile can run at a time.
- The first run of a profile builds the Semaphore image from the application `develop` branch.
  This takes several minutes once per application commit; later runs reuse the image.

## Quick start

```bash
git clone https://github.com/semaphoreui/integration-tests.git
cd integration-tests

./gradlew qualityGate                              # framework checks, no Docker needed
./gradlew playwrightInstallChromium                # once, for browser tests

test-environment/profile up core-sqlite-local      # Semaphore at http://localhost:3000
test-environment/profile test core-sqlite-local    # API suite
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local   # browser smoke
test-environment/profile down core-sqlite-local
```

The local Semaphore login is `admin` / `test-password`.

## Running locally

### 1. Checks without Docker

These run in seconds to minutes and need only Java (and Python for the scripts).

```bash
./gradlew qualityGate          # spotlessCheck + frameworkTest + JaCoCo >= 0.60 + version/changelog checks
./gradlew frameworkTest        # framework unit and architecture tests only
./gradlew spotlessCheck        # formatting check
./gradlew spotlessApply        # fix formatting (Java, Gradle, test-environment YAML, scripts/*.sh)
python3 -m unittest discover -s scripts/tests -p 'test_*.py'   # scripts/app-source.sh and Allure site tooling
```

`qualityGate` is the first job of the CI pull-request pipeline; run it before every push.

### 2. The profile lifecycle

A **profile** is one Semaphore configuration: a manifest
(`test-environment/profiles/<id>/profile.yaml`) plus a Docker Compose overlay. All actions go
through a single script, `test-environment/profile`:

```bash
test-environment/profile list                       # all profile ids
test-environment/profile show <profile>             # print the manifest

test-environment/profile up <profile>               # start and wait until /api/ping returns pong
test-environment/profile test <profile> [Gradle args]   # run the profile's test task
test-environment/profile ps <profile>               # container status
test-environment/profile logs <profile> [--follow]  # snapshot of logs, or tail with --follow
test-environment/profile down <profile>             # stop, KEEP database and fixture volumes
test-environment/profile clean <profile> --yes      # stop and DELETE volumes
```

What `test` does for you, compared to calling Gradle directly:

- re-checks readiness and generates any missing fixture material (keys, certificates);
- runs the task named by the manifest (`apiTest`, `uiTest`, `totpTest`, `webCacheTest`, …) with
  `-DSTAND=semaphore -DSEMAPHORE_PROFILE=<profile>` and the profile's own URLs, truststore and
  test-class filter;
- forces sequential JUnit classes (`-Djunit.jupiter.execution.parallel.enabled=false`);
- records the exact configuration and image digests in
  `build/allure-results/environment.properties`.

Switching profiles — they share port `3000`, so stop one before starting the next:

```bash
test-environment/profile down core-sqlite-local
test-environment/profile up core-postgres-local
test-environment/profile test core-postgres-local
```

Each profile has its own Compose project and volumes, so `down` on one never touches another.
Use `clean --yes` when you need a truly fresh database.

### 3. Available profiles

| Profile | Database | What it covers | How to run |
| --- | --- | --- | --- |
| `core-sqlite-local` | SQLite | fast baseline; the CI pull-request gate | `up` + `test`, then `uiTest` |
| `core-postgres-local` | PostgreSQL 14.3 | same suite on PostgreSQL | `up` + `test` |
| `core-mysql-local` | MySQL 8.4 | same suite on MySQL | `up` + `test` |
| `core-mariadb-local` | MariaDB 10.11 | same suite on MariaDB | `up` + `test` |
| `prod-postgres-runner` | PostgreSQL 14.3 | server → DB → persistent remote runner | `up` + `test` |
| `feature-ssh-local` | SQLite | Git over SSH, Ansible SSH target, key rotation, SSH credential mappings (`host_configs`) | `up` + `test` |
| `feature-git-https` | SQLite | private Git over HTTPS with Basic Auth and self-signed CA, URL credential mapping | `up` + `test` |
| `feature-parallel-tasks-cmd-git` | SQLite | parallel task isolation, command-line Git client | `up` + `test` |
| `feature-parallel-tasks-go-git` | SQLite | parallel task isolation, Go Git client | `up` + `test` |
| `feature-oidc-local` | SQLite | browser OIDC login through Dex | `up` + `test` (runs `uiTest`) |
| `feature-proxy-oidc` | PostgreSQL 14.3 | OIDC behind NGINX, HTTPS, subpath `/semaphore` | `up` + `test` (runs `uiTest`) |
| `feature-ldap-tls` | SQLite | LDAPS bind/search and user provisioning | `up` + `test` |
| `feature-totp-local` | SQLite | TOTP MFA, API and browser | `up` + `test` (runs `totpTest`) |
| `feature-schedule-timezone` | SQLite | cron / `run_at` in a non-UTC timezone | `up` + `test` |
| `feature-shell-output` | SQLite | complete short `stdout`/`stderr` of shell tasks | `up` + `test` |
| `feature-encryption-rotation` | PostgreSQL 14.3 | database encryption keyring rotation | `encryption-rotation-test` only |
| `feature-dynamic-runner` | SQLite | one-off runner via webhook — **known red reproducer** | `up` + `test`, manual only |
| `feature-web-cache-safety` | SQLite | shared-cache cross-user leak — **known red reproducer** | `up` + `test`, manual only |
| `upgrade-sqlite-local`, `upgrade-postgres-local`, `upgrade-mysql-local`, `upgrade-mariadb-local` | as named | `v2.19.8 → develop` upgrade on a preserved database | `upgrade-test` only |
| `external` | user-managed | full suite against an existing Semaphore | see [section 9](#9-testing-an-existing-semaphore-instance) |

"Known red reproducer" profiles are expected to fail until the upstream defect is fixed; they are
not part of any green CI gate. Details on each defect are in
[`test-environment/known-defects.md`](test-environment/known-defects.md) and the
`test-environment/*-defect.md` reports.

More detail on every profile: [`test-environment/README.md`](test-environment/README.md).

### 4. Running a subset of tests

Through the profile script (recommended — keeps sequential execution and Allure metadata). Any
arguments after the profile id are passed to Gradle:

```bash
test-environment/profile test core-sqlite-local --tests 'io.bookwright.tests.semaphore.ScheduleApiTest'
test-environment/profile test core-sqlite-local --tests 'io.bookwright.tests.semaphore.TaskStopTest.runningTaskCanBeStopped'
```

If the profile manifest has its own `test_class` (for example `feature-git-https`,
`feature-shell-output`), those filters are appended after yours. Gradle runs the union of
all `--tests` filters, so your filter widens the run instead of narrowing it. On such profiles use
direct Gradle, as shown next.

Directly with Gradle, when the environment is already up (faster feedback while writing a test):

```bash
./gradlew apiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local \
  -Djunit.jupiter.execution.parallel.enabled=false \
  --tests 'io.bookwright.tests.semaphore.ScheduleApiTest'
```

Useful switches:

| Switch | Effect |
| --- | --- |
| `-Dverbose` | print test stdout/stderr to the console |
| `-Dtest.seed=<n>` | replay a run with identical generated data; the exact command is attached to every Allure test as "Reproduce test data" |
| `-DincludeTags=smoke` / `-DexcludeTags=ui` | tag filter; honoured only by the generic `test` task |
| `-Dapi.base.url=…`, `-Dui.base.url=…`, `-Dapi.username=…` | override any `api.*` / `ui.*` / `teardown.*` config key |

Direct Gradle runs execute test classes in parallel unless you pass
`-Djunit.jupiter.execution.parallel.enabled=false`; the profile script always passes it.

> A test class that "did not run" is usually gated by
> `@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", …)` and silently skipped on other
> profiles. Check the annotation before assuming a problem.

Everything at once (framework self-tests plus product tests; needs a running profile):

```bash
./gradlew spotlessCheck test -DSTAND=semaphore
```

### 5. Browser (UI) tests

Install Chromium once:

```bash
./gradlew playwrightInstallChromium
```

Run the browser smoke against a running core profile:

```bash
test-environment/profile up core-sqlite-local
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
```

Profiles whose manifest sets `test_task: uiTest` or `totpTest` (`feature-oidc-local`,
`feature-proxy-oidc`, `feature-totp-local`) run browser tests from `profile test` directly.

On failure, screenshots, page HTML and a Playwright trace are attached to Allure, except for
tests marked `@SensitiveUi`.

### 6. Special lifecycles: upgrade and key rotation

These profiles are driven by a dedicated action instead of `up` + `test`; the action starts the
environment itself.

Release upgrade (seeds data on `v2.19.8`, swaps the server image to current `develop`, verifies
the preserved data, then runs the API suite):

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
```

The same works for `upgrade-postgres-local`, `upgrade-mysql-local` and `upgrade-mariadb-local`.

Encryption keyring rotation (three phases: seed, hot-reload new primary, `vault rekey` and
verify):

```bash
test-environment/profile encryption-rotation-test feature-encryption-rotation
test-environment/profile down feature-encryption-rotation
```

Both actions wipe that profile's volumes at the start and keep containers and volumes on failure
so that you can inspect them with `ps` / `logs`.

### 7. Choosing the Semaphore version

By default every profile tests the **HEAD commit of the application `develop` branch**
(`semaphore_image: branch:develop` in the manifest). `scripts/app-source.sh` resolves the commit,
reuses a local or registry image for it, and builds one only when none exists. Locally the image is
built for the architecture of your Docker daemon and kept in the local Docker store; it is never
pushed.

CI publishes branch and PR images for both `linux/amd64` and `linux/arm64`, so a local run on
either architecture can usually pull the image instead of building it. An image of the wrong
architecture — a local one, or an older amd64-only tag in the registry — does not count as
existing. The script builds a native one instead of starting a container that would fail with
`exec format error`.

| Goal | Command |
| --- | --- |
| Current `develop` (default) | `test-environment/profile up core-sqlite-local` |
| Another application branch | `APP_BRANCH=release/2.20 test-environment/profile up core-sqlite-local` |
| A published image | `APP_IMAGE=semaphoreui/semaphore:v2.19.14 test-environment/profile up core-sqlite-local` |
| An application pull request | see below |

Testing an application pull request locally (needs `gh` and Docker):

```bash
export APP_PR=123
export APP_IMAGE_REPOSITORY=local/semaphore-ci
export APP_BUILD_PUSH=false
eval "$(scripts/app-source.sh ensure | grep '^APP_')"   # builds or reuses the PR image, exports APP_IMAGE

test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local
```

See what would be tested without building anything:

```bash
scripts/app-source.sh resolve
APP_PR=123 scripts/app-source.sh resolve
APP_LINK_BODY='Application-PR: semaphoreui/semaphore#123' scripts/app-source.sh resolve
```

The resolved image is remembered per profile in `build/test-environment/<profile>/application.env`,
so `test`, `logs` and `down` refer to the image the containers were started with. `clean` forgets
it; run `clean --yes` before `up` if you change `APP_BRANCH` / `APP_IMAGE` for a profile that has
already been started.

Build knobs for `scripts/app-source.sh`: `APP_BRANCH` (default `develop`), `APP_REPOSITORY`,
`APP_BUILD_PLATFORM` (pushed builds default to `linux/amd64,linux/arm64`; set it locally only to
force a platform), `APP_DOCKERFILE` (default `deployment/docker/server/Dockerfile`),
`APP_BUILD_PUSH` (default `false` outside GitHub Actions), `APP_IMAGE_REPOSITORY`,
`APP_IMAGE_TAG_PREFIX`. Full reference:
[Testing Pull Requests of the Application Repository](docs/application-pr-testing.md).

### 8. Test fixtures (playbooks, scripts)

Ansible playbooks, Bash scripts and the Terraform module that Semaphore executes live in
[`test-environment/fixtures/`](test-environment/fixtures/). On `up`, Compose commits that folder
into a local Git repository and serves it inside the Compose network as
`http://fixture-git/fixtures.git` (branches `main` and `bookwright-fixture-ref`). Semaphore clones
it like any other remote.

- **After editing a fixture, run `profile down` then `profile up`.** That recreates the fixture
  repository from your working tree. No commit or push is needed.
- Paths in fixture records are relative to `test-environment/fixtures` (for example
  `ansible/smoke.yml`).

To test fixtures from another Git remote instead of the local checkout:

```bash
export TEST_REPOSITORY=https://github.com/<you>/integration-tests-fixtures.git
export TEST_BRANCH=my-branch
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local
```

That repository must have the same layout at its root as `test-environment/fixtures`.

### 9. Testing an existing Semaphore instance

There are two options, with very different safety properties.

**Read-only checks (safe for real environments).** `externalTest` runs only scenarios tagged
`external` — health, password login, system info, project listing. It creates nothing.
Credentials are passed through the environment, never as arguments:

```bash
export API_BASE_URL=https://semaphore.example.test/api/   # must end with /api/
export API_USERNAME=qa-reader
export API_PASSWORD='set-from-secret-storage'
scripts/run-external-tests.sh
```

Extra Gradle arguments go after the script name. For a self-signed certificate add
`-Dbookwright.test.ssl.trustStore=<path> -Dbookwright.test.ssl.trustStorePassword=<password>`.

**Full suite against a disposable instance (destructive).** The `external` profile starts only
the fixture services and runs the `core-sqlite-local` suite against your instance. It creates and
deletes projects, users, keys and tasks, and leaves the RBAC user `bookwright-rbac-guest` behind by
design. **Never point it at a production instance.**

```bash
export API_BASE_URL=https://semaphore.example.test/api/
export API_USERNAME=admin
export API_PASSWORD='set-from-secret-storage'
export TEST_REPOSITORY=http://host.docker.internal:3080/fixtures.git   # fixture-git as the instance sees it
test-environment/profile up external
test-environment/profile test external
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=external \
  -Dapi.base.url="$API_BASE_URL" -Dui.base.url="${API_BASE_URL%api/}" \
  -Dapi.username="$API_USERNAME" -Dui.user="$API_USERNAME"
test-environment/profile down external
```

The instance needs an admin account, local task execution with `ansible`, `terraform`/`tofu` and
`bash`, and network access to `TEST_REPOSITORY`. `fixture-git` is published on `FIXTURE_GIT_PORT`
(default `3080`). See the "External Semaphore instance" section of
[`test-environment/README.md`](test-environment/README.md).

### 10. Standalone checks

```bash
# Readiness of a running profile
curl http://localhost:3000/api/ping            # expects: pong

# Minimal Node.js API smoke (creates and deletes one project)
node test-environment/api-smoke.mjs
SEMAPHORE_BASE_URL=http://localhost:3000 SEMAPHORE_USERNAME=admin SEMAPHORE_PASSWORD=test-password \
  node test-environment/api-smoke.mjs

# Configuration contract: config.json, env override, invalid port (Docker + Python only)
python3 test-environment/config-contract/check.py
CONFIG_CONTRACT_IMAGE=semaphoreui/semaphore:v2.19.14 python3 test-environment/config-contract/check.py

# API endpoint coverage against the Swagger catalog (needs a running profile)
./gradlew semaphoreApiCoverage -DSEMAPHORE_PROFILE=core-sqlite-local   # run tests + print coverage
./gradlew semaphoreApiCoverageReport                                   # re-print from the last run
```

### 11. Running without Java on the host (`run.sh`, experimental)

`run.sh` runs `test-environment/profile` inside a prebuilt runner container
(`lowswoo/semaphore-test-container`, built from the root `Dockerfile`: JDK 21, Docker CLI with
Compose and Buildx, `git`, `openssl`, `ssh-keygen`, `socat`). Only Docker is needed on the host. It
takes exactly the same arguments as `test-environment/profile`:

```bash
./run.sh list
./run.sh up core-sqlite-local
./run.sh test core-sqlite-local --tests 'io.bookwright.tests.semaphore.ScheduleApiTest'
./run.sh down core-sqlite-local
APP_IMAGE=semaphoreui/semaphore:v2.19.14 ./run.sh up core-sqlite-local
```

How it works:

- the runner starts the stand through the host Docker socket, so containers and volumes are the same
  ones you would get from a host run. `profile ps`, `logs` and `down` work from either side;
- the repository is mounted at its host path, so Compose bind mounts and `build/` outputs (reports,
  generated fixtures) land in your working tree;
- `scripts/runner-entrypoint.sh` forwards the runner's `localhost` ports `3000`, `3003`, `3443`,
  `5556` and `FIXTURE_GIT_PORT` (default `3080`) to the host, so manifests and tests keep
  addressing the stand as `localhost`;
- only these host variables are forwarded, by name, so values never appear in the command line:
  `API_BASE_URL`, `API_USERNAME`, `API_PASSWORD`, `UI_BASE_URL`, `TEST_REPOSITORY`,
  `FIXTURE_GIT_PORT`, `APP_IMAGE`, `APP_SOURCE`, `APP_BRANCH`, `APP_REPOSITORY`, `APP_PR`,
  `APP_SHA`, `GH_TOKEN`, `GITHUB_TOKEN`. Anything else (for example `TEST_BRANCH`) has to be passed
  as a Gradle `-D` argument to `test`.

Limitations:

- the published runner image is currently built for **arm64 only** (Apple Silicon, arm64 Linux).
  On x86_64 hosts, run `test-environment/profile` natively;
- every call runs `docker pull` for the runner image, so it needs registry access;
- the Gradle cache lives in the throwaway container, so each call starts Gradle cold;
- the path is not used by CI. Prefer the native run when you have Java 21 installed.

The root `docker-compose.yml` is a leftover of the previous wrapper and is not used by `run.sh`.

### 12. Reports

| Output | Location |
| --- | --- |
| JUnit XML | `build/test-results/<task>/` |
| Gradle HTML report | `build/reports/tests/<task>/index.html` |
| Raw Allure results | `build/allure-results/` |

View the Allure report locally:

```bash
./gradlew allureServe      # builds and opens the report in a browser
./gradlew allureReport     # static report in build/reports/allure-report/
```

`profile test` does not delete earlier Allure results; remove `build/allure-results` (or run
`./gradlew clean`) when you want a report for a single run.

In CI, every run uploads a self-contained Allure HTML artifact (`allure-html-<run>-<attempt>`) — unzip
it and open `index.html`. Runs of `main` are also published to
[GitHub Pages](https://semaphoreui.github.io/integration-tests/).

### 13. Troubleshooting

| Symptom | What to check |
| --- | --- |
| `up` fails with a port conflict | another profile or process uses `3000`: `docker ps`, then `profile down <other>` |
| `up` times out waiting for `pong` | `test-environment/profile logs <profile>`; the first run may still be building the application image |
| `Java 21 is not available` | set `JAVA_HOME` to a JDK 21 |
| A fixture change is not picked up | run `profile down` + `profile up` so the fixture repository is recreated |
| SSH / TLS / encryption errors after deleting `build/` | generated keys were regenerated but old volumes kept the previous ones: `profile clean <profile> --yes` |
| Wrong application version after changing `APP_*` | `profile clean <profile> --yes`, then `up` again |
| Browser tests fail to launch | `./gradlew playwrightInstallChromium` |
| `exec format error` when the stand starts | the image was built for another architecture: `profile clean <profile> --yes`, then `up` again so a native image is resolved; with `APP_IMAGE`, pick a tag published for your platform |
| `run.sh` fails to pull or start the runner | the runner image is arm64-only; on x86_64 use `test-environment/profile` directly |
| Tests silently skipped | profile gating via `SEMAPHORE_PROFILE`; see [section 4](#4-running-a-subset-of-tests) |
| Unexpected failure on current `develop` | the suite tracks a moving target; check `test-environment/known-defects.md`, then reproduce with `APP_IMAGE=<last release>` to tell a test bug from a product regression |

Inspect the product directly:

```bash
test-environment/profile show <profile>        # compose_project is in the manifest
docker compose -p <compose_project> exec semaphore semaphore version
```

## Making changes

The framework is derived from [Bookwright v1.4.0](https://github.com/dantro86/bookwright/releases/tag/v1.4.0)
and its layering is enforced by architecture tests in `frameworkTest`; shortcuts will fail the
quality gate.

### Adding a test

1. **API contract** — add the Retrofit method to
   `src/main/java/io/bookwright/api/semaphore/<domain>/Semaphore<Domain>Api.java` and
   request/response records under `api/model/semaphore/`; bind new interfaces in `di/ApiModule`.
2. **Steps** — add `@Step` methods to `steps/semaphore/<domain>/<Domain>Steps.java`. Register a
   cleanup action with `teardown.push(...)` for every resource you create. Steps must not import
   steps of sibling domains.
3. **Fixture record** — put all scenario data in a record under `fixtures/semaphore/`, built from
   `MainConfig` + `TestData` so names carry the per-test seed. Never hard-code resource names.
   Redact secrets in `toString()`.
4. **Preconditions** (optional) — extend `Precondition` and read the result only through a typed
   `TestStore` accessor.
5. **Test class** — in `src/test/java/io/bookwright/tests/semaphore/` (or `tests/ui/semaphore/`),
   annotated with `@Api` / `@Ui`, an owner annotation, `@Feature` and `@DisplayName`. Take steps
   and fixtures as method parameters. Gate with `@EnabledIfSystemProperty` if the scenario needs a
   specific profile.
6. **Product fixture** (if needed) — under `test-environment/fixtures/`: deterministic, side-effect
   free, printing only markers. Verify secrets by hash under `no_log`, never print them.

### Adding a profile

Create `test-environment/profiles/<id>/profile.yaml` and a `compose.yml` that `include:`s
`../../compose.base.yml` plus overlays. Manifest keys must be top-level `key: value` lines. To
restrict a profile to specific test classes, set `test_class` to one fully-qualified class name or
several separated by commas (each becomes a `--tests` filter). Add the
profile to `configuration-matrix.yml` only once it is green, and document it in `README.md` and
`test-environment/README.md`.

### Infrastructure changes

- `test-environment/profile` is POSIX `sh` (`set -eu`) — no bashisms.
- New JVM inputs must be forwarded in `configureBookwrightTestRuntime` (`build.gradle.kts`), or the
  test JVM will not see them.
- Application-source logic belongs in `scripts/app-source.sh`; keep
  `docs/application-pr-testing.md` and `scripts/tests/test_app_source.py` in sync.

### Found a product defect?

Keep the assertion honest — do not bend a test to pass. Either keep a canary that explicitly
asserts the current (defective) behaviour inside a green suite, or isolate a failing reproducer in
its own profile outside the green gates. Add a `test-environment/<topic>-defect.md` report and
register it in `test-environment/known-defects.md` with the upstream fix condition.

## Before you open a pull request

```bash
./gradlew spotlessApply
./gradlew qualityGate
python3 -m unittest discover -s scripts/tests -p 'test_*.py'   # if scripts/ changed
test-environment/profile test core-sqlite-local                # if tests or fixtures changed
```

If you touched a specific profile, run that profile too. Also:

- add an entry to `CHANGELOG.md` under `[Unreleased]`. `validateChangelogStyle` rejects bullets
  that repeat the section word (no "- Added …" under "### Added");
- update `README.md` / `test-environment/README.md` when a command, profile or CI behaviour changes.

## Pull requests and Git conventions

- Branch from `main`, using a short descriptive name (`feat/…`, `fix/…`, `test/…`, `docs/…`).
- Commit subjects use conventional prefixes: `feat:`, `fix:`, `test:`, `docs:`, `chore:`, `ci:`,
  `refactor:`.
- Every PR runs the `CI` workflow: quality gate, configuration contract, then the API suite and
  UI smoke on `core-sqlite-local`. The daily configuration matrix and weekly upgrade run
  separately.
- **Testing your change against an application PR:** add one line to the *test PR description*
  (not to a file or commit):

  ```text
  Application-PR: semaphoreui/semaphore#123
  ```

  CI then builds or reuses an image of that PR. Remove the line once the application PR is merged.
  PR mode requires the test branch to live in this repository, not a fork. Details:
  [`docs/application-pr-testing.md`](docs/application-pr-testing.md).
- Never edit the `gh-pages` branch by hand; it is generated.

## Security rules

- Never commit credentials, tokens, or generated key material. `build/`, including
  `build/test-fixtures/` (SSH keys, TLS certificates, encryption keys), is ignored for this reason.
- Pass secrets through environment variables or CI secrets, never as command-line arguments.
- Tests must not leak secrets into API responses, task output, Allure or JUnit artifacts; existing
  suites assert this — follow the same pattern.
- Only `externalTest` / `scripts/run-external-tests.sh` is safe against a real environment.
  Everything else creates and deletes data.
