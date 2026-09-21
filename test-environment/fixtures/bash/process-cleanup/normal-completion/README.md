# Normal completion cleanup

This fixture verifies that Semaphore removes a task's descendants after its main
command exits normally.

## Components

1. `main.sh` starts `resistant-process.sh` as a background child, waits for it to
   record its PID, prints output markers, and exits successfully.
2. `resistant-process.sh` ignores `SIGTERM` and `SIGHUP`, then becomes a
   long-running `sleep` process that keeps stdout and stderr open.
3. `verify.sh` checks that the recorded process is gone and kills it if it
   unexpectedly survived.

## Flow

1. The test starts the template that runs `main.sh`.
2. The main command exits while its resistant descendant is still running.
3. Semaphore should kill the remaining process group.
4. The verifier must report `semaphore-resistant-process-gone`.

The main and verifier tasks run in the same local Semaphore container. The main
task stores the PID in
`/tmp/bookwright-process-cleanup/<task-id>/child.pid`, and the Java test passes
that task ID to the verifier task.
