# Resolved investigation: schedule fixture used an invalid task parameter

## Summary

The original schedule investigation reported that active cron and one-shot `run_at` schedules did
not create tasks on `v2.19.8` and `v2.19.12`. That conclusion was incorrect. The test fixture sent
Ansible `task_params.params.limit` as a string:

```json
{"limit":"localhost"}
```

The Ansible task contract expects an array:

```json
{"limit":["localhost"]}
```

Semaphore accepted and persisted the loosely typed schedule payload. At execution time the
scheduler did fire, but task creation failed while decoding the invalid value into
`AnsibleTaskParams.limit []string`.

## Diagnostic evidence

The decisive server log from `develop@e95560fd` was:

```text
failed to add task error="json: cannot unmarshal string into Go struct field AnsibleTaskParams.limit of type []string"
```

The API test only observed an empty task list and therefore misclassified the failure as a scheduler
that never fired. The corrected fixture uses `"limit":["localhost"]` and preserves the same
non-UTC timezone and real task execution boundary.

## Corrected coverage

The `feature-schedule-timezone` profile now verifies:

1. an active one-shot schedule creates a successful task and then becomes inactive;
2. `delete_after_run=true` creates a successful task and removes the schedule;
3. deleting the schedule clears the historical task's `schedule_id`, as defined by the database
   foreign key `ON DELETE SET NULL`;
4. an active cron schedule fires in `Pacific/Kiritimati`, remains active, and creates a successful
   task with the stored message and Ansible limit;
5. all scheduled tasks execute the trusted fixture and emit the expected safe output marker.

Automated regression:

```bash
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

## Verification

- `semaphoreui/semaphore:v2.19.12`: all three corrected scenarios pass on a clean SQLite profile;
- `develop@e95560fd81a6dc510957c5c74aff07f656bb1ccc`: all three corrected scenarios pass;
- verification performed locally on macOS/Docker Desktop on 2026-09-22.

BUG-003 is withdrawn from the active product-defect list. The schedule profile is promoted from a
manual red reproducer to the daily green configuration matrix.

## Test-infrastructure follow-up

The version comparison also exposed that Gradle could mark `apiTest` as `UP-TO-DATE` after the
application image changed. All test tasks now opt out of Gradle output caching because the tested
service is mutable state outside Gradle's input graph.
