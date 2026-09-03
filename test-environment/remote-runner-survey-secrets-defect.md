# Defect: remote runner loses secret survey variables

## Summary

Semaphore `v2.19.8` accepts a task launch containing a survey variable of type
`secret`, but a persistent remote runner receives no value for that variable.
The same template and launch payload succeed with local execution.

## Environment

- observed: 2026-08-21;
- Semaphore server and runner: `v2.19.8`;
- profile: `prod-postgres-runner`;
- database: PostgreSQL 14.3;
- executor: persistent remote runner, `local` executor.

## Reproduction

1. Create an Ansible template with a required survey variable of type `secret`.
2. Launch it through `POST /api/project/{project_id}/tasks`, supplying the value
   in the request `secret` object.
3. Let the registered persistent runner execute a playbook that consumes the
   variable without printing its value.

The task reaches terminal `error`. Ansible reports that the survey variable is
undefined. The automated reproducer is
`SurveyAndTaskOverridesApiTest.remoteRunnerLosesSurveySecret`; it also checks
that the plaintext value is absent from task output.

## Expected

The remote runner receives the secret through its authenticated dispatch
channel, the task succeeds, and the value remains masked in API responses and
task output.

## Actual

The server clears the transient task secret before remote dispatch. Local
execution retains a separate in-memory copy, while the remote job receives an
empty `task.secret`. The task therefore fails when the playbook references the
required variable.

## Impact

Survey secrets cannot be relied on in the production-like deployment model
where execution is delegated to a persistent runner. A template can work in a
local test installation and fail after the same configuration is moved to a
remote-runner installation.

## Upstream status

The root cause and HA-safe persistence design are implemented by upstream PR
[#4086](https://github.com/semaphoreui/semaphore/pull/4086), commit
[`081425d2`](https://github.com/semaphoreui/semaphore/commit/081425d2bc20d5fe41def47ec6a429e2e43cf715).
The change stores task-bound survey secrets encrypted as access keys and fills
them during runner dispatch. It is present in `v2.20.0-alpha1`, but not in the
tested `v2.19.8` release line.

## Regression criterion

After upgrading to a release containing #4086, replace the known-defect canary
with the positive survey execution assertion on `prod-postgres-runner`. Keep
the existing redaction checks for structured/raw output and test artifacts.
