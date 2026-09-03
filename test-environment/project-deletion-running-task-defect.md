# Defect: deleting a project does not stop its running task

## Summary

Semaphore UI `v2.19.8` accepts project deletion while a task in that project is `running`.
`DELETE /api/project/{project_id}` returns `204` and removes the project, but the executor continues
the playbook and later writes task state against already deleted database records. On SQLite this
ends with foreign-key errors in the server log.

## Environment

- Semaphore UI image: `semaphoreui/semaphore:v2.19.8`;
- database: SQLite;
- execution mode: local;
- profile: `core-sqlite-local`;
- reproduced: 2026-08-24.

## Steps to reproduce

1. Create a project with an access key, repository, inventory and template.
2. Start a harmless playbook that prints a readiness marker and then pauses for 10 seconds.
3. Wait until task output contains the readiness marker and its status is `running`.
4. Delete the project through `DELETE /api/project/{project_id}`.
5. Observe `204`; both project detail and project list confirm that it has been deleted.
6. Wait for the already running executor to finish and inspect the Semaphore server log.

Automated reproduction:

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.ProjectDeletionApiTest
```

The same test class first proves the supported path: stop the running task, wait for terminal
`stopped`, then delete the project and all dependent resources successfully.

## Expected result

Project deletion is rejected with a conflict while any project task is non-terminal, and the
response explains which task must be stopped. Alternatively, deletion must synchronously stop and
join every executor before removing task and project records. No automation may continue after the
API reports that its project has been deleted.

## Actual result

The delete request returns `204` while the task is `running`. The executor keeps working for about
10 seconds and reports `success` for the deleted task. Semaphore then logs:

```text
Task status updated status=success task_id=138
Fatal error inserting an event: constraint failed: FOREIGN KEY constraint failed (787)
constraint failed: FOREIGN KEY constraint failed (787)
```

The test uses only a debug marker and a pause; it has no external side effects.

## Source-level boundary

`api/projects/project.go` delegates deletion directly to `ProjectService.DeleteProject` and clears
the temporary directory without checking project tasks. `services/server/project_svc.go` delegates
straight to the project store. `db/sql/project.go` deletes project resources and the project inside
a transaction, but it does not coordinate with the in-memory task pool or wait for executors.

The task logger therefore remains alive after the rows it references have been cascaded or deleted.
Its later status, stage and event writes target missing parents and violate referential integrity.

## Impact

Automation can continue after an operator receives confirmation that the containing project no
longer exists. A real playbook may still change customer infrastructure, while its final task
state and audit events cannot be persisted reliably. The resulting foreign-key errors also pollute
server diagnostics and can hide the actual completion outcome.

## Suggested correction

Before project deletion, query non-terminal tasks and return a diagnostic `409 Conflict` containing
their IDs. Keep the operation available after every task reaches a terminal state. If product
semantics require automatic cancellation, stop and join the executors first and only then perform
the database transaction. Add API regression coverage for `waiting`, `starting`, `running`,
`stopping` and terminal tasks, plus an assertion that no late database writes or FK errors occur.
