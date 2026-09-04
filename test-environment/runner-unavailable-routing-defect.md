# Defect candidate: tasks fail when no matching runner is available

## Summary

Semaphore UI `v2.19.12` treats two remote-runner conditions differently:

- a matching online runner at capacity re-queues the task in `waiting`;
- no active runner with the required tag immediately moves the task to terminal `error` with
  `no runners available`.

The second behavior means temporary runner downtime and a currently unmatched tag cannot recover
when infrastructure becomes available. It contradicts the expected behavior recorded in upstream
manual cases TC-027 and TC-028, where the task should remain waiting and later be picked up.

## Environment

- Semaphore server: `semaphoreui/semaphore:v2.19.12`;
- Semaphore runner: `semaphoreui/runner:v2.19.12`;
- database: PostgreSQL 14.3;
- execution: persistent remote runner;
- profile: `prod-postgres-runner`;
- initially reproduced: 2026-08-21 on `v2.19.8`;
- reconfirmed: 2026-09-04 in Linux CI on `v2.19.12`,
  [run 33871024329](https://github.com/semaphoreui/integration-tests/actions/runs/33871024329).

## Automated reproduction

```bash
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner \
  --tests io.bookwright.tests.semaphore.RunnerRoutingApiTest
```

The test temporarily assigns a tag and capacity `1` to the managed runner. It first proves that a
second matching task stays in `waiting` while the runner is busy. It then covers two unavailable
paths:

1. set the matching runner to `active=false` and launch a tagged task;
2. reactivate the runner and launch a task requiring a non-existent tag.

## Expected result

Both unavailable tasks remain in `waiting`. Reactivating or registering a matching runner should
allow the existing task to proceed without a new launch.

## Actual result

Both tasks transition `waiting → starting → error` in about one second. Task output contains:

```text
Failed to run task: no runners available
```

A fresh task launched after reactivation succeeds on the expected runner, proving that the runner
and tagged routing configuration are otherwise valid.

## Source-level boundary

`services/tasks/RemoteJob.go` returns a generic error when the matching runner collection is empty,
but returns `ErrAllRunnersBusy` when matching runners exist and have no free capacity.
`services/tasks/TaskRunner.go` re-queues only `ErrAllRunnersBusy`; every other runner-selection
error finalizes the task as `error`.

## Impact

Self-hosted customers can lose queued automation during runner restarts, maintenance, autoscaling
gaps or temporary tag-capacity changes. Retrying requires creating a new task manually or through
external orchestration. The distinction between “busy” and “temporarily absent” is not useful to a
queue that is expected to recover from infrastructure availability changes.

## Suggested correction

Represent “no matching online runner currently available” as a retryable dispatch condition and
re-queue it like runner capacity exhaustion. Permanent configuration mistakes can be surfaced in
the UI/API as a warning while the task remains cancellable in `waiting`. Regression coverage should
verify offline-to-online recovery and late registration of a matching tag.
