# Semaphore UI Component Map for Defect Analysis

**Snapshot date:** 2026-08-06  
**Source:** the structure of the `semaphoreui/semaphore` repository, API routes, and core services.  
**Purpose:** a unified classification for issues and future tests.

## Classification rule

For each ticket we specify:

- **primary component** — the place where the problem manifested or was fixed;
- **additional tags** — affected cross-cutting areas, for example `RBAC`, `Secrets`, `Migration`, `UI`, `Performance`;
- if the cause is not established, the component is marked as **"needs verification"** rather than guessed from the title.

One ticket may affect several components. For example, a project A user accessing a project B task log is classified as `Tasks & execution` with the tags `Auth/RBAC`, `Project isolation`, `Task output`.

## Product components

| Code | Component | What it includes | Main code areas | Typical risks and checks |
|---|---|---|---|---|
| AUTH | Authentication and sessions | Login/logout, API tokens, TOTP, recovery, LDAP, OIDC, external identities, JWT/JWKS | `api/login*.go`, `api/auth.go`, `api/jwks.go`, `services/session_svc.go`, `pkg/jwt/`, `db/Session.go`, `db/UserExternalIdentity.go` | Authentication bypass, session lifetime, logout, token leakage, LDAP/OIDC incompatibility |
| USERS | Users and global administration | Users, administrators, user settings, system information | `api/users.go`, `api/user*.go`, `api/admin_info.go`, `db/User.go`, `cli/cmd/user*.go` | Privilege escalation, user lifecycle, incompatible settings |
| PROJECTS | Projects and isolation | Project creation/modification, members, invitations, project roles, statistics | `api/projects/project*.go`, `api/projects/users.go`, `services/server/project_svc.go`, `db/Project*.go`, `db/Role.go` | Horizontal access, wrong role, deletion of related data, project isolation |
| TEMPLATES | Task templates | Task templates, launch parameters, inventory/repository/environment/key bindings, template permissions | `api/projects/templates.go`, `db/Template*.go`, `api/router.go` | Incorrect relations, parameter overrides, run/edit permissions, backward compatibility |
| WORKFLOWS | Workflows | Workflow definitions, nodes, approvals, runs, and artifacts | `api/projects/workflows.go`, `db/Workflow*.go`, `pro_interfaces/workflow_*`, `pro/services/` | Step order, stopping, approval bypass, partial failure, artifact access |
| TASKS | Tasks and execution | Queue, task lifecycle, local execution, stop/confirm/reject, statuses, and retry | `api/projects/tasks.go`, `api/tasks/`, `services/tasks/`, `db/Task*.go` | Lost/hung tasks, races, wrong status, stopping, parallelism, cleanup |
| OUTPUT | Task output and events | Output persistence, stages, raw output, WebSocket streaming, event log, alerts | `services/tasks/TaskRunner_logging.go`, `pkg/task_logger/`, `api/sockets/`, `db/Event.go`, `services/tasks/alert.go` | Lost lines, secret leakage, stream hangs, large output, wrong order, alerts |
| RUNNERS | Remote runners | Registration, tokens, tags, polling, job assignment and execution, reconciliation | `api/runners/`, `services/runners/`, `services/tasks/RemoteJob.go`, `db/Runner.go`, `cli/cmd/runner*.go` | Wrong assignment, lost connection, duplicate execution, token auth, runner tags, large payload |
| REPOSITORIES | Git repositories | Clone/pull, SSH/HTTPS auth, branches, playbooks, cache | `api/projects/repository.go`, `db_lib/*Git*`, `pkg/git/`, `db/Repository.go`, `api/cache.go` | Private repo auth, branch/ref, timeout, cache invalidation, command injection, unreachable remote |
| INVENTORY | Inventory and target hosts | Static/file/Terraform inventory, aliases, and Terraform state | `api/projects/inventory.go`, `db/Inventory.go`, `db/TerraformInventory*`, `services/server/inventory_svc.go`, `pro_interfaces/terraform_inventory_ctl.go` | Large inventory, invalid format, content leakage, state locking, deletion of a resource in use |
| SECRETS | Keys, secrets, and Variable Groups | Access keys, SSH/login/vault keys, environments, secret storage, sync, task secrets | `api/projects/keys.go`, `api/projects/environment.go`, `api/projects/secret_storages.go`, `services/server/*secret*`, `services/server/access_key_*`, `db/AccessKey.go`, `db/Environment.go`, `db/SecretStorage.go` | Leakage in API/UI/logs/backup, encryption at rest, wrong key, masking, sync, cross-project access |
| SCHEDULES | Schedules and time | Cron/run-at, timezone, activation, scheduler pool | `api/projects/schedules.go`, `services/schedules/`, `db/Schedule.go`, `pkg/tz/` | DST/timezone, repeated/missed run, disable race, recovery after restart |
| INTEGRATIONS | Integrations and webhooks | Webhooks, aliases, matchers, extracted values, external triggers | `api/integration.go`, `api/projects/integration*.go`, `hook_helpers/`, `db/Integration*.go` | Unauthorized launch, wrong matcher, replay, payload parsing, secret verification |
| PROJECT_DATA | Backup, restore, import, and export | Project export/import, backup/restore of related entities | `api/projects/backup_restore.go`, `services/project/`, `services/export/`, `cli/cmd/project_*` | Data loss/duplication, secret leakage, version incompatibility, broken references |
| UI | Web interface | Vue application, forms, tables, routing, task and log display | `web/src/`, `web/tests/` | Wrong form state, hidden errors, permissions only in UI, browser compatibility, accessibility |
| CLI | CLI and setup | Server/setup, user/project/vault commands, migrations, runner management | `cli/`, `cli/cmd/`, `cli/setup/` | Divergence from the API, destructive defaults, validation, exit codes, secret exposure in terminal |

