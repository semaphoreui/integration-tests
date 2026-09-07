# Semaphore UI Problem Register

**Analysis start date:** 2026-08-06  
**Repository:** [semaphoreui/semaphore](https://github.com/semaphoreui/semaphore)  
**Current coverage:** an initial verified sample — recent bug issues, open tickets labeled `critical`, and several closed/fixed defects to validate the format.

## How to read the table

- **Date** — the issue creation date.
- **What broke** — the observed behavior, not the presumed root cause.
- **What/how it was fixed** is filled in only when there is a PR, commit, diff, or an unambiguous maintainer comment.
- `Not established` means a fix may exist, but it cannot be reliably linked to the ticket using the available data.
- Components correspond to the [component map](./semaphore-testing-component-map.md).

## Initial table

| Date | Ticket / status | What the user complained about | What broke | What was fixed | How it was fixed | Component |
|---|---|---|---|---|---|---|
| 2026-07-21 | [#4075](https://github.com/semaphoreui/semaphore/issues/4075) · open | When creating a Cron or Run once schedule, the specified template variables disappear immediately after saving | Variables are empty when the form is reopened and are not passed to the scheduled job | Not fixed yet | Fix/PR not found | `SCHEDULES`; additionally `TEMPLATES`, `UI`, `Regression` |
| 2025-01-08 | [#2682](https://github.com/semaphoreui/semaphore/issues/2682) · open · critical | Ansible 2.18 bundled in the Docker image is incompatible with Python 3.6 on managed RHEL 8 hosts | Ansible modules fail with `SyntaxError: future feature annotations is not defined`; the standard image cannot be used for such targets | No complete solution in the ticket; an image variant with Ansible 2.16 appeared, and users also rely on a custom image | The maintainer suggested a custom Dockerfile; multiple images/Execution Environments are under discussion. The ticket remains open | `EXECUTORS`; additionally `DEPLOYMENT`, `Compatibility`, `Ansible` |
| 2025-01-08 | [#2681](https://github.com/semaphoreui/semaphore/issues/2681) · open · critical | After a successful OIDC login with an empty `web_host`, the user lands on a 404 instead of the UI | The session is created, but the redirect is built as a relative path and the browser opens `/api/auth/oidc/.../project/1` | A fix has been prepared but was not merged as of the snapshot date | [PR #3831](https://github.com/semaphoreui/semaphore/pull/3831): extracts `buildOidcRedirectURL`, forces a leading `/` when `web_host` is empty, adds table-driven tests in `api/login_test.go` | `AUTH`; additionally `CONFIG`, `API`, `Regression` |
| 2024-08-19 | [#2294](https://github.com/semaphoreui/semaphore/issues/2294) · open · critical | Semaphore spontaneously launches tasks too frequently; some tasks run indefinitely | The scheduled task lifecycle is broken: unexpected launches and hung executions | Not fixed yet; cause not established | The issue has no maintainer response, PR, or reproducible steps | `SCHEDULES`; additionally `TASKS`, `Concurrency`, `needs reproduction` |
| 2024-08-19 | [#2293](https://github.com/semaphoreui/semaphore/issues/2293) · open · critical | A secret cannot be renamed in an Environment: the UI reports success, but the old name remains | The secret name change is not persisted; no errors in the browser console | Not fixed yet | Fix/PR not found; the request payload, API update, and DB persistence need to be checked | `SECRETS`; additionally `UI`, `API`, `Postgres` |
| 2024-07-01 | [#2152](https://github.com/semaphoreui/semaphore/issues/2152) · open · critical | `SEMAPHORE_DB_PORT` is ignored in the container when Postgres uses a non-standard port | The application keeps using port 5432 regardless of the env var | Not fixed yet | Fix/PR not found; the likely area to check is env/config conversion and the Docker entrypoint, but the cause is not yet confirmed | `CONFIG`; additionally `DEPLOYMENT`, `DB`, `Postgres` |
| 2024-06-20 | [#2125](https://github.com/semaphoreui/semaphore/issues/2125) · open · critical | After version v2.10.7 all schedules stopped running | Cron schedules are ignored, tasks are not created | Not fixed yet; insufficient data | The issue has no logs, maintainer response, or linked PR | `SCHEDULES`; additionally `TASKS`, `Regression`, `needs reproduction` |
| 2024-06-13 | [#2097](https://github.com/semaphoreui/semaphore/issues/2097) · open · critical | After an upgrade, the Task Log link in the email became non-clickable and does not contain the configured `web_host` | The alert builds an incorrect task URL | Not fixed yet | Fix/PR not found; the ticket points to a regression after v2.9.112 and a possible link to #2084 | `OUTPUT`; additionally `CONFIG`, `Alerts`, `Regression` |
| 2024-05-07 | [#1999](https://github.com/semaphoreui/semaphore/issues/1999) · open · critical | After upgrading from 2.9.37 to 2.9.75, one template with cron `*/2 * * * *` gets stuck in Waiting daily | The next scheduled run does not start executing; stopping the waiting job and rebooting helps | Not established; the maintainer could not reproduce | No fix or PR referenced; the template configuration, parallel task limits, DB, and scheduler/task pool logs are needed | `TASKS`; additionally `SCHEDULES`, `Concurrency`, `Regression`, `needs reproduction` |
| 2023-09-05 | [#1459](https://github.com/semaphoreui/semaphore/issues/1459) · closed | A Project Manager could promote themselves to Owner, demote the Owner, or remove them from the project | The server-side project role model and the privilege escalation boundary are broken | The ticket is closed, but evidence of a specific fix has not been found yet | No linked PR/commit; before treating it as fixed, the history of `api/projects/users.go` must be checked and direct API tests for the role matrix written | `PROJECTS`; additionally `RBAC`, `Security`, `Privilege escalation` |
| 2023-04-19 | [#1216](https://github.com/semaphoreui/semaphore/issues/1216) · closed | After a task finished, output lines changed order; `PLAY RECAP` ended up among earlier TASK entries | Lines with the same second were sorted ambiguously, so the stored history differed from the live output | The maintainer marked the ticket as fixed, but the related diff is not referenced | The approach proposed in the issue is to sort `task__output` by `id` rather than by time. The implementation in the current query path still needs to be confirmed separately | `OUTPUT`; additionally `DB`, `Data integrity` |
| 2023-04-12 | [#1211](https://github.com/semaphoreui/semaphore/issues/1211) · open, fix found | When two cron tasks started simultaneously, Semaphore with BoltDB crashed with `panic: Connection schedule already exists` | Parallel `ScheduleRunner` instances used the same non-persistent connection name `schedule` | Fixed in commit [`e2f43bee`](https://github.com/semaphoreui/semaphore/commit/e2f43bee7e4bb13bc0553d371f9ea162e3861c22); the user confirmed the problem is gone starting from v2.9.75 | The connection name was changed from the shared `schedule` to the unique `schedule <scheduleID>` so that parallel schedules do not conflict | `SCHEDULES`; additionally `DB`, `BoltDB`, `Concurrency` |

## Initial findings

### 1. Schedules and the task lifecycle are the most prominent cluster in the initial sample

Even in the small verified sample we immediately see:

- variables lost when saving a schedule (#4075);
- too frequent/spontaneous launches (#2294);
- cron schedules ignored entirely (#2125);
- a task stuck in Waiting (#1999);
- a BoltDB crash on simultaneous launch (#1211).

This already justifies an early set of tests for `create schedule -> persist params -> trigger -> create exactly one task -> complete`, including two schedules at the same time and different DB modes.

### 2. Configuration errors often surface far from the configuration

An empty `web_host` breaks the OIDC redirect (#2681), the same class of setting participates in email alert links (#2097), and the DB port from the environment never reaches the connection (#2152). Table-driven config tests plus several integration smoke scenarios are needed.

### 3. GitHub status cannot be used as the single source of truth

- #1211 is open, although there is a fix commit and user confirmation.
- #2681 is open and has a ready but unmerged PR.
- #1459 is closed, but no link to a fix is given.

During further processing, for every closed ticket the timeline, linked PRs/commits, and the current code must be checked.

### 4. Old `critical` issues require re-triage

Some of them have no logs, reproducible steps, or maintainer reaction. Their value for the test plan is still high as a risk signal, but they must not automatically be treated as confirmed current defects.

## Next pass

### Found locally: survey enum default is not validated in `v2.19.8`

A black-box API run confirmed that a template with an enum `default_value` outside `values` is created with `201` and stores the invalid value. Upstream this is already fixed by commit [`eb29c3e8`](https://github.com/semaphoreui/semaphore/commit/eb29c3e802df4890dc803709954dc373ae8968b2), included in `v2.20.0-alpha1`: backend validation of survey type/default/values compatibility was added. The full reproducer and the criterion for switching the regression check are in `test-environment/survey-default-validation-defect.md`.

### Found locally: task ends in error when no matching runner is available

On `v2.19.8`, a matching runner with exhausted capacity correctly leaves the task in `waiting`, but a temporarily inactive runner or an unknown required tag produces a terminal `error: no runners available`. A runner that appears later cannot pick up the already-finished task, which contradicts the offline recovery expected in TC-027/TC-028. The persistent-runner reproducer, impact, and source-level boundary are described in `test-environment/runner-unavailable-routing-defect.md`.

### Found locally: remote runner loses secret survey variables

On `v2.19.8`, the same survey/task launch succeeds on the local executor but fails on a persistent runner with an undefined variable error: the server clears the non-persistent `task.secret` and does not pass a separate in-memory copy to the remote job. The secret value is not revealed in the output. The defect is fixed upstream by PR [#4086](https://github.com/semaphoreui/semaphore/pull/4086), commit [`081425d2`](https://github.com/semaphoreui/semaphore/commit/081425d2bc20d5fe41def47ec6a429e2e43cf715), and is included in `v2.20.0-alpha1`, but absent from `v2.19.8`. The reproducer and regression criterion are in `test-environment/remote-runner-survey-secrets-defect.md`.

### Found locally: project restore accepts duplicate resource names

On `v2.19.8`, a backup with two repositories of the same name is restored with `200`, and both objects are persisted. Backup relations are defined by names, so the template receives an ambiguous reference. The cause is in the shared `verifyDuplicate`: a duplicate is rejected only when `n > 2`, while two matches pass. The canary also confirms correct neighboring boundaries — `401` for non-admin and `400` for a missing repository reference. The full reproducer, impact, and the additional empty error response gap are in `test-environment/project-backup-restore-validation-defect.md`.

### Regression status: #2293

An API baseline for renaming a Variable Group secret has been added and passes on SQLite and PostgreSQL `v2.19.8`: the new name is persisted, the old value keeps working in a real Ansible task, and no plaintext appears in the API or output. This confirms backend persistence, but the original complaint was about the UI, so the issue cannot be considered fully closed without a separate browser check of the Environment form payload.

1. Sort the remaining open bug issues by component without deep fix analysis.
2. Select 3–5 of the most informative tickets from the `TASKS/SCHEDULES`, `AUTH/RBAC`, `SECRETS`, `REPOSITORIES`, `RUNNERS`, and `MIGRATIONS` clusters.
3. For the selected closed tickets, check the timeline, PR, commit, and current code.
4. Add the fields `closing date`, `version`, `DBMS/installation method`, and `cause confidence` to a machine-readable CSV once the format stabilizes.
