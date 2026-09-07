# Upgrade Check: Semaphore v2.19.6 → v2.19.7

**Check date:** 2026-08-14

**Status:** reproducible blocking product defect
**DBMS:** SQLite and PostgreSQL 14.3

## What Is Checked

A linked fixture is created on the release image `semaphoreui/semaphore:v2.19.6`:

```text
project → encrypted access key → repository → inventory → template
→ successful task/output → inactive schedule
```

After a successful seed phase, the server container is replaced with `semaphoreui/semaphore:v2.19.7`. The database and Git fixture volumes are left unchanged. The verify phase logs in with the old admin account, finds the saved project, and must check all resources, the old task output, and a re-run of the template.

## Actual Result

The result is the same for SQLite and PostgreSQL:

1. `v2.19.6` starts on a clean database, creates the fixture, and successfully runs the Ansible task.
2. `v2.19.7` starts on the same database and logs in successfully.
3. The saved project, repository, inventory, template, schedule, and successful task are available.
4. `GET /api/project/1/keys` returns `400` with an empty response body.
5. The server log contains: `gorp: no fields [task_id expire_at] in type AccessKey`.

Because the access key API fails, it is impossible to confirm that the saved credentials are available and to safely continue running the templates that depend on them. The upgrade is considered failed.

## Root Cause

Tag `v2.19.6` (`ff0cf4cbaa5760ea57fb02973b9f909e619b1856`) contains and applies the `v2.20.0` and `v2.20.1` migrations. Migration `v2.20.1` adds:

- `access_key.task_id`;
- `access_key.expire_at`;
- index `access_key__task_id`.

Tag `v2.19.7` (`e9dc41a1de8a747569334f7a2b76c320b945d4f0`) removes these migration entries and files, as well as the `TaskID` and `ExpireAt` fields from the `AccessKey` Go model. The already applied schema is not rolled back. Gorp receives extra columns from the existing table and cannot map them to the model of the current release.

This is not a test DTO mismatch: the error occurs inside the server while reading the database and is confirmed on two dialects.

## Reproduction

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

On failure the command keeps the current containers and volumes and prints the latest server logs. A re-run starts from clean volumes of the selected upgrade profile only.

## Fix Criteria

- `v2.19.7` or the next fixed release correctly opens a database created by `v2.19.6`;
- listing and individual reads of old access keys return a successful response without plaintext secrets;
- the repository/inventory links to the saved key remain unchanged;
- the old task output is available;
- the saved template re-runs successfully;
- the regular core API suite passes after the upgrade;
- the scenario is green for SQLite and PostgreSQL.

MySQL and MariaDB use the same shared migration but were not run separately: two confirmed dialects are sufficient for the initial localization. After the fix, they should be added to the release compatibility job if the support policy requires a full upgrade gate for every DBMS.
