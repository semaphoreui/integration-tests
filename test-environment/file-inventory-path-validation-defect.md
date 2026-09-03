# Defect: file inventory create accepts a path outside its repository

## Summary

Semaphore UI `v2.19.8` accepts a repository-backed file inventory whose path starts with `../`.
`POST /api/project/{project_id}/inventory` returns `201` and persists the unsafe path, while `PUT`
for the same inventory returns `400` with an empty response body.

## Environment

- Semaphore UI image: `semaphoreui/semaphore:v2.19.8`;
- database: SQLite;
- execution mode: local;
- profile: `core-sqlite-local`;
- reproduced: 2026-08-24.

## Steps to reproduce

1. Create a project, a `none` access key and a local Git repository.
2. Create an inventory with `type=file`, the repository ID and
   `inventory=../bookwright-outside-repository.ini`.
3. Observe that create returns `201` and the response retains the traversal path.
4. Send the same object to `PUT /api/project/{project_id}/inventory/{inventory_id}`.
5. Observe that update returns `400` with an empty response body.

Automated reproduction:

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.FileInventoryApiTest
```

## Expected result

Create and update apply the same validation. Both operations reject an absolute path or a relative
path that resolves outside the selected inventory repository, and no inventory is persisted.

## Actual result

Create returns `201` and persists `../bookwright-outside-repository.ini`. Update of that same
object returns `400`.

The server-side update branch constructs `Invalid inventory file pathname. Must be:
path/to/inventory.`, but the `v2.19.8` API response does not expose it to the client.

## Source-level boundary

`api/projects/inventory.go` calls `IsValidInventoryPath` only in `UpdateInventory`. `AddInventory`
checks the inventory type and referenced access keys, but persists a `file` path without calling
the path validator.

During task preparation, `services/tasks/local_executor.go` joins a repository-backed inventory
path with the cloned repository directory. A traversal segment can therefore resolve outside the
checkout and make Ansible attempt to consume a different file readable by the Semaphore process.

## Impact

The API accepts configuration that the edit path considers invalid. Besides confusing users who
can create but not save the same inventory later, this weakens the repository boundary for a file
that is passed to Ansible as inventory input. The automated canary does not execute the unsafe
inventory; it only proves persistence and the create/update validation mismatch.

## Suggested correction

Use one shared validation function in both create and update. Validate the inventory path relative
to the selected repository root after normalization, reject absolute and escaping paths, and add
table-driven API tests covering valid nested paths, `..` traversal and platform-specific absolute
paths. Return the validation reason in the `400` response using the normal public error contract.