## Platform components

| Code | Component | What it includes | Main code areas | Typical risks and checks |
|---|---|---|---|---|
| API | HTTP API and contract | Router, middleware, request validation, response/error contracts, OpenAPI | `api/router.go`, `api/helpers/`, `api-docs.yml`, `.dredd/` | Documentation diverges from code, wrong status codes, missing validation, incompatible changes |
| RBAC | Authorization and permissions | Global and project roles, permissions on resources and templates | `api/router.go`, auth middleware, `db/Role.go`, `db/ProjectUser.go`, `db/TemplateRole*` | IDOR, horizontal access, privilege escalation, UI hides an allowed backend endpoint |
| DB | Storage and data integrity | Store interfaces, SQL implementations, transactions, constraints, and indexes | `db/`, `db/sql/`, `db/factory/` | SQLite/MySQL/Postgres/Bolt differences, N+1, races, orphan data, incorrect transactions |
| MIGRATIONS | Migrations and upgrades | Schema migrations, version transitions, rekey/compatibility | `db/migration/`, `db/sql/migration*.go`, `deployment/`, `cli/cmd/migrate.go`, `cli/cmd/vault_*` | Upgrade from an old version, data loss, rollback/restart, large databases, secret migration |
| EXECUTORS | Integration with execution tools | Ansible, Terraform/OpenTofu/Terragrunt, Bash, PowerShell, local commands | `db_lib/*App.go`, `services/tasks/*executor*`, `db/ansible.go` | Command arguments, quoting/injection, exit codes, timeouts, incompatible tool versions |
| CONFIG | Application configuration | Env/config file, schema, feature flags, mail/alerts, paths | `config.schema.yaml`, `util/config.go`, `api/options.go`, `db/Option.go` | Wrong defaults, incompatible env vars, validation, secret values in config/logs |
| DEPLOYMENT | Installation and packaging | Docker, compose, systemd, deb/rpm, devcontainer, release artifacts | `deployment/`, `.devcontainer/`, Dockerfile, release workflows | File permissions, volume/data loss, platform/arch, upgrade path, healthcheck |
| HA | Cluster and high availability | Claims, coordination, cluster status, Pro HA boundaries | `api/cluster.go`, `pro_interfaces/ha.go`, `pro/` | Duplicate execution, split brain, stale claim, failover, consistency |
| OBSERVABILITY | Logs, metrics, and diagnostics | Application logs, task logs, metrics, debug log, system info | `pkg/debuglog/`, `pkg/metrics/`, `api/system_info.go`, `api/admin_info.go` | Insufficient diagnostics, PII/secrets in logs, wrong metrics, excessive logging |
| CI | Project build and CI | Unit/integration/e2e jobs, lint, release workflows | `.github/workflows/`, `Taskfile.yml`, `.golangci.yml`, `qodana.yaml`, `.codacy.yml` | Tests not running, flaky pipeline, local/CI divergence, missing artifacts |
| TEST_INFRA | Test infrastructure | E2E environment, fixtures, test cases, Playwright | `test/`, `test/e2e/`, `web/tests/` | Non-reproducibility, shared state, brittle selectors, real secrets, weak diagnostics |

## Cross-cutting tags

These values do not replace the primary component:

| Tag | When to use |
|---|---|
| `Security` | Trust boundary violation, check bypass, injection, insecure default |
| `Secrets` | Possible leakage, corruption, or misuse of sensitive data |
| `RBAC` | The error depends on the role or project membership |
| `Regression` | A previously working scenario is broken by a change |
| `Data loss` | Loss, corruption, or irreversible modification of data |
| `Performance` | Time, CPU, memory, payload size, DB load, or scaling |
| `Concurrency` | Race, duplicate execution, deadlock, queue, or parallel tasks |
| `Compatibility` | OS, browser, DB, Ansible/Terraform version, or legacy data format |
| `Upgrade` | Installing a new version on top of an existing one |
| `Documentation` | Behavior diverges from the documentation or the documentation is insufficient |
| `UX` | A clarity, feedback, or user-error-prevention problem |
| `Flaky` | The result is unstable with identical inputs |

## High-level execution flow map

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

## Notes for issue analysis

- The user complaint and the root cause are different fields. For example, "UI loads forever" may be a DB query or WebSocket defect.
- For a closed ticket, the fix method is confirmed by a PR, commit, or diff. If there is no such link, we write "not established".
- If a ticket is closed without a fix, this is recorded explicitly: duplicate, cannot reproduce, configuration/support question, stale, or won't fix.
- The ticket date in the register should be understood as the creation date; for closed tickets, the closing date is also useful.
