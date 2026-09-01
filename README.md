# Semaphore UI Test Automation

A test project for [Semaphore UI](https://github.com/semaphoreui/semaphore), built on top of [Bookwright v1.4.0](https://github.com/dantro86/bookwright/releases/tag/v1.4.0) (`b30d7e6`).

## Stack

* Java 21;
* Gradle;
* JUnit 5;
* Retrofit and OkHttp;
* Playwright;
* Guice;
* AssertJ;
* Allure;
* Awaitility.

The framework is adapted for Semaphore while preserving the Bookwright v1.4.0 architecture: API and steps are separated as `target/domain`, scenario data belongs to typed fixtures, and precondition state is accessed only through the typed `TestStore`.

## Local Environment

```bash
test-environment/profile up core-sqlite-local
```

Semaphore will be available at http://localhost:3000.

## First API Smoke Test

```bash
test-environment/profile test core-sqlite-local
```

The command automatically checks readiness and adds the exact environment configuration to the Allure environment. To stop the environment while preserving the SQLite volume, use `test-environment/profile down core-sqlite-local`. Completely removing the state requires an explicit `test-environment/profile clean core-sqlite-local --yes` command.

The same test suite runs against PostgreSQL, MySQL, and MariaDB without copying the tests. Profiles use the same port and are started sequentially:

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

The production-like setup runs tasks on a separate persistent runner:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

The runner is registered automatically, stores a long-lived token in a separate volume, and is assigned as the default runner through the admin API. A separate API test verifies `active`, `registered`, `is_default`, `online`, and heartbeat before task scenarios are started.

Published image upgrade testing against a persistent SQLite or PostgreSQL environment is launched with a separate command:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

The current `v2.19.6 → v2.19.7` upgrade path exposes a reproducible access key schema incompatibility and remains red until the product is fixed. The diagnostics are documented in `test-environment/upgrade-report.md`.

## CI

GitHub Actions are split by cost and purpose:

* `CI` runs for every pull request and push to `main`: it first performs the framework quality gate and then runs the core API suite on `core-sqlite-local`;
* `Configuration matrix` runs daily at `01:30 UTC` and manually, testing PostgreSQL, MySQL, MariaDB, and production-like PostgreSQL with a persistent runner;
* `Release upgrade` runs weekly on Sundays at `03:30 UTC` and manually, testing the `v2.19.6 → v2.19.7` upgrade on SQLite and PostgreSQL.

Matrix jobs use separate GitHub-hosted runners and run in parallel with `fail-fast: false`. JUnit, HTML reports, Allure results, and container diagnostics on failure are preserved as artifacts. The upgrade workflow is not part of the PR gate and remains red while the confirmed product incompatibility exists — a successful job must indicate that the upgrade path actually works again.

After every CI, nightly matrix, or release-upgrade run, Allure is automatically built into a ready-to-use HTML site and uploaded as the `allure-html-<run>-<attempt>` artifact. Each Allure report is generated in single-file mode: after downloading the archive, simply extract it and open `index.html` by double-clicking it — no local HTTP server is required. For a matrix run, the landing page contains a separate report for each profile, so results from different database engines are not mixed across retries. GitHub Pages publication is prepared but currently suspended because the current plan does not support Pages for private repositories.

The test verifies health, invalid and valid login, creates an isolated project, and exercises the main resource chain:

```text
project → access key → local Git repository → inventory → task template
→ task execution → success status → output marker
→ inactive cron schedule → schedule verification
→ guest RBAC → assigned project access → forbidden mutation
→ unassigned project isolation
```

After the test, Bookwright LIFO cleanup removes project data in reverse order. RBAC uses one stable fixture user, `bookwright-rbac-guest`: repeated runs reuse this user because Semaphore v2.19.7 does not allow a user to be deleted after a login session has been created.

A separate RBAC suite verifies the built-in `manager` and `task_runner` contracts. A Manager can create project resources and run tasks, but cannot delete a project or manage its members. A Task Runner can run tasks but receives `403` when attempting to modify resources, the project, or its members.

A separate security smoke test creates a `login_password` access key with a unique marker, uses it as an inventory credential when running a task, and verifies that the plaintext value is absent from the create/get/list APIs, structured and raw task output, Allure, and JUnit artifacts.

The Git suite verifies task execution from an explicitly selected branch, a diagnosable failure for a missing branch, and an unavailable HTTPS remote. For authenticated cloning, it additionally verifies that the login/password do not appear in structured or raw task output.

The task lifecycle suite runs a safe long-running playbook, waits for a marker confirming actual execution, and verifies both regular stop and force-stop. In both cases, the task transitions to `stopped`, and the step after the pause is not executed.

Ansible code is taken only from trusted fixtures, `test-environment/fixtures/ansible/smoke.yml` and `long-running.yml`, which are packaged by Compose into a local read-only Git volume with the `main` and `bookwright-fixture-ref` branches. External code is not executed when tasks are started through the API.

The complete set of Bookwright infrastructure self-tests and Semaphore product tests:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
./gradlew spotlessCheck test -DSTAND=semaphore
```

## Research Materials

* `semaphore-ui-testing-assessment-plan.md` — overall plan;
* `semaphore-testing-component-map.md` — component map;
* `outputs/issues-assessment/` — complete issue registry;
* `test-environment/api-map.md` — API map and automation priorities;
* `test-environment/configuration-testing-overview.md` — client configuration matrix and reference profiles;
* `test-environment/smoke-report.md` — environment verification results.

The Semaphore source code is stored locally in `/semaphore/` and excluded from this repository.
