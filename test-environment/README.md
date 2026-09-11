# Local test environment

The `core-sqlite-local` profile: a minimal Semaphore UI `v2.19.12` stand with SQLite, local task execution, and a trusted Git fixture.

The profile manifest lives in `profiles/<profile>/profile.yaml`. It pins the Semaphore version, installation method, DBMS, execution mode, and capabilities. The lifecycle command reads the manifest, uses a stable Compose project name, and records the actual configuration and image digests in `build/allure-results/environment.properties`.

Five baseline profiles and twelve feature profiles are available:

| Profile | DBMS | Purpose |
|---|---|---|
| `core-sqlite-local` | SQLite | fast primary baseline |
| `core-postgres-local` | PostgreSQL 14.3 | black-box verification of the SQL dialect and migrations on a clean PostgreSQL |
| `core-mysql-local` | MySQL 8.4 | black-box verification of the MySQL dialect and migrations |
| `core-mariadb-local` | MariaDB 10.11 | MariaDB compatibility check via the MySQL dialect |
| `prod-postgres-runner` | PostgreSQL 14.3 | production-like server → DB → persistent remote runner |
| `feature-ssh-local` | SQLite | Git over SSH, Ansible SSH target, and key material protection |
| `feature-git-https` | SQLite | private Git over HTTPS, Basic Auth, trusted self-signed CA, and credentials protection |
| `feature-parallel-tasks-cmd-git` | SQLite | parallel task isolation with the command-line Git client |
| `feature-parallel-tasks-go-git` | SQLite | parallel task isolation with the Go Git client |
| `feature-oidc-local` | SQLite | browser login via Dex, session/logout, provisioning, and negative account/provider scenarios |
| `feature-proxy-oidc` | PostgreSQL 14.3 | OIDC via NGINX, HTTPS, and non-root public path `/semaphore` |
| `feature-ldap-tls` | SQLite | LDAPS bind/search, user provisioning/reuse, logout, and negative credential/account scenarios |
| `feature-totp-local` | SQLite | API and browser TOTP: Security/QR, challenge, invalid passcode, and recovery lifecycle |
| `feature-encryption-rotation` | PostgreSQL 14.3 | keyring hot reload, mixed-key reads, vault rekey, and retired key removal |
| `feature-schedule-timezone` | SQLite | cron/run-at execution in `Pacific/Kiritimati`; local defect reproducer |
| `feature-dynamic-runner` | SQLite | webhook-launched one-off runner; defect reproducer for a process that never exits |
| `feature-shell-output` | SQLite | strict defect reproducer for the loss of `stdout`/`stderr` in a short task |

The shared Semaphore configuration and the Git fixture live in `compose.base.yml`, while profiles only add a DB/execution-specific overlay. All of them publish Semaphore on port `3000`, so only one profile may be running at a time.

## Startup

```bash
test-environment/profile up core-sqlite-local
```

After startup the UI is available at <http://localhost:3000>.

- user: `admin`
- password: `test-password`

## Status and logs

```bash
test-environment/profile ps core-sqlite-local
test-environment/profile logs core-sqlite-local
test-environment/profile logs core-sqlite-local --follow
```

Without the flag, the `logs` command prints a final snapshot of the logs of all services in the profile, which is suitable for CI diagnostics. The `--follow` flag enables interactive tailing.

## Shutdown

```bash
test-environment/profile down core-sqlite-local
```

SQLite is stored in a named Docker volume and persists between restarts.

Fully recreating a profile together with its volume requires explicit confirmation:

```bash
test-environment/profile clean core-sqlite-local --yes
test-environment/profile up core-sqlite-local
```

Product API tests with a readiness check and Allure metadata:

```bash
test-environment/profile test core-sqlite-local
```

After bringing up the same profile, the minimal browser smoke is run separately:

```bash
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
```

It verifies password login, a real launch of an API-prepared template through the form, and client-side validation of an empty project name. The validation scenario additionally proves that `POST /api/projects` was never sent.

Listing the profiles and showing the manifest of the selected profile:

```bash
test-environment/profile list
test-environment/profile show core-sqlite-local
```

Direct invocation of `docker compose -f test-environment/compose.yml ...` is kept for diagnostics and backward compatibility, but the primary launch interface is the `profile` command.

## SQL matrix

