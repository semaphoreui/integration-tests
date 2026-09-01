# Semaphore UI Component Map for Defect Analysis

**Snapshot date:** 2026-08-06
**Source:** `semaphoreui/semaphore` repository structure, API routes, and core services.
**Purpose:** unified classification of issues and future tests.

## Classification Rule

For each ticket, specify:

* **primary component** — the area where the problem manifested itself or was fixed;
* **additional tags** — affected cross-cutting areas such as `RBAC`, `Secrets`, `Migration`, `UI`, `Performance`;
* if the root cause has not been established, the component is marked as **"requires investigation"** rather than being inferred from the issue title.

A single ticket may affect multiple components. For example, a user from project A being able to access a task log from project B is classified as `Tasks & execution` with the tags `Auth/RBAC`, `Project isolation`, and `Task output`.

## Product Components

| Code         | Component                           | Scope                                                                                                  | Main Code Areas                                                                                                                                                                                                     | Typical Risks and Checks                                                                                                   |
| ------------ | ----------------------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| AUTH         | Authentication and Sessions         | Login/logout, API tokens, TOTP, recovery, LDAP, OIDC, external identities, JWT/JWKS                    | `api/login*.go`, `api/auth.go`, `api/jwks.go`, `services/session_svc.go`, `pkg/jwt/`, `db/Session.go`, `db/UserExternalIdentity.go`                                                                                 | Authentication bypass, session lifetime, logout, token leakage, LDAP/OIDC incompatibility                                  |
| USERS        | Users and Global Administration     | Users, administrators, user settings, system information                                               | `api/users.go`, `api/user*.go`, `api/admin_info.go`, `db/User.go`, `cli/cmd/user*.go`                                                                                                                               | Privilege escalation, user lifecycle, incompatible settings                                                                |
| PROJECTS     | Projects and Isolation              | Project creation/modification, members, invitations, project roles, statistics                         | `api/projects/project*.go`, `api/projects/users.go`, `services/server/project_svc.go`, `db/Project*.go`, `db/Role.go`                                                                                               | Horizontal access, incorrect role, deletion of related data, project isolation                                             |
| TEMPLATES    | Task Templates                      | Task templates, launch parameters, inventory/repository/environment/key bindings, template permissions | `api/projects/templates.go`, `db/Template*.go`, `api/router.go`                                                                                                                                                     | Invalid relationships, parameter overrides, launch/edit permissions, backward compatibility                                |
| WORKFLOWS    | Workflows                           | Workflow definitions, nodes, approvals, runs, and artifacts                                            | `api/projects/workflows.go`, `db/Workflow*.go`, `pro_interfaces/workflow_*`, `pro/services/`                                                                                                                        | Step ordering, stopping, approval bypass, partial failure, artifact access                                                 |
| TASKS        | Tasks and Execution                 | Queue, task lifecycle, local execution, stop/confirm/reject, statuses, and retry                       | `api/projects/tasks.go`, `api/tasks/`, `services/tasks/`, `db/Task*.go`                                                                                                                                             | Lost/stuck tasks, race conditions, incorrect status, stopping, parallelism, cleanup                                        |
| OUTPUT       | Task Output and Events              | Output storage, stages, raw output, WebSocket streaming, event log, alerts                             | `services/tasks/TaskRunner_logging.go`, `pkg/task_logger/`, `api/sockets/`, `db/Event.go`, `services/tasks/alert.go`                                                                                                | Lost lines, secret leakage, stalled streams, large output, incorrect ordering, alerts                                      |
| RUNNERS      | Remote Runners                      | Registration, tokens, tags, polling, job assignment and execution, reconciliation                      | `api/runners/`, `services/runners/`, `services/tasks/RemoteJob.go`, `db/Runner.go`, `cli/cmd/runner*.go`                                                                                                            | Incorrect assignment, connection loss, duplicate execution, token authentication, runner tags, large payloads              |
| REPOSITORIES | Git Repositories                    | Clone/pull, SSH/HTTPS authentication, branches, playbooks, cache                                       | `api/projects/repository.go`, `db_lib/*Git*`, `pkg/git/`, `db/Repository.go`, `api/cache.go`                                                                                                                        | Private repository authentication, branch/ref handling, timeout, cache invalidation, command injection, unavailable remote |
| INVENTORY    | Inventory and Target Hosts          | Static/file/Terraform inventory, aliases, and Terraform state                                          | `api/projects/inventory.go`, `db/Inventory.go`, `db/TerraformInventory*`, `services/server/inventory_svc.go`, `pro_interfaces/terraform_inventory_ctl.go`                                                           | Large inventory, invalid format, content leakage, state locking, deletion of a resource in use                             |
| SECRETS      | Keys, Secrets, and Variable Groups  | Access keys, SSH/login/vault keys, environments, secret storage, synchronization, task secrets         | `api/projects/keys.go`, `api/projects/environment.go`, `api/projects/secret_storages.go`, `services/server/*secret*`, `services/server/access_key_*`, `db/AccessKey.go`, `db/Environment.go`, `db/SecretStorage.go` | Leakage through API/UI/logs/backups, encryption at rest, incorrect key, masking, synchronization, cross-project access     |
| SCHEDULES    | Schedules and Time                  | Cron/run-at, timezone, activation, scheduler pool                                                      | `api/projects/schedules.go`, `services/schedules/`, `db/Schedule.go`, `pkg/tz/`                                                                                                                                     | DST/timezone, duplicate/missed execution, disable race, recovery after restart                                             |
| INTEGRATIONS | Integrations and Webhooks           | Webhooks, aliases, matchers, extracted values, external triggers                                       | `api/integration.go`, `api/projects/integration*.go`, `hook_helpers/`, `db/Integration*.go`                                                                                                                         | Unauthorized execution, incorrect matcher, replay, payload parsing, secret verification                                    |
| PROJECT_DATA | Backup, Restore, Import, and Export | Project export/import, backup/restore of related entities                                              | `api/projects/backup_restore.go`, `services/project/`, `services/export/`, `cli/cmd/project_*`                                                                                                                      | Data loss/duplication, secret leakage, version incompatibility, broken references                                          |
| UI           | Web Interface                       | Vue application, forms, tables, routing, task and log display                                          | `web/src/`, `web/tests/`                                                                                                                                                                                            | Incorrect form state, hidden errors, permissions enforced only in UI, browser compatibility, accessibility                 |
| CLI          | CLI and Setup                       | Server/setup, user/project/vault commands, migrations, runner management                               | `cli/`, `cli/cmd/`, `cli/setup/`                                                                                                                                                                                    | Differences from API behavior, destructive defaults, validation, exit codes, secret exposure in terminal                   |

