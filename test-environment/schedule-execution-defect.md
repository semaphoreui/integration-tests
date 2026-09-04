# Defect: active schedules do not create tasks

## Summary

An active schedule created through the API is persisted and returned as active, but no task is
created when either its cron occurrence or one-shot `run_at` time is reached.

## Environment

- Semaphore UI `v2.19.12` (`012ed06d3eccadaed594c73b93b3d8a2459b576f`);
- image `semaphoreui/semaphore:v2.19.12`;
- Community Edition, SQLite, local task execution;
- `SEMAPHORE_SCHEDULE_TIMEZONE=Pacific/Kiritimati`;
- Docker Desktop on macOS arm64;
- profile `feature-schedule-timezone`.

Both variants were reconfirmed locally on the current stable release on 2026-09-04. Earlier Linux CI
evidence exists for `v2.19.8`; a current-stable Linux investigation run is still useful as an
additional platform check but is no longer needed to establish that the release is affected.

For Linux confirmation, run the `Configuration matrix` workflow manually with
`include_schedule_investigation=true`. The schedule profile is opt-in and is not part of the daily
matrix.

## Preconditions

1. A project contains a valid access key, repository, static inventory and runnable Ansible
   template.
2. Manual execution of the same template succeeds and emits
   `semaphore-bookwright-smoke-ok`.
3. `/api/info` returns `schedule_timezone: Pacific/Kiritimati`.

## Steps to reproduce

### Cron

1. Calculate a cron occurrence 15–75 seconds in the future in `Pacific/Kiritimati`.
2. Create a schedule through `POST /api/project/{project_id}/schedules` with that cron expression
   and `active: true`.
3. Confirm that create/GET returns the same cron and `active: true`.
4. Poll `GET /api/project/{project_id}/tasks` through the occurrence and for another 90 seconds.

### One-shot

1. Create a `type: run_at` schedule with `active: true` and `run_at` 15 seconds in the future.
2. Confirm that create/GET returns the expected timestamp.
3. Poll the project tasks for 90 seconds after `run_at`.

Automated reproducer:

```bash
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

## Actual result

- Schedule creation returns `201`.
- Schedule is persisted and returned active immediately after creation.
- Project task collection remains empty: no task with the schedule ID is created.
- The server log contains no task creation attempt or scheduler error.
- Reproduced twice for cron and once for `run_at` on `v2.19.8` on 2026-08-19.
- Reproduced once for cron and once for `run_at` on `v2.19.12` on 2026-09-04; both automated
  scenarios reached their task-creation timeout.

## Expected result

At the configured occurrence Semaphore creates exactly one task with the schedule ID and stored
task parameters. The task executes the selected template and reaches `success`.

## Impact

Schedules appear correctly configured in the API/UI but do not run automation. This affects a
core unattended-execution use case and is treated as high severity.

## Suspected component

Backend schedule lifecycle, specifically the boundary between schedule CRUD refresh and
`services/schedules/SchedulePool`. The black-box evidence does not yet prove the exact code-level
cause.