Switching profiles while preserving the data of each DBMS:

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

Each profile uses its own Compose project and volumes. `down` preserves the DB, while `clean <profile> --yes` deletes only the volumes of the selected profile.

The MySQL 8.4 and MariaDB 10.11 versions are pinned in the test matrix and passed the core suite on Semaphore `v2.19.8`. The older official Compose examples use MySQL 8.0 and MariaDB 10.8; they can be added to the compatibility set later, once the minimum supported versions are settled.

## Remote runner

The production-like profile uses the same PostgreSQL overlay, enables `SEMAPHORE_USE_REMOTE_RUNNER`, and starts `semaphoreui/runner:v2.19.12` as a separate service:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

On first start the runner registers via a test global registration token and stores the issued long-lived token in `runner-data`. Auto-registration creates a global runner with `is_default=false`, and tasks without a runner tag only select default runners. Therefore the one-shot `runner-configure` logs in via the admin API after registration, sets `is_default=true`, and the lifecycle does not declare the profile ready until this configuration completes successfully.

The Git fixture is mounted at the same path `/fixtures/ansible` in both the server and the runner. Otherwise the local repository would be available to the server but missing from the actual task execution environment.

The API suite additionally verifies that the runner is active, registered, assigned as default, has `online` status, and sends heartbeats. Successful task/output and stop/force-stop scenarios with remote mode enabled confirm actual execution on the runner.

On `v2.19.8` the profile also contains a known-defect canary: a secret survey variable is lost before remote dispatch, although the same launch passes with local execution. The canary does not print the secret value; the upstream fix #4086 and the criterion for removing the workaround are described in `remote-runner-survey-secrets-defect.md`.

## SSH feature profile

```bash
test-environment/profile down prod-postgres-runner
test-environment/profile up feature-ssh-local
test-environment/profile test feature-ssh-local
```

The profile builds a minimal Alpine SSH fixture and mounts the local Git repository into it read-only. A single encrypted Semaphore access key is used both to clone `ssh://fixture@ssh-fixture:22/repositories/ansible` and for Ansible to connect to `ssh-fixture`. A separate negative scenario verifies an invalid key and a clone failure. Create/get/list responses, structured output, and raw output are checked for the absence of the private key and passphrase.

The key pair is generated during `profile up` in the Git-ignored directory `build/test-fixtures/ssh`. Only the public key is mounted into the container, while the private key stays outside the Docker build context and is used by the Java test only for the local API. The fixture image version is recorded in the Allure environment.

## Private HTTPS Git feature profile

The private HTTPS Git fixture brings up a pinned NGINX, publishes a bare repository only inside the Compose network, and requires Basic Auth. A self-signed CA is generated in the Git-ignored `build/test-fixtures/git-https` and passed to child Git processes via an allowed environment variable:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-git-https
test-environment/profile test feature-git-https
```

## Schedule timezone feature profile

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

The profile sets `SEMAPHORE_SCHEDULE_TIMEZONE=Pacific/Kiritimati`, passes the same zone to the test JVM, and records it in the Allure environment. The tests compute the nearest cron occurrence in that zone and a separate `run_at`, then wait for the automatically created task by `schedule_id` and its successful output.

On release `v2.19.8` the profile is currently a defect reproducer: the API stores active cron and one-shot schedules, but no task appears. It is deliberately excluded from the CI matrix until Linux confirmation and a decision on the upstream issue. The full report is in `schedule-execution-defect.md`.

## Shell output feature profile

```bash
test-environment/profile down feature-schedule-timezone
test-environment/profile up feature-shell-output
test-environment/profile test feature-shell-output
```

On release `v2.19.12` a short, successfully completed Bash task may retain only one of the
process streams: `stdout` or `stderr`. The profile runs only the strict `ShellOutputTest`,
including the background-child scenario, and remains a manual red reproducer until the already
existing upstream fixes land in stable. The full report is in `shell-output-loss-defect.md`.

## OIDC feature profile

```bash
test-environment/profile down feature-schedule-timezone
test-environment/profile up feature-oidc-local
test-environment/profile test feature-oidc-local
```

The profile starts a pinned Dex `v2.45.1` and the browser-based `uiTest`. The positive path verifies the provider button, credentials on the IdP, the OAuth callback, the Semaphore session via `/api/user`, the return to `/tokens`, and provisioning of a non-admin external user. A repeated login proves reuse of the same user ID, logout clears the session, and separate negative paths protect the local account on an email match and create no session when discovery is refused. `SEMAPHORE_WEB_ROOT` is set explicitly, and the `username`/`name` claims are mapped to `email`, because the local Dex connector does not issue `preferred_username`.

## HTTPS proxy + OIDC feature profile

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-proxy-oidc
test-environment/profile test feature-proxy-oidc
```

