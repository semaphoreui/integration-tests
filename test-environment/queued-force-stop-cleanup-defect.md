# Force-stopped queued task can execute after the running task releases capacity

## Observed environment

- Local `core-sqlite-local` profile, SQLite, execution inside the Semaphore container.
- Application image built from `semaphoreui/semaphore` commit `232bfb242b1eeda93717098d46283a7582279f0a`.
- Reproduced on 2026-09-25 while adding failure-path task cleanup coverage.
- This is evidence for that application revision; newer revisions have not been verified here.

## Reproduction

1. Create an isolated project with `max_parallel_tasks=1` and a template with parallel tasks enabled.
2. Start task A using `ansible/long-running.yml`; wait for its ready marker.
3. Start task B using the same template; verify it stays `waiting` behind A.
4. Call `POST /api/project/{projectId}/tasks/{taskB}/stop` with `{"force":true}`.
5. Observe B reported as `stopped`, then attempt to delete B.
6. Stop A and observe whether B is subsequently started by the executor.

## Expected

Cancelling a queued task removes it from queue admission. Its playbook must not start later,
and the completed cancellation must allow its task record to be removed.

## Actual

Task B was marked `stopped` but remained in the queue. DELETE returned empty HTTP 400 for the
entire 15-second cleanup timeout. Once A stopped, the executor started B anyway.

The reproduction's server log recorded this sequence (UTC):

```text
08:07:44  Task added to queue       task_id=54
08:07:48  Task status -> stopped    task_id=54
08:08:03  Task status -> stopped    task_id=53
08:08:03  Stopped running task      task_id=53
08:08:03  Task started             task_id=54
```

The original cleanup then removed the project despite the failed task deletion. The late task
produced foreign-key errors, and the following graceful-stop test timed out waiting for output.
The late execution and cleanup failure are directly observed; the following test's timeout is
recorded as an accompanying failure, not a separately established root cause.

## Source boundary and test-suite handling

At the tested revision, `TaskPool.stopTaskRunner` sets `stopped` for a force stop without
dequeueing a waiting task. `TaskRunner.run` skips execution for `stopping`, but not `stopped`.
The task DELETE handler rejects records still present in the pool.

The suite must cancel all owned tasks before waiting for or deleting any of them. It uses
normal stop for queued/preparing tasks so they enter `stopping`, and force stop for running
tasks. Once capacity is released, a cancelled queued task can finalize without running its
playbook. This is a cleanup workaround, not a fix to Semaphore's force-stop contract.

`TaskCleanupTest` checks the workaround against running and queued tasks together. Framework
tests separately cover cancellation order, terminal states, bounded deletion retries, and
preservation of the original test failure.
