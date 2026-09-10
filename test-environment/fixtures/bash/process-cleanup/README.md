# Process cleanup test rig

This fixture verifies that Semaphore removes a task's descendants after the main
command exits normally.

## Components

1. `main.sh`
   - It is the original Semaphore task script.
   - Creates `/tmp/bookwright-process-cleanup/<task-id>`.
   - Starts `resistant-process.sh` as a child process in the background.
   - Waits until the child process records its PID.
   - Prints stdout and stderr markers, then exits successfully.

2. `resistant-process.sh`
   - Runs as a descendant of the original task.
   - Ignores `SIGTERM` and `SIGHUP`.
   - Records its PID in the task's state directory.
   - Replaces itself with `sleep 120`, keeping the original task's stdout and
     stderr open.

3. `verify.sh`
   - Runs later as a second Semaphore task.
   - Receives the completed task ID as an argument.
   - Reads the recorded PID and checks whether that process is still running.
   - Kills it if it unexpectedly survived, so a failing test does not leak a
     process.
   - Prints whether the process was `gone`, `alive`, or `missing`.

## Test flow

`LocalProcessCleanupTest` creates two Semaphore templates: one for the original
task and one for the verifier.

1. The test starts the original task.
2. The original command exits while its resistant descendant is still running.
3. Semaphore should kill the remaining process group.
4. The test starts the verifier task with the original task ID.
5. The verifier must print `semaphore-process-cleanup-child-gone`.

Both tasks run in the same local Semaphore container, so they can exchange the
PID through `/tmp`. The task ID gives them a shared, unique directory name.
