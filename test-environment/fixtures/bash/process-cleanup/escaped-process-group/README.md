# Escaped process group

This fixture documents that a descendant can escape task cleanup by joining a
new session and process group.

## Components

1. `main.sh` starts `child.sh` through `setsid`, waits for its PID, prints a
   readiness marker, and exits successfully.
2. `child.sh` ignores `SIGTERM` and `SIGHUP`, records its PID, and becomes a
   long-running `sleep` process.
3. `verify.sh` confirms that the escaped process survived, kills it, and reports
   `semaphore-escaped-process-alive`.

The process tree and groups are:

```text
main.sh                    [task process group]
└── child.sh → sleep 120   [new process group created by setsid]
```

`child.sh` uses `exec`, so `sleep` replaces it and keeps the same PID.

## Flow

1. The test starts the template that runs `main.sh`.
2. `setsid` moves the child into a new process group.
3. The main task exits and Semaphore cleans its process group.
4. The escaped process remains alive because it belongs to another group.
5. The verifier reports `semaphore-escaped-process-alive` and kills it.
