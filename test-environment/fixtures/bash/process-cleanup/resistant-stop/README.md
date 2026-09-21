# Resistant stop cleanup

This fixture verifies that Semaphore kills a process group that does not exit
after receiving `SIGTERM`.

## Components

1. `main.sh` starts `child.sh`, prints a readiness marker, and waits for the
   child process. Its TERM handler prints a marker without exiting.
2. `child.sh` runs a waiting `sleep` process. Its TERM handler prints a marker
   without exiting.
3. `verify.sh` confirms that the recorded processes are gone and kills any
   unexpected survivor.

The running process tree is:

```text
main.sh
└── child.sh
    └── sleep 120
```

## Flow

1. The test starts the template that runs `main.sh`.
2. It waits for `semaphore-resistant-stop-ready`.
3. It calls the stop API with `force=false`.
4. The main and child processes print their `SIGTERM` markers but remain running.
5. Semaphore waits for the 15-second grace period, then sends `SIGKILL`.
6. The task reaches `stopped` and the verifier reports
   `semaphore-resistant-stop-processes-gone`.

The main and verifier tasks run in the same local Semaphore container. The main
task stores process IDs and signal markers under
`/tmp/bookwright-process-cleanup/<task-id>`, and the Java test passes that task
ID to the verifier task.
