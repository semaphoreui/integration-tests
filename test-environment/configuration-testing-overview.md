# Semaphore UI Configuration Testing Overview

## Why a separate strategy is needed

Semaphore UI is mostly installed in the customer's infrastructure. A bug may depend not only on the API or UI, but also on the combination of installation method, database, runner, network, authentication, Git, and the way secrets are stored.

Checking the full Cartesian product of these settings is impossible and unnecessary. We use three levels:

1. **Broad configuration checks** — process start-up, readiness, configuration validation, and migrations on a large number of variants.
2. **Baseline profiles** — the same set of critical API scenarios on several realistic configurations.
3. **Feature profiles** — dedicated checks only for LDAP, OIDC, HA, remote runner, encryption rotation, and other special capabilities.

## Confirmed configuration variants

### Installation method

Officially documented:

- Docker and Docker Compose;
- DEB/RPM via a package manager;
- standalone binary and running via systemd;
- Kubernetes via the official Helm chart;
- installation in cloud infrastructure as one of the deployment options.

Snap is marked as deprecated in the documentation and must not be part of the main matrix.

Sources: [Installation overview](https://semaphoreui.com/docs/admin-guide/installation), [Package manager](https://semaphoreui.com/docs/admin-guide/installation/package-manager), [Binary file](https://semaphoreui.com/docs/admin-guide/installation/binary-file), [deprecated Snap](https://semaphoreui.com/docs/administration-guide/installation/snap).

The release configuration in `.goreleaser.yml` also produces binaries for several OS/architecture combinations and DEB/RPM packages. Docker CI builds at least `linux/amd64` and `linux/arm64`.

### Database and state

Three dialects are supported in `config.schema.yaml`:

- SQLite;
- MySQL;
- PostgreSQL.

MariaDB uses the MySQL dialect, but upstream CI runs it separately. This is correct: driver compatibility does not guarantee identical behavior of the two servers.

A separate axis is not a clean installation but an upgrade of existing state:

- previous release → current release;
- migrations for each supported database;
- project backup and restore;
- BoltDB → SQLite for the remaining supported migration path.

### Task execution architecture

Two fundamentally different modes are supported:

- executing tasks locally by the server process;
- executing on a separately registered remote runner.

A runner can be persistent or one-off. For a dynamic one-off runner, the server can call a webhook, after which the created runner registers and picks up the task. There are concurrency limits and runner-to-project binding. Global runner tags and exact routing are confirmed on Community `v2.19.8`; Docker/Kubernetes executors remain Pro features.

The following executors were found in the runner configuration:

- `local` — a subprocess on the runner machine;
- `docker` — a separate container per task;
- `k8s` — an ephemeral Kubernetes pod.

The Docker/Kubernetes executors are implemented in `pro/`, so they must be treated as a separate Pro matrix and run only when a Pro build/license is available. Source on runner operation: [Runners](https://semaphoreui.com/docs/admin-guide/cli/runners).

### Configuration source and network

Semaphore reads a JSON/YAML config and environment variables; the path is set via `SEMAPHORE_CONFIG_PATH`/`--config`, and it can also start without a config file. Environment variables can override file fields.

Network variants that affect behavior:

- plain HTTP;
- built-in TLS and HTTP redirect;
- reverse proxy with TLS termination;
- publishing at the domain root or under a subpath via web root;
- custom CA between runner and server;
- a single server node or HA nodes with Redis and a shared SQL DB.

There is no need to check every business test for JSON, YAML, and env. A separate config-contract set is enough, one that proves reading, override, an error for an unknown/invalid value, and the absence of secrets in logs.

Source: [Configuration](https://semaphoreui.com/docs/admin-guide/configuration).

Active-active HA requires an Enterprise subscription: the community build keeps task state in-memory and does not provide a Redis-backed node registry, distributed claims, schedule deduplication, or Pub/Sub. Therefore two community containers with a shared PostgreSQL cannot be considered HA — they would create a false green check and a risk of double execution. A full `enterprise-ha` profile is postponed until a test subscription key is obtained. Source: [High Availability](https://semaphoreui.com/docs/admin-guide/ha).

### Authentication

Possible modes:

- local account and password;
- LDAP, including multiple providers and TLS;
- OIDC, including multiple providers, claim mapping, and account linking rules;
- TOTP/email MFA;
- disabling password login.

We do not mix authentication with the whole DB matrix. LDAP and OIDC need standalone profiles with negative scenarios for account mapping, callback, logout, TLS, and RBAC after login.

Sources: [LDAP](https://semaphoreui.com/docs/admin-guide/ldap), [OpenID](https://semaphoreui.com/docs/admin-guide/openid).

### Git, keys, and secrets

Axes that directly affect the main task flow:

- local/file repository, HTTPS, and SSH;
- `cmd_git` and `go_git` clients;
- branches, tags/refs, submodules, known_hosts, and custom SSH config;
- password, SSH key, and other access key types;
- local encryption of access keys: legacy key or keyring file with rotation;
- external secret storage implementations found in the code: Vault, environment, and file.

These variants are more efficiently checked with small feature sets on top of one stable DB profile rather than multiplied across all databases.

### Tool inside the task

The source code contains the following template applications:

- Ansible;
- Terraform;
- OpenTofu;
- Terragrunt;
- shell;
- PowerShell.

Ansible remains the base end-to-end fixture. For the other tools, one minimal successful scenario each plus characteristic installation/execution errors are needed; checking each of them on every database is not required.

## What upstream CI already checks

On the examined commit, upstream separately runs migrate/integration jobs for SQLite, MySQL, MariaDB, and PostgreSQL. This reduces the need to duplicate the whole internal Go integration suite, but does not replace black-box checking of the published image:

- upstream checks its own build, while we currently run the release image;
- DB jobs do not prove the end-to-end task lifecycle with real Git/Ansible;
- they do not cover customer packaging, reverse proxy, external auth, or the upgrade of a persisted stand as a product scenario.

Database versions must not be taken as "latest" implicitly. Each profile must pin an exact image tag, and a periodic compatibility job must separately check the declared minimum and current versions once the support policy is agreed.

## Proposed baseline profiles

| ID | Configuration | Why | Run |
|---|---|---|---|
| `core-sqlite-local` | release Docker image, SQLite, local execution, env config, password auth, `cmd_git` | API baseline; browser password login, task launch, and client validation | every PR |
| `core-postgres-local` | Docker Compose, PostgreSQL 14.3, local execution | Black-box PostgreSQL and migration compatibility without runner-specific variables | nightly |
| `prod-postgres-runner` | Docker Compose, PostgreSQL, separate persistent runner with local executor, config file | The most useful check of the production-like server ↔ DB ↔ runner boundary | nightly; after stabilization — PR gate |
| `core-mysql-local` | Docker Compose, MySQL 8.4, local execution | Black-box MySQL and migration compatibility | nightly |
| `core-mariadb-local` | Docker Compose, MariaDB 10.11, local execution | Real compatibility of the MySQL dialect with MariaDB | nightly |
| `feature-ssh-local` | SQLite, two local SSH servers with different encrypted test keys | Git clone and Ansible target over SSH, key rotation, negative auth, and secret protection | nightly |
| `feature-git-https` | SQLite, pinned NGINX, self-signed CA, and Basic Auth | Successful private HTTPS clone/execution, rejection without credentials, and login/password protection | nightly |
| `feature-oidc-local` | SQLite and pinned local Dex | Discovery, browser login, callback, session/logout, return path, provisioning, repeat login, local-email conflict, and provider failure | nightly |
| `feature-ldap-tls` | SQLite and pinned OpenLDAP with TLS | LDAPS service/user bind, search/mapping, provisioning/reuse, logout, invalid password, and local-email conflict | nightly |
| `feature-totp-local` | SQLite, password auth, and TOTP recovery | API lifecycle; browser Security/QR, challenge, invalid/valid passcode, and recovery form | nightly |
| `feature-schedule-timezone` | SQLite, `Pacific/Kiritimati`, local execution | Real cron/run-at execution and the schedule → task link | manual; defect reproducer |
| `feature-shell-output` | SQLite, local execution | Completeness of short `stdout`/`stderr` and closing of inherited pipes | manual; defect reproducer |
| `feature-proxy-oidc` | PostgreSQL, NGINX TLS, non-root web path, Dex | Callback URL, Secure cookie, redirects, account mapping, and negative paths | nightly |
| `feature-encryption-rotation` | PostgreSQL, file keyring with two test-only AES keys | Hot reload of primary, mixed-key reads, `vault check`, backup/rekey, removal of the retired key, and post-rekey execution | nightly |
| `enterprise-ha-two-node` | two Enterprise server nodes, PostgreSQL, Redis, remote runner | Queue, session/state consistency, and failure of one node | after obtaining a test subscription |
| `feature-dynamic-runner` | SQLite and a one-off runner started via webhook | Start/finish webhook, exactly one task, and termination of the runner process | manual; defect reproducer |
| `pro-docker-executor` | Pro runner with Docker executor | Task container isolation, limits, cleanup, secret hydration | when Pro is available, nightly |
| `pro-k8s-executor` | Helm/Pro runner with Kubernetes executor | pod lifecycle, service account, pull secret, and cleanup | when Pro/K8s is available, release |

The five base profiles and ten feature profiles are implemented. `feature-git-https` checks the separate client-side boundary of private Git with real trusted TLS and Basic Auth. `feature-oidc-local`, `feature-proxy-oidc`, `feature-ldap-tls`, and `feature-totp-local` provide green positive and negative auth paths without multiplying across the whole DB matrix; the proxy variant additionally pins the HTTPS/subpath/cookie contract, and TOTP the passcode/recovery lifecycle. `feature-encryption-rotation` checks zero-downtime primary switch-over, rekey, and safe removal of the retired key. `feature-schedule-timezone` reproduces missing cron/run-at tasks, `feature-dynamic-runner` a one-off runner that does not terminate after a successful task, and `feature-shell-output` the loss of one of the short process streams after `success`. All three profiles remain manual red reproducers. HA has been investigated and correctly postponed as Enterprise-only instead of an unsafe community imitation.

The CI distribution is also implemented: the API baseline and a short Chromium UI smoke on `core-sqlite-local` are part of the pull-request gate after the framework quality checks; the other four base profiles, `feature-ssh-local`, `feature-git-https`, `feature-oidc-local`, `feature-proxy-oidc`, `feature-ldap-tls`, `feature-totp-local`, and `feature-encryption-rotation` run in a daily matrix job. Four release-upgrade profiles run as a separate weekly and manual check; SQLite, PostgreSQL, MySQL, and MariaDB all passed the complete Linux lifecycle on 2026-09-11. The upgrade workflow is deliberately excluded from the PR gate.

## Which tests to run where

| Set | SQLite | PostgreSQL local | PostgreSQL + runner | MySQL | MariaDB | Feature profile |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| health, login, project CRUD | ✓ | ✓ | ✓ | ✓ | ✓ | short smoke |
| invalid login, account enumeration, brute-force canary | ✓ | ✓ | ✓ | ✓ | ✓ | core profiles |
| Git → template → task → output → cleanup | ✓ | ✓ | ✓ | ✓ | ✓ | if applicable |
| INI/YAML static inventory: multi-group + template limit | ✓ | ✓ | ✓ | ✓ | ✓ | core profiles |
| file inventory from a Git repository | ✓ | ✓ | ✓ | ✓ | ✓ | create traversal defect recorded separately |
| Terraform/OpenTofu plan + workspace inventory + masked `TF_VAR_*` | ✓ | — | — | — | — | `core-sqlite-local`, toolchain/secret-injection coverage |
| Build → Deploy artifact version chain | ✓ | — | — | — | — | `core-sqlite-local`, template/task contract |
| RBAC and project isolation | ✓ | ✓ | ✓ | ✓ | ✓ | auth profiles extend the set |
| task stop/force-stop | local | local | remote | local | local | runner profiles |
| project deletion after stop / while running | stopped ✓; running defect | planned | planned | planned | planned | `project-deletion-running-task-defect.md` |
| runner registration/default/heartbeat | — | — | ✓ | — | — | runner profiles |
| project max parallel / queue admission | ✓ | planned | ✓ | planned | planned | core profiles |
| runner exact tag / capacity / used runner | — | — | ✓ | — | — | `prod-postgres-runner` |
| unavailable matching runner recovery | — | — | defect: task error | — | — | `runner-unavailable-routing-defect.md` |
| secret survey variable dispatch | local ✓ | local ✓ | defect: value lost | local ✓ | local ✓ | `remote-runner-survey-secrets-defect.md` |
| dynamic start/finish webhook and one-off exit | — | — | — | — | — | `feature-dynamic-runner`, defect |
| Git over SSH, SSH inventory, and key rotation | — | — | — | — | — | `feature-ssh-local` |
| Private Git over HTTPS and Basic Auth | — | — | — | — | — | `feature-git-https` |
| real cron/run-at execution | — | — | — | — | — | `feature-schedule-timezone`, defect |
| completeness of short stdout/stderr | defect | planned | planned | planned | planned | `feature-shell-output` |
| constraints, schedules, cleanup, clean migration | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| secrets and absence of leaks | ✓ | ✓ | ✓ | ✓ | ✓ | encryption/storage extend the set |
| database encryption key rotation | — | — | — | — | — | `feature-encryption-rotation` |
| OIDC/LDAP/MFA | — | — | — | — | — | OIDC: `feature-oidc-local`, `feature-proxy-oidc`; LDAP: `feature-ldap-tls`; MFA: `feature-totp-local` |
| HA/failover | — | — | — | — | — | HA profile only |

The `—` sign means a deliberate exclusion, not unknown coverage. It is important to record this, otherwise the matrix will over time turn back into an implicit full enumeration.

## Separate release set

Before a release, it is more important not to repeat all API tests but to check the customer's installation and upgrade path:

1. clean install of the Docker image, DEB/RPM, and Helm;
2. creation of a small but interconnected data set;
3. stop and upgrade from N-1 to current;
4. automatic DB migrations;
5. login, reading old data, and launching an old template after the upgrade;
6. checking schedules, access keys, and encryption keys after restart;
7. backup/restore;
8. a short artifact smoke on `amd64` and `arm64`.

It is enough to keep the full business suite on Docker. Package/binary/Helm check packaging, persistence, permissions, readiness, and upgrade.

## How to organize this in the test project

Java tests should not be copied, nor should separate classes be created per database. The infrastructure selects a profile, and the same test set works with the published capabilities of the stand.

Each profile needs a manifest with the following fields:

```yaml
id: prod-postgres-runner
semaphore:
  image: semaphoreui/semaphore:v2.19.7
  source_commit: e9dc41a1de8a747569334f7a2b76c320b945d4f0
  edition: community
installation: docker-compose
architecture: arm64
database:
  type: postgres
  image: postgres:<pinned-version>
execution:
  mode: remote-runner
  executor: local
auth: password
git_client: cmd_git
capabilities:
  - core-api
  - task-execution
  - remote-runner
  - schedules
```

The manifest must end up in Allure environment/labels together with the image digest. Then any failure can be linked to the exact configuration, and tests with an unsupported capability can be skipped with a clear reason.

Proposed launch interface:

```bash
./test-environment/profile up core-sqlite-local
./gradlew test -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
./test-environment/profile down core-sqlite-local
```

The `profile` command is implemented for `core-sqlite-local`, `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner`, `feature-ssh-local`, `feature-git-https`, `feature-oidc-local`, `feature-proxy-oidc`, `feature-ldap-tls`, `feature-totp-local`, `feature-encryption-rotation`, `feature-schedule-timezone`, `feature-dynamic-runner`, `feature-shell-output`, and the upgrade profiles: it manages the Compose lifecycle, generates local SSH/TLS/HTTPS-Git/encryption fixtures when needed, waits for readiness/setup services, runs the selected test lifecycle, and records manifest/runtime metadata and image digests in Allure. Subsequent profiles are plugged in through the same interface.

## Discovered reproducibility risk

The current test Compose is pinned to the fully verified release image `v2.19.12` (tag commit
`012ed06d3eccadaed594c73b93b3d8a2459b576f`). The previous baseline is `v2.19.8`
(`3449a04f3bfa2522ec7fd60803f71b578c39f6b4`).

This means the API schema and configuration details cannot automatically be assumed to match the running image. Before extending the matrix, one of two rules must be chosen:

- test the release image and take the schema/source from the corresponding tag;
- test the build of the current source commit and store the commit as the stand version.

For a regression system it is better to support both profile types: the release image for the customer scenario and a source build for early verification of the upcoming release.

## Recommended sequence

1. Introduce the manifest and a unified profile lifecycle.
2. Move the existing stand to `core-sqlite-local` without changing the tests. Done.
3. Add PostgreSQL and remote runner. Done: `core-postgres-local` and `prod-postgres-runner` pass the existing core suite; the runner API additionally confirms the default/online/heartbeat contract.
4. Add a short MySQL/MariaDB DB matrix. Done: the `core-mysql-local` profile on MySQL 8.4 and `core-mariadb-local` on MariaDB 10.11 pass the same core suite after a clean schema migration; the actual image digests end up in Allure.
5. Implement the N-1 → current upgrade for all Community database variants. SQLite and PostgreSQL
are done and confirmed in Linux CI:
   `upgrade-sqlite-local` and `upgrade-postgres-local` create data on `v2.19.8`, switch
   the server image to `v2.19.12` while preserving the DB, and run the verify/core suite. The current pair passed
   on SQLite and PostgreSQL in Linux CI on 2026-09-04; the previous `v2.19.7 → v2.19.8` pair was also green.
   The same lifecycle is implemented for `upgrade-mysql-local` and `upgrade-mariadb-local` and
   added to the weekly workflow. Both passed locally on 2026-09-09 and remain pending until the
   first successful Linux run.
6. Add the SSH feature profile. Done: Git clone, Ansible SSH target, wrong key, secret replacement for an existing key ID, and key material protection are checked on two isolated SSH fixtures. `known_hosts` is postponed until a release with the corresponding upstream configuration.
7. Add real schedule execution. The reproducer is implemented for cron and `run_at`; the missing task on `v2.19.8` is confirmed locally and in Linux CI. The next step is upstream issue/fix verification.
8. Add the OIDC feature profile. Done: pinned Dex, discovery, browser login, callback, session/logout, return path, provisioning, repeat login, local-email conflict, and provider failure pass locally on `v2.19.8`.
9. Add LDAP with TLS. Done: pinned OpenLDAP, LDAPS service/user bind, search/mapping, provisioning/reuse, logout, invalid password, and local-email conflict pass locally on `v2.19.8`.
10. Add the dynamic one-off runner. Done as a manual reproducer: the webhook starts the runner, the task completes successfully, and the `finish` webhook arrives, but the process does not exit on `v2.19.8`. The likely unreachable exit condition is recorded in `dynamic-runner-one-off-exit-defect.md`; the profile is not added to the green CI matrix.
11. Add an HTTPS reverse proxy, non-root web path, and OIDC. Done: `feature-proxy-oidc` on PostgreSQL/Nginx/Dex checks `/semaphore` routing, trusted TLS API, browser callback/return path, and the `Secure`/`HttpOnly` session cookie.
12. Investigate HA. Done: active-active requires an Enterprise subscription and a Redis-backed overlay that is absent from the community build. Implementation is postponed until a test key is obtained; two community nodes are not used as a false HA stand.
13. Add database encryption keyring rotation. Done: `feature-encryption-rotation` on PostgreSQL creates secrets with the old primary, hot reload switches writes to the new key ID, `vault check` confirms the mixed state, `vault rekey --backup` migrates the ciphertext, after which the retired key is removed and the stored template is executed again.
14. Add MFA. Done: `feature-totp-local` checks self-enrollment, the challenge after password login, invalid/valid RFC 6238 passcodes, recovery, repeated enrollment, and rejection of an already used recovery code. The TOTP secret and codes are excluded from HTTP and Allure artifacts.
15. Extend the TOTP browser flow. Done: a separate UI account enables TOTP in Security settings, checks QR/recovery-code rendering, passes the challenge with invalid/valid passcodes, and recovers via the recovery form. Sensitive UI artifacts are not published on failure.
16. Investigate web-cache safety. Planned as P2: derive a focused profile from the existing NGINX
    proxy setup, enable an observable shared cache, and use two users to prove that authentication,
    authenticated API, redirect, and error responses cannot cross the user boundary. Probe common
    unkeyed headers and query parameters, while keeping immutable static assets cacheable. Promote
    the work to a P1 defect only if a cross-user response or attacker-controlled cached response is
    reproduced; otherwise document the required application headers and safe proxy bypass rules.

This way we first protect the typical customer installation and the most expensive failure points, while keeping the environment understandable for a single engineer.
