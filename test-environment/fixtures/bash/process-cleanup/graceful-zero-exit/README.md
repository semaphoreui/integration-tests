# Graceful stop with zero exit

This fixture verifies that a task remains cancelled when its main process handles
`SIGTERM` and exits with status `0`.

Once `SIGTERM` is handled gracefully, both situations can produce exit code `0`:

```text
normal completion → exit 0
cancellation → handle SIGTERM → exit 0
```

## Flow

1. Start a Bash template that runs `main.sh`.
2. Wait until the main script is ready.
3. Request a regular stop with `force=false`.
4. The script handles `SIGTERM` and exits with status `0`.
5. The task must reach `stopped`; the clean process exit must not be interpreted
   as normal task completion.

This scenario deliberately makes cancellation look like normal completion by
exiting with status `0` after `SIGTERM`. Semaphore must still finalize the task
as `stopped`.