The profile uses PostgreSQL 14.3, Dex, and a pinned NGINX `1.27.5-alpine`. Semaphore is published as `https://localhost:3443/semaphore`; the proxy preserves the subpath and supports WebSocket upgrade. The lifecycle generates a localhost certificate and a PKCS12 truststore in `build/test-fixtures/proxy-tls`, passes the truststore only to the test JVM, and waits for readiness via the trusted HTTPS endpoint.

The same OIDC suite verifies discovery, callback, return path, provisioning/reuse, logout, and negative account/provider paths. After a successful login, `HttpOnly`, `Secure`, and the cookie path `/` are confirmed separately. The certificate, truststore, and private key are disposable fixtures and are not committed to Git.

## LDAPS feature profile

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-ldap-tls
test-environment/profile test feature-ldap-tls
```

The profile starts a pinned OpenLDAP `1.5.0` with self-signed TLS and three directory users. Semaphore connects over LDAPS on `636`, performs a service bind, a search by `uid`, a user bind, and maps `uid`/`cn`/`mail`. Four scenarios verify provisioning of an external user, reuse of the same ID after logout, rejection of a wrong password without provisioning, and protection of the local admin on an email match.

## TOTP feature profile

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-totp-local
test-environment/profile test feature-totp-local
```

The profile explicitly enables TOTP and recovery and runs the shared `totpTest`. The API scenario creates a separate non-admin user, performs self-enrollment, obtains the `otpauth://` material, verifies the `TOTP_REQUIRED` state, the rejection of a tampered passcode, and a successful login with an RFC 6238 code. Then a recovery code restores the session and removes the old TOTP binding; a repeated enrollment issues a new recovery code, while the old one receives `INVALID_RECOVERY_CODE`.

An independent browser scenario enables TOTP via the Security tab, verifies that the QR loads and the recovery code is shown, logs out, goes through the password → challenge flow with negative and positive passcode checks, and then uses the UI recovery form. After recovery, the API confirms that the TOTP binding has been removed.

The OTP secret, passcode, and recovery code never end up in HTTP attachments or raw Allure result JSON. Steps accept redacted request objects: Allure's visual `hidden` mode alone is not enough, since it leaves the original value inside the downloadable artifact. For the browser scenario, only safe browser diagnostics are saved on failure; screenshot, HTML, and Playwright trace are disabled because they may contain the QR, the recovery code, or the entered passcode.

## Dynamic one-off runner

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

The profile registers a global runner with a webhook, assigns it as default, and on `start` launches a separate `semaphore runner start --no-config` with `SEMAPHORE_RUNNER_ONE_OFF=true`. The launcher records the `webhook_start`, `runner_started`, and `webhook_finish` events and the actual exit code of the process, but does not stop the process itself.

On `v2.19.8` the task completes successfully and the server calls `finish`, yet the runner stays alive. The test is intentionally red because it expects `runner_exited` with code `0`. The profile is not part of the regular CI matrix; the full reproduction and source code analysis are in `dynamic-runner-one-off-exit-defect.md`.

## DB encryption key rotation

```bash
test-environment/profile encryption-rotation-test feature-encryption-rotation
```

The specialized command recreates only the volumes of this profile and runs three phases on PostgreSQL. First, Semaphore creates an encrypted `login_password` access key and executes a template with the old primary. Then the keyring atomically switches to the new primary without a restart: the old secret is still readable, while the new one is already written with a different key ID. The `semaphore vault check` command must show both `retired, rekey pending` and `active` at the same time.

After `semaphore vault rekey --backup`, the lifecycle requires the state `0 rows — retired, SAFE TO REMOVE` for the specific old key ID, removes it from the keyring, and runs the saved template again. The final phase also creates a new secret, so it verifies both reading the rekeyed ciphertext and writing after the retired key has been removed. The generated test-only keys live in the Git-ignored `build/test-fixtures/encryption-rotation` and are not committed to Git.

