# Semaphore upgrade check v2.19.6 → v2.19.7

**Check date:** 2026-08-14

**Status:** reproducible blocking product defect
**Database:** SQLite and PostgreSQL 14.3

## What is verified

On the release image `semaphoreui/semaphore:v2.19.6`, a linked fixture is created:

```text
project → encrypted access key → repository → inventory → template
→ successful task/output → inactive schedule
```

After a successful seed phase, the server container is replaced with `semaphoreui/semaphore:v2.19.7`. The database and Git fixture volumes are not changed. The verify phase logs in with the old admin account, finds the preserved project, and must check all resources, the old task output, and re-execution of the template.

## Actual result

The result is the same for SQLite and PostgreSQL:

1. `v2.19.6` starts on a clean database, creates the fixture, and successfully runs an Ansible task.
2. `v2.19.7` starts on the same database and logs in successfully.
3. The preserved project, repository, inventory, template, schedule, and successful task are accessible.
4. `GET /api/project/1/keys` returns `400` with an empty response body.
5. The server log contains: `gorp: no fields [task_id expire_at] in type AccessKey`.

Because of the access key API failure, it is impossible to confirm that the preserved credentials are available and to safely continue running the templates that depend on them. The upgrade is considered failed.

## Cause

Tag `v2.19.6` (`ff0cf4cbaa5760ea57fb02973b9f909e619b1856`) contains and applies the `v2.20.0` and `v2.20.1` migrations. Migration `v2.20.1` adds:

- `access_key.task_id`;
- `access_key.expire_at`;
- the `access_key__task_id` index.

Tag `v2.19.7` (`e9dc41a1de8a747569334f7a2b76c320b945d4f0`) removes these migration entries and files, as well as the `TaskID` and `ExpireAt` fields from the Go `AccessKey` model. The already-applied schema is not rolled back. Gorp sees extra columns in the existing table and cannot map them to the current release's model.

This is not a mismatch in a test DTO: the error occurs inside the server when reading the database and is confirmed on two dialects.

## Reproduction

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

On failure, the command preserves the current containers and volumes and prints the latest server logs. A repeated run starts from clean volumes of only the selected upgrade profile.

## Fix criteria

- `v2.19.7` or the next fixed release correctly opens a database created by `v2.19.6`;
- listing and individual reads of old access keys return a successful response without plaintext secrets;
- the repository/inventory links to the preserved key do not change;
- the old task output is accessible;
- the preserved template re-executes successfully;
- the regular core API suite passes after the upgrade;
- the scenario is green for SQLite and PostgreSQL.

MySQL and MariaDB use the same shared migration but were not run separately: two confirmed dialects are enough for initial localization. After the fix, they should be added to the release compatibility job if the support policy requires a full upgrade gate for every database.
