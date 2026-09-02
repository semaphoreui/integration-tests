# Semaphore UI configuration testing overview

## Why a separate strategy is needed

Semaphore UI is mostly installed in the customer's infrastructure. A failure may depend not only on the API or UI, but also on the combination of installation method, database, runner, network, authorization, Git, and secret storage method.

Testing the full Cartesian product of these settings is impossible and unnecessary. We use three levels:

1. **Broad configuration checks** — process startup, readiness, configuration validation, and migrations across a large number of variants.
2. **Reference profiles** — the same set of critical API scenarios on several realistic configurations.
3. **Feature profiles** — dedicated checks only for LDAP, OIDC, HA, remote runner, encryption rotation, and other special capabilities.

## Confirmed configuration variants

### Installation method

Officially documented:

- Docker and Docker Compose;
- DEB/RPM via a package manager;
- standalone binary and launch via systemd;
- Kubernetes via the official Helm chart;
- installation in cloud infrastructure as one of the hosting options.

Snap is marked deprecated in the documentation and must not be part of the core matrix.

Sources: [Installation overview](https://semaphoreui.com/docs/admin-guide/installation), [Package manager](https://semaphoreui.com/docs/admin-guide/installation/package-manager), [Binary file](https://semaphoreui.com/docs/admin-guide/installation/binary-file), [deprecated Snap](https://semaphoreui.com/docs/administration-guide/installation/snap).

The release configuration in `.goreleaser.yml` also produces binaries for several OS/architecture combinations and DEB/RPM packages. Docker CI builds at least `linux/amd64` and `linux/arm64`.

### Database and state

Three dialects are supported in `config.schema.yaml`:

- SQLite;
- MySQL;
- PostgreSQL.

MariaDB uses the MySQL dialect, but upstream CI runs it separately. This is correct: driver compatibility does not guarantee identical behavior of the two servers.

A separate axis is not a clean installation but upgrading existing state:

- previous release → current release;
- migrations of every supported database;
- project backup and restore;
- BoltDB → SQLite for the remaining supported migration path.

### Task execution architecture

Two fundamentally different modes are supported:

- tasks executed locally by the server process;
- execution on a separately registered remote runner.

A runner can be persistent or one-off. For a dynamic one-off runner, the server can call a webhook, after which the created runner registers and picks up the task. There are concurrency limits and runner-to-project binding; tags belong to the Pro capabilities.

The following executors were found in the runner configuration:

- `local` — a subprocess on the runner machine;
- `docker` — a dedicated container per task;
- `k8s` — an ephemeral Kubernetes pod.

The Docker/Kubernetes executors are implemented in `pro/`, so they should be treated as a separate Pro matrix and run only when a Pro build/license is available. Source on runner operation: [Runners](https://semaphoreui.com/docs/admin-guide/cli/runners).

### Configuration source and network

Semaphore reads a JSON/YAML config and environment variables; the path is set via `SEMAPHORE_CONFIG_PATH`/`--config`, and running without a config file is also possible. Environment variables can override file fields.

Network variants that affect behavior:

- direct HTTP;
- built-in TLS and HTTP redirect;
- reverse proxy with TLS termination;
- publishing at the domain root or under a subpath via web root;
- a custom CA between the runner and the server;
- a single server node or HA nodes with Redis and a shared SQL DB.

There is no need to run every business test for JSON, YAML, and env. A dedicated config-contract suite is enough, one that proves reading, override, an error for an unknown/invalid value, and the absence of secrets in the logs.

Source: [Configuration](https://semaphoreui.com/docs/admin-guide/configuration).

### Authorization

Possible modes:

- local account and password;
- LDAP, including multiple providers and TLS;
- OIDC, including multiple providers, claim mapping, and account linking rules;
- TOTP/email MFA;
- disabling password login.

We do not mix authorization with the whole DB matrix. LDAP and OIDC need standalone profiles with negative scenarios for account mapping, callback, logout, TLS, and RBAC after login.

Sources: [LDAP](https://semaphoreui.com/docs/admin-guide/ldap), [OpenID](https://semaphoreui.com/docs/admin-guide/openid).

### Git, keys, and secrets

Axes that directly affect the core task flow:

- local/file repository, HTTPS, and SSH;
- `cmd_git` and `go_git` clients;
- branches, tags/refs, submodules, known_hosts, and custom SSH config;
- password, SSH key, and other access key types;
- local encryption of access keys: a legacy key or a keyring file with rotation;
- external secret storage implementations found in the code: Vault, environment, and file.

These variants are better verified with small feature suites on top of one stable DB profile rather than multiplied across all databases.

### Tool inside a task

The source code contains template applications:

- Ansible;
- Terraform;
- OpenTofu;
- Terragrunt;
- shell;
- PowerShell.

Ansible remains the base end-to-end fixture. The other tools each need one minimal successful scenario and their characteristic installation/execution errors; verifying each of them on every database is not required.

## What upstream CI already covers

On the examined commit, upstream separately runs migrate/integration jobs for SQLite, MySQL, MariaDB, and PostgreSQL. This reduces the need to duplicate the entire internal Go integration suite, but does not replace black-box verification of the published image:

- upstream tests its own build, while we currently run the release image;
- the DB jobs do not prove the end-to-end task lifecycle with real Git/Ansible;
- they do not cover customer packaging, reverse proxy, external auth, or upgrading a preserved stand as a product scenario.

Database versions must not be taken as "latest" implicitly. Each profile must pin an exact image tag, and a periodic compatibility job must separately verify the declared minimum and current versions once the support policy is agreed.

## Proposed reference profiles

| ID | Configuration | Why | Run |
|---|---|---|---|
| `core-sqlite-local` | release Docker image, SQLite, local execution, env config, password auth, `cmd_git` | The cheapest smoke and convenient local development | every PR |
| `core-postgres-local` | Docker Compose, PostgreSQL 14.3, local execution | Black-box PostgreSQL and migration compatibility without runner-specific variables | nightly |
| `prod-postgres-runner` | Docker Compose, PostgreSQL, dedicated persistent runner with local executor, config file | The most useful check of the production-like server ↔ DB ↔ runner boundary | nightly; after stabilization — PR gate |
| `core-mysql-local` | Docker Compose, MySQL 8.4, local execution | Black-box MySQL and migration compatibility | nightly |
| `core-mariadb-local` | Docker Compose, MariaDB 10.11, local execution | Real compatibility of the MySQL dialect with MariaDB | nightly |
| `proxy-oidc` | PostgreSQL, reverse proxy TLS, non-root web path, OIDC provider | Callback URL, cookies, redirects, account mapping, and RBAC | nightly/scheduled |
| `ldap-tls` | PostgreSQL and LDAP with TLS | Bind/search/mapping, TLS failure, and RBAC | scheduled |
| `ha-two-node` | two server nodes, PostgreSQL, Redis, remote runner | Queue, session/state consistency, and failure of one node | scheduled |
| `dynamic-runner` | one-off runner launched via webhook | Registration, receiving exactly one task, timeout, and cleanup | scheduled |
| `pro-docker-executor` | Pro runner with the Docker executor | Task container isolation, limits, cleanup, secret hydration | when Pro is available, nightly |
| `pro-k8s-executor` | Helm/Pro runner with the Kubernetes executor | pod lifecycle, service account, pull secret, and cleanup | when Pro/K8s is available, release |

The base five profiles are implemented. OIDC/LDAP/HA/dynamic runner should be added sequentially, without multiplying the whole DB matrix onto them.

The CI distribution is also implemented: `core-sqlite-local` is part of the pull-request gate after the framework quality checks; the other four base profiles run in a daily matrix job; the two release-upgrade profiles run as a separate weekly and manual check. The upgrade workflow deliberately stays out of the PR gate and does not mask the known incompatibility as an expected failure.

## Which tests run where

| Suite | SQLite | PostgreSQL local | PostgreSQL + runner | MySQL | MariaDB | Feature profile |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| health, login, project CRUD | ✓ | ✓ | ✓ | ✓ | ✓ | short smoke |
| Git → template → task → output → cleanup | ✓ | ✓ | ✓ | ✓ | ✓ | where applicable |
| RBAC and project isolation | ✓ | ✓ | ✓ | ✓ | ✓ | auth profiles extend the suite |
| task stop/force-stop | local | local | remote | local | local | runner profiles |
| runner registration/default/heartbeat | — | — | ✓ | — | — | runner profiles |
| constraints, schedules, cleanup, clean migration | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| secrets and absence of leaks | ✓ | ✓ | ✓ | ✓ | ✓ | encryption/storage extend the suite |
| OIDC/LDAP/MFA | — | — | — | — | — | dedicated profile only |
| HA/failover | — | — | — | — | — | HA profile only |

The `—` sign means a deliberate exclusion, not unknown coverage. This is important to record, otherwise the matrix will eventually turn back into an implicit full enumeration.

## Separate release suite

Before a release, what matters is not repeating all the API tests, but verifying the customer's installation and upgrade path:

1. clean install of the Docker image, DEB/RPM, and Helm;
2. creation of a small but linked data set;
3. shutdown and upgrade from N-1 to current;
4. automatic DB migrations;
5. login, reading old data, and running an old template after the upgrade;
6. verification of schedules, access keys, and encryption keys after a restart;
7. backup/restore;
8. a short artifact smoke on `amd64` and `arm64`.

The full business suite can stay on Docker. Package/binary/Helm verify packaging, persistence, permissions, readiness, and upgrade.

## How to organize this in the test project

Java tests should not be copied, and separate classes should not be created per database. The infrastructure selects a profile, while the same test suite works with the published capabilities of the stand.

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

The manifest must go into the Allure environment/labels along with the image digest. Then any failure can be tied to an exact configuration, and tests with an unsupported capability can be skipped with a clear reason.

Proposed launch interface:

```bash
./test-environment/profile up core-sqlite-local
./gradlew test -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
./test-environment/profile down core-sqlite-local
```

The `profile` command is implemented for `core-sqlite-local`, `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, and `prod-postgres-runner`: it manages the Compose lifecycle, waits for readiness/setup services, runs the API tests, and records manifest/runtime metadata and image digests into Allure. Subsequent profiles plug into the same interface.

## Discovered reproducibility risk

The current test Compose is pinned to the release image `v2.19.7`, while the local copy of the sources is at commit `80b78a3ef4a074cab6ec33792dd96f9cd85619af`. The `v2.19.7` tag points to a different commit — `e9dc41a1de8a747569334f7a2b76c320b945d4f0`.

This means the API schema and configuration details cannot automatically be assumed to match the running image. Before expanding the matrix, one of two rules must be chosen:

- test the release image and take the schema/source from the corresponding tag;
- test a build of the current source commit and record the commit as the stand version.

For a regression system it is better to support both profile types: the release image for the customer scenario and a source build for early verification of the upcoming release.

## Recommended sequence

1. Introduce the manifest and a unified profile lifecycle.
2. Move the existing stand to `core-sqlite-local` without changing the tests. Done.
3. Add PostgreSQL and the remote runner. Done: `core-postgres-local` and `prod-postgres-runner` pass the existing core suite; the runner API additionally confirms the default/online/heartbeat contract.
4. Add a short MySQL/MariaDB DB matrix. Done: the `core-mysql-local` profile on MySQL 8.4 and `core-mariadb-local` on MariaDB 10.11 pass the same core suite after a clean-schema migration; the actual image digests go into Allure.
5. Implement the N-1 → current upgrade for SQLite and PostgreSQL. Done: `upgrade-sqlite-local` and `upgrade-postgres-local` automatically create data on `v2.19.6`, switch the server image to `v2.19.7` while preserving the database, and run the verify/core suite. Both profiles discovered a blocking incompatibility of the `v2.20.1` migration: the `access_key.task_id` and `access_key.expire_at` columns remain in the schema but are missing from the `v2.19.7` model, causing the access key API to return 400. Until the product is fixed, the upgrade jobs must stay red.
6. Then choose between OIDC, LDAP, and HA based on the frequency of relevant issues and the available infrastructure.

This way we first protect the typical customer installation and the most expensive failure points, while keeping the environment understandable for a single engineer.