## N-1 → current upgrade

Four isolated profiles verify the upgrade of the release image `v2.19.8` → `v2.19.12` while keeping the same DB:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
test-environment/profile down upgrade-postgres-local
test-environment/profile upgrade-test upgrade-mysql-local
test-environment/profile down upgrade-mysql-local
test-environment/profile upgrade-test upgrade-mariadb-local
test-environment/profile down upgrade-mariadb-local
```

The command deletes only the volumes of the selected upgrade profile, brings up N-1, creates a linked persisted fixture, and executes a task. Then it recreates only the server on the current image, verifies the persisted project/access key/repository/inventory/template/schedule/task output, re-executes the old template, and runs the regular core suite. Both image references and digests are recorded in the Allure environment.

The `v2.19.8` → `v2.19.12` pair is the current upgrade gate and passed successfully on SQLite and
PostgreSQL in Linux CI on 2026-09-04. MySQL 8.4 and MariaDB 10.11 are wired into the same weekly
workflow, passed locally on 2026-09-09, and remain pending until their first successful Linux run.
The previous pair `v2.19.7` → `v2.19.8` also read the persisted
project/access key/repository/inventory/template/schedule/task and re-executed the template on
both DBMSs on 2026-08-19. The upgrade workflow remains a separate observed gate because it verifies
the migration of persisted state between release images.
Details are in `v2.19.8-regression-report.md`; the historical schema defect
`v2.19.6` → `v2.19.7` is preserved in `upgrade-report.md`.

## CI profiles

The profiles are wired into three GitHub Actions workflows:

| Workflow | Trigger | Profiles |
|---|---|---|
| `CI` | pull request and push to `main` | API suite and Chromium UI smoke on `core-sqlite-local` after the framework quality gate |
| `Configuration matrix` | daily at `01:30 UTC`, manually | `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner`, `feature-ssh-local`, `feature-git-https`, `feature-oidc-local`, `feature-proxy-oidc`, `feature-ldap-tls`, `feature-totp-local`, `feature-encryption-rotation` |
| `Release upgrade` | Sunday at `03:30 UTC`, manually | `upgrade-sqlite-local`, `upgrade-postgres-local`, `upgrade-mysql-local`, `upgrade-mariadb-local` |

Each matrix profile runs on its own runner, so the shared port `3000` causes no conflicts. After execution the workflow keeps JUnit/HTML/Allure artifacts, adds `profile ps` and a final snapshot of the Compose logs on failure, and then removes only the containers and volumes of the selected profile.

On stable `v2.19.12` the `profile test` command runs JUnit classes sequentially because of a
confirmed race in the product output collector. This does not disable concurrent execution checks:
`ProjectConcurrencyApiTest` itself launches several Semaphore tasks and verifies queue admission.
The strict concurrent/short-output contract is isolated in `feature-shell-output`.

In the manual `Configuration matrix`, the inputs `include_schedule_investigation=true` and
`include_shell_output_investigation=true` add the corresponding defect profiles only to the
selected run. Their failure, expected until the fix, does not pollute the daily gate but preserves
Linux diagnostics for confirming the defects.

The raw Allure results of each job are uploaded as a separate artifact. The final reusable workflow downloads them, generates an independent HTML report for each profile, and uploads the combined site as a downloadable artifact. The build runs even after a test failure, including on pull requests, so the diagnostics of a red run can be opened without GitHub Pages. Successful trusted `main` runs are also published to the public [GitHub Pages history](https://semaphoreui.github.io/integration-tests/); pull-request and external-environment runs remain artifact-only.

The `fixture-init` Compose service creates a separate Git repository from `fixtures/ansible` with the `main` and `bookwright-fixture-ref` branches. Initialization is safely repeatable for an existing volume and fails with an error when a Git command fails. The repository is mounted into Semaphore read-only and is used to verify the task lifecycle, branch selection, and a missing ref. `long-running.yml` contains a start marker, a controlled pause, and a completion marker for deterministic stop/force-stop verification.

## Quick check

```bash
curl http://localhost:3000/api/ping
```

Expected response: `pong`.

## API smoke

The smoke creates a separate project, verifies it, and deletes it in the cleanup block:

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
