# Semaphore UI known defects

**Last reviewed:** 2026-09-04  
**Core runtime baseline:** `semaphoreui/semaphore:v2.19.12`

**Runner and scheduler defect baseline:** `semaphoreui/semaphore:v2.19.8`

**Current stable release checked:** [`v2.19.12`](https://github.com/semaphoreui/semaphore/releases/tag/v2.19.12)

This document consolidates product defects found by the integration test suite. Runtime behaviour was
initially reproduced on `v2.19.8`. The core suite was rerun on `v2.19.12` in Linux CI on 2026-09-04;
runner- and scheduler-specific defects still require an equivalent current-stable run unless stated
otherwise. Affected source paths were also compared with the `v2.19.12` tag and current `develop`.

The defects are API, scheduler and runner failures rather than rendering problems. Screenshots would
not add useful evidence; request/response contracts, process state and server log excerpts are included
instead. All reproducers use generated fixtures and do not expose credentials or secret values.

## Summary

| ID | Title | Severity | Component | Current status |
|---|---|---:|---|---|
| BUG-001 | Project deletion succeeds while a task is still running | High | Projects / task execution | Confirmed in Linux CI on `v2.19.12` |
| BUG-002 | File inventory creation accepts a path outside its repository | High | Inventory API / path validation | Confirmed in Linux CI on `v2.19.12` |
| BUG-003 | Active schedules do not create tasks | High | Scheduler | Confirmed locally and in Linux CI on `v2.19.8`; rerun on current stable required |
| BUG-004 | Remote runner loses secret survey variables | High | Remote runner dispatch / secrets | Confirmed on `v2.19.8`; fixed on `develop`, not in `v2.19.12` |
| BUG-005 | Tasks fail when no matching runner is temporarily available | High | Runner routing / task queue | Reproduced on `v2.19.8`; product-contract decision required |
| BUG-006 | One-off runner does not exit after a completed task | Medium | Runner lifecycle | Confirmed on `v2.19.8`; defective condition remains in `v2.19.12` and `develop` |
| BUG-007 | Project restore accepts duplicate resource names | Medium | Project backup / restore | Confirmed in Linux CI on `v2.19.12`; off-by-one remains on `develop` |
| BUG-008 | Survey enum accepts a default outside its allowed values | Medium | Template survey validation | Confirmed on `v2.19.8`; fixed on `develop`, not in `v2.19.12` |
| BUG-009 | Successful short task loses `stdout` or `stderr` | High | Task execution / output collection | Confirmed in Linux CI on `v2.19.12`; fixed on `develop` |

## BUG-001 — Project deletion succeeds while a task is still running

**Description**

`DELETE /api/project/{project_id}` returns `204` and removes a project while one of its tasks is
still `running`. The executor continues the playbook after deletion and later writes task state and
events against deleted database records. SQLite then reports foreign-key violations.

**Impact**

Automation may continue changing customer infrastructure after an operator has received confirmation
that the project was deleted. The final task status and audit trail can no longer be persisted reliably.

**Steps to reproduce**

1. Create a project with an access key, repository, inventory and runnable template.
2. Start a harmless playbook that emits a readiness marker and pauses for 10 seconds.
3. Wait for the marker and confirm that the task status is `running`.
4. Send `DELETE /api/project/{project_id}`.
5. Confirm the `204` response and that the project is no longer returned by the API.
6. Wait for the executor to finish and inspect the Semaphore server log.

**Expected:** deletion is rejected with `409 Conflict` until all project tasks are terminal, or the
server stops and joins the executors before deleting project data.

**Actual:** the request returns `204`; the playbook continues and the server later reports:

```text
Task status updated status=success task_id=138
Fatal error inserting an event: constraint failed: FOREIGN KEY constraint failed (787)
```

**Automated reproducer**

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.ProjectDeletionApiTest
```

**Detailed evidence:** [project-deletion-running-task-defect.md](project-deletion-running-task-defect.md)

## BUG-002 — File inventory creation accepts a path outside its repository

**Description**

The create endpoint accepts a repository-backed file inventory whose path starts with `../`, persists
it and returns `201`. Updating the same inventory returns `400`. The create and update paths therefore
apply different validation, and a stored path can resolve outside the selected repository checkout.

**Impact**

The API persists a configuration that the edit path considers invalid. More importantly, a traversal
path can make Ansible consume another file readable by the Semaphore process instead of a file from
the selected repository.

**Steps to reproduce**

1. Create a project, a `none` access key and a local Git repository.
2. Send `POST /api/project/{project_id}/inventory` with `type=file`, the repository ID and
   `inventory=../bookwright-outside-repository.ini`.
3. Observe `201` and read the persisted traversal path from the response.
4. Send the same object to `PUT /api/project/{project_id}/inventory/{inventory_id}`.
5. Observe `400` with an empty response body.

**Expected:** create and update both reject absolute and repository-escaping paths with an actionable
`400` response, and no invalid inventory is persisted.

**Actual:** create returns `201`; update rejects the same payload. In the current stable source,
`IsValidInventoryPath` is still called only by `UpdateInventory`.

**Automated reproducer**

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.FileInventoryApiTest.createAcceptsUnsafePathAlthoughUpdateRejectsIt
```

**Detailed evidence:** [file-inventory-path-validation-defect.md](file-inventory-path-validation-defect.md)

## BUG-003 — Active schedules do not create tasks

**Description**

An active schedule is persisted and returned by the API, but Semaphore creates no task when either a
cron occurrence or one-shot `run_at` time is reached. Manual execution of the same template succeeds.

**Impact**

Schedules appear correctly configured while unattended automation silently does not run. This is a
core reliability failure for scheduled infrastructure operations.

**Steps to reproduce: one-shot schedule**

1. Create a project with a valid repository, inventory and runnable template.
2. Prove that manual execution of the template succeeds.
3. Create an active `run_at` schedule for 15 seconds in the future.
4. Read the schedule back and confirm its timestamp and `active=true`.
5. Poll project tasks through the scheduled time and for another 90 seconds.

The cron variant uses an occurrence 15–75 seconds in the future in the configured
`Pacific/Kiritimati` timezone and produces the same result.

**Expected:** Semaphore creates exactly one task linked to the schedule and the task completes
successfully.

**Actual:** the schedule remains active, the task collection stays empty and the server log contains
no task-creation attempt or scheduler error.

**Automated reproducer**

```bash
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

**Detailed evidence:** [schedule-execution-defect.md](schedule-execution-defect.md)

## BUG-004 — Remote runner loses secret survey variables

**Description**

Semaphore accepts a task launch containing a survey variable of type `secret`, but a persistent
remote runner receives no value for it. The same template and payload succeed with local execution.

**Impact**

A template can work in a local installation and fail after moving to the production-like remote-runner
deployment model. Tasks depending on secret survey values terminate with an undefined-variable error.

**Steps to reproduce**

1. Start the `prod-postgres-runner` profile.
2. Create an Ansible template with a required survey variable of type `secret`.
3. Launch the task through `POST /api/project/{project_id}/tasks`, providing the value in the request
   `secret` object.
4. Let the registered persistent runner execute a playbook that consumes the variable under `no_log`.
5. Inspect the task status and sanitized output.

**Expected:** the remote runner receives the secret, the task succeeds and the plaintext value is
absent from API responses, logs and test artifacts.

**Actual:** the task reaches `error` because the survey variable is undefined. The secret itself is
not leaked.

**Automated reproducer**

```bash
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner \
  --tests io.bookwright.tests.semaphore.SurveyAndTaskOverridesApiTest.remoteRunnerLosesSurveySecret
```

**Upstream status:** fixed by [PR #4086](https://github.com/semaphoreui/semaphore/pull/4086), commit
[`081425d2`](https://github.com/semaphoreui/semaphore/commit/081425d2bc20d5fe41def47ec6a429e2e43cf715),
on `develop`; the fix is not contained in the current stable `v2.19.12` tag.

**Detailed evidence:** [remote-runner-survey-secrets-defect.md](remote-runner-survey-secrets-defect.md)

## BUG-005 — Tasks fail when no matching runner is temporarily available

**Description**

When matching runners exist but are at capacity, Semaphore re-queues the task in `waiting`. When no
active runner has the requested tag, Semaphore instead changes the task to terminal `error` with
`no runners available`. The task cannot recover when a matching runner appears later.

**Impact**

Self-hosted customers lose queued automation during runner restart, maintenance, autoscaling gaps or
temporary tag changes. Recovery requires launching a new task externally or manually.

**Steps to reproduce**

1. Start `prod-postgres-runner` and configure the managed runner with a unique tag and capacity `1`.
2. Launch two matching tasks and confirm that the second waits while the first occupies the runner.
3. Disable the matching runner and launch another tagged task.
4. Re-enable the runner and observe the existing task.
5. Repeat with a tag for which no runner is registered.

**Expected:** unavailable tasks remain cancellable in `waiting` and are dispatched when a matching
runner becomes available.

**Actual:** both unavailable paths transition `waiting → starting → error` in about one second:

```text
Failed to run task: no runners available
```

**Automated reproducer**

```bash
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner \
  --tests io.bookwright.tests.semaphore.RunnerRoutingApiTest
```

**Status note:** the behaviour is reproducible and the relevant code is unchanged in `v2.19.12` and
`develop`, but the product team should confirm that recoverable waiting is the intended contract for
an unmatched tag.

**Detailed evidence:** [runner-unavailable-routing-defect.md](runner-unavailable-routing-defect.md)

## BUG-006 — One-off runner does not exit after a completed task

**Description**

A webhook-launched runner receives and completes exactly one task, reports terminal `success`, and
the server sends the `finish` webhook. Despite `SEMAPHORE_RUNNER_ONE_OFF=true`, the runner process
remains alive.

**Impact**

Autoscaling launchers can leak runner processes or containers. Later launches may contend with an old
runner that should already have terminated.

**Steps to reproduce**

1. Start the isolated dynamic-runner profile.
2. Register a global runner with start and finish webhooks and make it the default runner.
3. On the start webhook, launch `semaphore runner start --no-config` with
   `SEMAPHORE_RUNNER_ONE_OFF=true`.
4. Execute one real Ansible task and wait for terminal `success` and the finish webhook.
5. Inspect the launcher events and process table.

**Expected:** after terminal progress is accepted, the runner exits with code `0`.

**Actual:** `webhook_start`, `runner_started` and `webhook_finish` occur once, but `runner_exited`
never appears and the process remains alive.

```text
PID  COMMAND
1    python3 /dynamic-runner-launcher.py
76   /usr/local/bin/semaphore runner start --no-config
```

**Automated reproducer**

```bash
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

**Source evidence:** `sendProgress()` deletes the finished job before the one-off exit condition checks
`runningJobsCount() > 0`. The same condition remains in `v2.19.12` and current `develop`.

**Detailed evidence:** [dynamic-runner-one-off-exit-defect.md](dynamic-runner-one-off-exit-defect.md)

## BUG-007 — Project restore accepts duplicate resource names

**Description**

`POST /api/projects/restore` accepts a backup containing two repositories with the same name, returns
`200` and persists both objects. Backup references are name-based, so subsequent relationship
resolution becomes ambiguous.

**Impact**

Templates, inventories or schedules can be silently linked to the wrong restored resource. The common
validator is used by several backup entity types, so the issue is broader than repositories.

**Steps to reproduce**

1. Create a project containing a repository, inventory and executable template.
2. Export it through `GET /api/project/{project_id}/backup`.
3. Append a copy of `repositories[0]` without changing its `name`.
4. Change `meta.name` to avoid a project-name collision.
5. Restore the modified document through `POST /api/projects/restore`.
6. Read the restored project's repositories.

**Expected:** restore returns `400`, identifies the duplicate name and creates no partial project.

**Actual:** restore returns `200` and the project contains two repositories with the same name.

**Automated reproducer**

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.ProjectBackupRestoreApiTest
```

**Source evidence:** the shared `verifyDuplicate` helper reports a duplicate only when `n > 2`; it
should reject the second match with `n > 1`. The off-by-one remains in `v2.19.12` and `develop`.

**Detailed evidence:** [project-backup-restore-validation-defect.md](project-backup-restore-validation-defect.md)

## BUG-008 — Survey enum accepts a default outside its allowed values

**Description**

The backend accepts and persists an enum survey variable whose `default_value` is absent from its
`values` list. The launch form cannot represent the stored default as a valid choice.

**Impact**

Invalid templates can be created through the API or an outdated client. Default selection and actual
task parameters can diverge because validation happens too late or only in the browser.

**Steps to reproduce**

1. Create a valid project, repository and inventory.
2. Send `POST /api/project/{project_id}/templates` with this survey variable:

   ```json
   {
     "name": "deployment_env",
     "type": "enum",
     "values": [
       {"name": "Development", "value": "dev"},
       {"name": "Production", "value": "prod"}
     ],
     "default_value": "qa"
   }
   ```

3. Read the created template through `GET /api/project/{project_id}/templates/{template_id}`.

**Expected:** creation returns `400` and explains that `qa` is not an allowed enum value.

**Actual:** creation returns `201` and persists `default_value: qa`.

**Upstream status:** fixed on `develop` by
[`eb29c3e8`](https://github.com/semaphoreui/semaphore/commit/eb29c3e802df4890dc803709954dc373ae8968b2).
The fix is not contained in the current stable `v2.19.12` tag.

**Detailed evidence:** [survey-default-validation-defect.md](survey-default-validation-defect.md)

## BUG-009 — Successful short task loses `stdout` or `stderr`

**Description**

A short Bash task reaches terminal `success`, but its persisted output can contain only one process
stream. In one Linux CI scenario Semaphore lost the entire stderr marker; in another it lost the
entire stdout marker. Reading output after the terminal status does not recover the missing stream.

**Impact**

Successful automation may have an incomplete audit trail. Operators can lose warnings, diagnostics
or machine-readable values written intentionally to one stream, while the task status gives no
indication that log capture was incomplete.

**Steps to reproduce**

1. Create a project with a local Git repository, inventory and Bash template.
2. Make the script write distinct no-newline markers to stdout and stderr:

   ```bash
   printf 'semaphore-shell-stdout-marker'
   printf 'semaphore-shell-stderr-marker' >&2
   ```

3. Start the task and wait for terminal `success`.
4. Read the task output through the API and assert that both markers are present.
5. Repeat with a background child (`sleep 60 &`) to exercise inherited output pipes.

**Expected:** the task completes promptly and the persisted output contains both markers.

**Actual:** each task reports `success`, but the normal scenario can omit stdout while the
background-child scenario can omit stderr (or vice versa).

**Automated reproducer**

```bash
test-environment/profile up feature-shell-output
test-environment/profile test feature-shell-output
```

**CI evidence:** [run 33866188839](https://github.com/semaphoreui/integration-tests/actions/runs/33866188839).
The failure is API/process-lifecycle based, so a screenshot would not add useful evidence; the run
contains JUnit XML, Allure results and sanitized Compose logs.

**Upstream status:** fixed after `v2.19.12` by
[`5c2d6e34`](https://github.com/semaphoreui/semaphore/commit/5c2d6e34bed587b3cbea029c0799c5577e781800)
and
[`4976e916`](https://github.com/semaphoreui/semaphore/commit/4976e91699886157184d04fa2069e912e40156b9).
The first fix registers readers before waiting and drains both pipes before `cmd.Wait`; the second
adds command-scoped finalization and bounds pipes inherited by background descendants.

The strict test remains in the manual expected-red `feature-shell-output` profile until these fixes
reach a stable release. It is intentionally excluded from the green PR and nightly gates.

## Excluded historical and policy findings

- The `v2.19.6 → v2.19.7` schema upgrade failure is historical; the maintained
  `v2.19.7 → v2.19.8` upgrade path passes. It remains documented in
  [upgrade-report.md](upgrade-report.md).
- Missing password-login rate limiting is tracked as a security-hardening gap rather than a confirmed
  product defect because no explicit product contract was identified. See
  [password-login-brute-force-protection-gap.md](password-login-brute-force-protection-gap.md).

## Recommended revalidation order

1. Rerun BUG-003 on the current stable schedule profile.
2. Rerun the production-like remote runner profile for BUG-004 and BUG-005.
3. Run BUG-006 manually because its expected failure intentionally leaves the one-off process alive.
4. Test `develop` for BUG-004, BUG-008 and BUG-009 and convert their canaries to positive regression checks.
5. Preserve Allure results and sanitized server logs as attachments when upstream issues are created.
