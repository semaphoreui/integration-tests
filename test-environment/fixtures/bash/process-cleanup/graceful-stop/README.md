# Graceful stop cleanup

This fixture verifies that a graceful API stop sends `SIGTERM` to the main task
process and its child.

## Components

1. `main.sh` starts `child.sh`, prints a readiness marker, and waits for the
   child process to exit. Its TERM handler prints a marker before exiting.
2. `child.sh` records its process IDs. Its TERM handler records and prints
   a marker before exiting.
3. `verify.sh` confirms that no recorded process survived and kills any
   unexpected survivor.

The running process tree is:

```text
main.sh
└── child.sh
    └── sleep 120
```

## Flow

1. The test starts the template that runs `main.sh`.
2. It waits for `semaphore-process-cleanup-term-ready`.
3. It calls the stop API with `force=false`.
4. The main and child processes handle `SIGTERM` and exit.
5. The task must reach `stopped` before the 15-second grace period expires.
6. The test confirms both signal markers, and the verifier reports
   `semaphore-graceful-stop-descendants-gone`.

The main and verifier tasks run in the same local Semaphore container. The main
task stores the process IDs in
`/tmp/bookwright-process-cleanup/<task-id>/children.pids`, and the Java test
passes that task ID to the verifier task.
