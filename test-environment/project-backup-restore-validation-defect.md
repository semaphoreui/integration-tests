# Defect: project restore accepts duplicate resource names

## Summary

Semaphore UI `v2.19.8` accepts a project backup containing two repositories with the same name.
`POST /api/projects/restore` returns `200`, creates a project and persists both repositories, even
though backup references are name-based and therefore ambiguous.

The same validation path correctly rejects a template that references a missing repository, and
authorization correctly prevents a non-admin user from restoring a project. The duplicate-name
boundary itself is off by one.

## Environment

- Semaphore UI image: `semaphoreui/semaphore:v2.19.8`;
- database: SQLite;
- execution mode: local;
- profile: `core-sqlite-local`;
- reproduced: 2026-08-21.

## Steps to reproduce

1. Create a project containing a repository, inventory and executable template.
2. Export it with `GET /api/project/{project_id}/backup`.
3. Append a copy of `repositories[0]` to the `repositories` array without changing its `name`.
4. Change `meta.name` so the restored project has a unique name.
5. Send the modified document to `POST /api/projects/restore`.
6. Read the restored project's repositories.

Automated reproduction:

```bash
test-environment/profile up core-sqlite-local
./gradlew apiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local \
  --tests io.bookwright.tests.semaphore.ProjectBackupRestoreApiTest
```

## Expected result

Restore returns `400` with a validation error identifying the duplicate repository name. No
project or partial resources are created.

## Actual result

Restore returns `200`. The created project contains two repositories with the same name. A
template referencing that name is linked to whichever matching repository is found first.

## Source-level boundary

`services/project/restore.go` uses the shared `verifyDuplicate` helper for keys, repositories,
inventories, templates and several other backup resources. The helper increments the match count
but reports a duplicate only when `n > 2`. Two equal names therefore pass validation; the correct
boundary is `n > 1`.

## Impact

Backup relationships use resource names instead of original IDs. Allowing duplicate names makes
reference resolution order-dependent and can silently link templates, inventories or schedules to
the wrong restored resource. The issue affects every backup entity using the shared helper, not
only repositories.

## Additional diagnostic gap

A template that references a missing repository does return `400`, but the response body is empty.
The server logs the useful message and prints a stack trace because the validation error is a plain
`fmt.Errorf`, so API clients receive no actionable explanation.

## Suggested correction

Reject the second matching name by changing the duplicate condition to `n > 1`, add unit coverage
for exactly two equal entries for every name-addressed resource type, and ensure backup verification
errors are returned as user-visible validation errors. Restore should complete verification before
persisting the project or any child resource.