## Platform Components

| Code          | Component                         | Scope                                                                         | Main Code Areas                                                                                 | Typical Risks and Checks                                                                                  |
| ------------- | --------------------------------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| API           | HTTP API and Contract             | Router, middleware, request validation, response/error contracts, OpenAPI     | `api/router.go`, `api/helpers/`, `api-docs.yml`, `.dredd/`                                      | Documentation diverges from code, incorrect status codes, missing validation, incompatible changes        |
| RBAC          | Authorization and Permissions     | Global and project roles, permissions for resources and templates             | `api/router.go`, auth middleware, `db/Role.go`, `db/ProjectUser.go`, `db/TemplateRole*`         | IDOR, horizontal access, privilege escalation, UI hides an allowed backend endpoint                       |
| DB            | Storage and Data Integrity        | Store interfaces, SQL implementations, transactions, constraints, and indexes | `db/`, `db/sql/`, `db/factory/`                                                                 | Differences between SQLite/MySQL/Postgres/Bolt, N+1 queries, races, orphaned data, incorrect transactions |
| MIGRATIONS    | Migrations and Upgrades           | Schema migrations, version transitions, rekeying/compatibility                | `db/migration/`, `db/sql/migration*.go`, `deployment/`, `cli/cmd/migrate.go`, `cli/cmd/vault_*` | Upgrade from an old version, data loss, rollback/restart, large databases, secret migration               |
| EXECUTORS     | Integration with Execution Tools  | Ansible, Terraform/OpenTofu/Terragrunt, Bash, PowerShell, local commands      | `db_lib/*App.go`, `services/tasks/*executor*`, `db/ansible.go`                                  | Command arguments, quoting/injection, exit codes, timeouts, incompatible tool versions                    |
| CONFIG        | Application Configuration         | Environment/config file, schema, feature flags, mail/alerts, paths            | `config.schema.yaml`, `util/config.go`, `api/options.go`, `db/Option.go`                        | Incorrect defaults, incompatible environment variables, validation, secret values in config/logs          |
| DEPLOYMENT    | Installation and Packaging        | Docker, Compose, systemd, deb/rpm, devcontainer, release artifacts            | `deployment/`, `.devcontainer/`, Dockerfile, release workflows                                  | File permissions, volume/data loss, platform/architecture, upgrade path, health checks                    |
| HA            | Cluster and High Availability     | Claims, coordination, cluster status, Pro HA boundaries                       | `api/cluster.go`, `pro_interfaces/ha.go`, `pro/`                                                | Duplicate execution, split brain, stale claims, failover, consistency                                     |
| OBSERVABILITY | Logging, Metrics, and Diagnostics | Application logs, task logs, metrics, debug logging, system information       | `pkg/debuglog/`, `pkg/metrics/`, `api/system_info.go`, `api/admin_info.go`                      | Insufficient diagnostics, PII/secrets in logs, incorrect metrics, excessive logging                       |
| CI            | Project Build and CI              | Unit/integration/e2e jobs, linting, release workflows                         | `.github/workflows/`, `Taskfile.yml`, `.golangci.yml`, `qodana.yaml`, `.codacy.yml`             | Tests not running, flaky pipeline, local/CI differences, missing artifacts                                |
| TEST_INFRA    | Test Infrastructure               | E2E environment, fixtures, test cases, Playwright                             | `test/`, `test/e2e/`, `web/tests/`                                                              | Non-reproducibility, shared state, brittle selectors, real secrets, insufficient diagnostics              |

