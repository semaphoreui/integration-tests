# One-off runner does not exit after a completed task

## Summary

Semaphore UI `v2.19.8` successfully launches a dynamic runner through the configured webhook. The runner receives and completes exactly one task, reports the terminal `success` status, and the server sends the `finish` webhook. Despite `SEMAPHORE_RUNNER_ONE_OFF=true`, the runner process remains alive.

The behavior is reproduced by the isolated `feature-dynamic-runner` profile. This profile is intentionally excluded from the normal CI matrix until the product defect is fixed.

## Environment

- Semaphore server: `semaphoreui/semaphore:v2.19.8`
- Semaphore runner: `semaphoreui/runner:v2.19.8`
- release source commit: `3449a04f3bfa2522ec7fd60803f71b578c39f6b4`
- database: SQLite
- runner executor: local
- runner mode: webhook-launched, one-off
- reproduced: 2026-08-19

## Preconditions

The launcher registers one global runner with a start/finish webhook and makes it the default runner. On a `start` webhook it launches:

```text
/usr/local/bin/semaphore runner start --no-config
```

The process inherits `SEMAPHORE_RUNNER_ONE_OFF=true`. The launcher only observes the process; it does not terminate it or alter Semaphore behavior.

## Reproduction

```bash
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

The automated scenario creates a project, access key, local repository, inventory and template, then runs a real Ansible task. After task completion it waits for the complete launcher lifecycle:

```text
webhook_start → runner_started → webhook_finish → runner_exited
```

## Expected result

- the task finishes with `success`;
- the server calls both webhook actions once;
- the one-off runner exits with code `0` after reporting the terminal task state.

## Actual result

- the task finishes with `success`;
- `webhook_start`, `runner_started` and `webhook_finish` are observed exactly once;
- `runner_exited` is never observed;
- the runner process remains alive after the task was removed from its running list.

Observed launcher state:

```json
{
  "events": [
    {"type": "webhook_start", "task_id": 1, "runner_id": 1, "exit_code": null},
    {"type": "runner_started", "task_id": 1, "runner_id": 1, "exit_code": null},
    {"type": "webhook_finish", "task_id": 1, "runner_id": 1, "exit_code": null}
  ]
}
```

Process evidence after the terminal task status:

```text
PID  COMMAND
1    python3 /dynamic-runner-launcher.py
76   /usr/local/bin/semaphore runner start --no-config
```

The container reports `SEMAPHORE_RUNNER_ONE_OFF=true`.

## Probable cause

The defect is present in the `v2.19.8` implementation of `services/runners/job_pool.go` and is still visible on upstream `develop` commit `ae12f3acac626f78673b95cc57acd62ed873b089`.

The polling loop calls `sendProgress()` before evaluating the one-off exit condition:

```go
ok := p.sendProgress()

if util.Config.Runner.OneOff && ok && p.runningJobsCount() > 0 && !p.hasRunningJobs() {
    os.Exit(0)
}
```

When the terminal progress is accepted, `sendProgress()` removes the finished job:

```go
if jp.Status.IsFinished() {
    p.deleteRunningJob(jp.ID)
}
```

Consequently, `p.runningJobsCount() > 0` is false when the exit condition is evaluated. The condition that should terminate the one-off process cannot become true on the successful terminal-report path.

## Suggested correction

Preserve whether the runner had a job before `sendProgress()` removes terminal jobs, or track explicitly that this one-off process accepted a task. Exit only after its terminal progress was accepted and there are no remaining running jobs. A regression test should cover `success`, `error`, `stopped` and a transient progress-report failure.

## Impact

Webhook-based autoscaling cannot rely on the runner process to terminate after one task. Launchers may leak processes or containers and later launches can contend with an old runner. An infrastructure workaround that forcibly kills the process would hide the product contract failure, so the test profile deliberately leaves the process untouched.

## Existing upstream issues checked

- [#1546 Remote runner hangs](https://github.com/semaphoreui/semaphore/issues/1546) concerns tasks hanging on a persistent runner and is not the same terminal one-off lifecycle failure.
- [#3897 Runner does not return the response to the server](https://github.com/semaphoreui/semaphore/issues/3897) concerns TLS feedback and tasks stuck in `starting`; our task completes successfully and only the runner process fails to exit.

No issue matching this exact reproduction was found during the 2026-08-19 check.
