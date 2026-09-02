# Local test environment

Profile `core-sqlite-local`: a minimal Semaphore UI `v2.19.7` stand with SQLite, local task execution, and a trusted Git fixture.

The profile manifest lives in `profiles/<profile>/profile.yaml`. It pins the Semaphore version, installation method, database, execution mode, and capabilities. The lifecycle command reads the manifest, uses a stable Compose project name, and writes the actual configuration and image digests to `build/allure-results/environment.properties`.

Five reference profiles are available:

| Profile | Database | Purpose |
|---|---|---|
| `core-sqlite-local` | SQLite | fast primary baseline |
| `core-postgres-local` | PostgreSQL 14.3 | black-box check of the SQL dialect and migrations on plain PostgreSQL |
| `core-mysql-local` | MySQL 8.4 | black-box check of the MySQL dialect and migrations |
| `core-mariadb-local` | MariaDB 10.11 | MariaDB compatibility check via the MySQL dialect |
| `prod-postgres-runner` | PostgreSQL 14.3 | production-like server → DB → persistent remote runner |

The shared Semaphore and Git fixture configuration lives in `compose.base.yml`, while profiles only add a DB/execution-specific overlay. All of them publish Semaphore on port `3000`, so only one profile should be running at a time.

## Startup

```bash
test-environment/profile up core-sqlite-local
```

Once started, the UI is available at <http://localhost:3000>.

- user: `admin`
- password: `test-password`

## Status and logs

```bash
test-environment/profile ps core-sqlite-local
test-environment/profile logs core-sqlite-local
test-environment/profile logs core-sqlite-local --follow
```

Without a flag, the `logs` command prints a final snapshot of the logs of all the profile's services, which suits CI diagnostics. The `--follow` flag enables interactive tailing.

## Shutdown

```bash
test-environment/profile down core-sqlite-local
```

SQLite is stored in a named Docker volume and persists across restarts.

Fully recreating a profile along with its volume requires explicit confirmation:

```bash
test-environment/profile clean core-sqlite-local --yes
test-environment/profile up core-sqlite-local
```

Product API tests with a readiness check and Allure metadata:

```bash
test-environment/profile test core-sqlite-local
```

List profiles and show the manifest of a selected profile:

```bash
test-environment/profile list
test-environment/profile show core-sqlite-local
```

Direct invocation of `docker compose -f test-environment/compose.yml ...` is kept for diagnostics and backward compatibility, but the primary launch interface is the `profile` command.

## SQL matrix

Switching profiles while preserving each database's data:

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

Each profile uses its own Compose project and volumes. `down` preserves the database, while `clean <profile> --yes` deletes only the volumes of the selected profile.

The MySQL 8.4 and MariaDB 10.11 versions match the images on which the upstream Semaphore `v2.19.7` CI runs migration and integration jobs. The older official Compose examples use MySQL 8.0 and MariaDB 10.8; they can be added to the compatibility set later once the minimum supported versions are pinned down.

## Remote runner

The production-like profile uses the same PostgreSQL overlay, enables `SEMAPHORE_USE_REMOTE_RUNNER`, and runs `semaphoreui/runner:v2.19.7` as a separate service:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

On first start, the runner registers via a test global registration token and stores the issued long-lived token in `runner-data`. Auto-registration creates a global runner with `is_default=false`, and tasks without a runner tag pick only default runners. Therefore a one-shot `runner-configure` logs in via the admin API after registration, sets `is_default=true`, and the lifecycle does not declare the profile ready until this configuration completes successfully.

The Git fixture is mounted at the same path `/fixtures/ansible` in both the server and the runner. Otherwise the local repository would be visible to the server but missing from the actual task execution environment.

The API suite additionally verifies that the runner is active, registered, assigned as default, has `online` status, and sends heartbeats. Successful task/output and stop/force-stop scenarios with remote mode enabled confirm actual execution on the runner.

## N-1 → current upgrade

Two isolated profiles verify the release image upgrade `v2.19.6` → `v2.19.7` while keeping the same database:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

The command deletes only the volumes of the selected upgrade profile, brings up N-1, creates a linked persisted fixture, and runs a task. It then recreates only the server on the current image, verifies the preserved project/access key/repository/inventory/template/schedule/task output, re-runs the old template, and launches the regular core suite. Both image references and digests are recorded in the Allure environment.

On the verified release pair, both profiles currently fail as expected due to a product defect: `v2.19.6` adds access key columns from the `v2.20.1` migration, while `v2.19.7` removes the migration and the model fields without a rollback. Details and fix criteria are in `upgrade-report.md`. The test must not be marked skipped or as an expected failure: a green result must mean the published upgrade path is genuinely restored.

## CI profiles

The profiles are wired into three GitHub Actions workflows:

| Workflow | Trigger | Profiles |
|---|---|---|
| `CI` | pull request and push to `main` | `core-sqlite-local` after the framework quality gate |
| `Configuration matrix` | daily `01:30 UTC`, manual | `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner` |
| `Release upgrade` | Sunday `03:30 UTC`, manual | `upgrade-sqlite-local`, `upgrade-postgres-local` |

Each matrix profile runs on its own runner, so the shared port `3000` causes no conflicts. After a run, the workflow saves JUnit/HTML/Allure artifacts; on failure it adds `profile ps` and a final snapshot of the Compose logs, and then removes only the containers and volumes of the selected profile.

The raw Allure results of each job are uploaded as a separate artifact. A final reusable workflow downloads them, generates an independent HTML report for each profile, and uploads the combined site as a downloadable artifact. The build runs even after a test failure, including on pull requests, so diagnostics for a red run can be opened without GitHub Pages. Pages deployment is paused while the private repository remains on a plan without private Pages.

The Compose service `fixture-init` creates a dedicated Git repository from `fixtures/ansible` with the `main` and `bookwright-fixture-ref` branches. Initialization is safely repeatable for an existing volume and fails when a Git command fails. The repository is mounted into Semaphore read-only and is used to verify the task lifecycle, branch selection, and a missing ref. `long-running.yml` contains a start marker, a controlled pause, and a completion marker for deterministic stop/force-stop verification.

## Quick check

```bash
curl http://localhost:3000/api/ping
```

Expected response: `pong`.

## API smoke

The smoke test creates a dedicated project, verifies it, and deletes it in the cleanup block:

```bash
node test-environment/api-smoke.mjs
```

The stand address and credentials can be overridden:

```bash
SEMAPHORE_BASE_URL=http://localhost:3000 \
SEMAPHORE_USERNAME=admin \
SEMAPHORE_PASSWORD=test-password \
node test-environment/api-smoke.mjs
```