## Cross-Cutting Tags

These values do not replace the primary component:

| Tag             | When to Use                                                                        |
| --------------- | ---------------------------------------------------------------------------------- |
| `Security`      | Trust boundary violation, bypassed validation, injection, insecure default         |
| `Secrets`       | Sensitive data may be leaked, corrupted, or used incorrectly                       |
| `RBAC`          | The issue depends on the user's role or project membership                         |
| `Regression`    | A previously working scenario is broken by a change                                |
| `Data loss`     | Data is lost, corrupted, or irreversibly modified                                  |
| `Performance`   | Time, CPU, memory, payload size, DB load, or scaling is involved                   |
| `Concurrency`   | Race condition, duplicate execution, deadlock, queue, or parallel tasks            |
| `Compatibility` | OS, browser, database, Ansible/Terraform version, or old data format compatibility |
| `Upgrade`       | Installing a new version over an existing installation                             |
| `Documentation` | Behavior differs from the documentation or the documentation is insufficient       |
| `UX`            | Usability, feedback, or prevention of user error is affected                       |
| `Flaky`         | The result is unstable with identical inputs                                       |

## High-Level Execution Flow

```text
User / API client
  -> UI or HTTP API
  -> authentication + RBAC
  -> project resources
       repository + inventory + environment/secrets + access key
  -> template / workflow / schedule / integration
  -> task queue
  -> local executor or remote runner
  -> Ansible / Terraform / Shell / PowerShell
  -> task status + DB output + WebSocket + alerts
```

## Notes for Issue Analysis

* The user's complaint and the root cause are different fields. For example, "UI keeps loading indefinitely" may actually be a DB query or WebSocket defect.
* For a closed ticket, the fix should be confirmed by a PR, commit, or diff. If no such link exists, write **"not established"**.
* If a ticket was closed without a fix, record this explicitly: duplicate, cannot reproduce, configuration/support question, stale, or won't fix.
* The ticket date in the registry should be understood as the creation date; for closed tickets, the closing date is also useful.