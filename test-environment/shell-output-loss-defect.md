# Defect: a short Bash task loses `stdout` or `stderr`

## Status

- severity: High;
- affected version: Semaphore UI `v2.19.12` (`012ed06d`);
- configuration: Community, Docker Compose, SQLite, local task execution;
- reproduced: 2026-09-04 in Linux CI;
- GitHub Actions run: [33866188839](https://github.com/semaphoreui/integration-tests/actions/runs/33866188839);
- fixed in `develop`, but the fix is not yet included in the current stable release `v2.19.12`.

## Description

After a short Bash command completes successfully, Semaphore may persist only one of the two
process streams. The task status is already `success`, but the API output is missing either `stdout` or
`stderr`. The lost stream does not appear on subsequent reads.

This makes a successful status insufficient proof that log collection has completed. A user
may lose diagnostic messages, values that a script deliberately writes to a specific
stream, and part of the execution audit trail.

## Steps to reproduce

1. Bring up the manual profile:

   ```bash
   test-environment/profile up feature-shell-output
   ```

2. Run the strict reproducer:

   ```bash
   test-environment/profile test feature-shell-output \
     --tests io.bookwright.tests.semaphore.ShellOutputTest
   ```

3. The test creates a local Git repository, an inventory, and a template that executes:

   ```bash
   printf 'semaphore-shell-stdout-marker'
   printf 'semaphore-shell-stderr-marker' >&2
   ```

4. Wait for `success` and read the task output via the API.

The second scenario repeats the check with a background child (`sleep 60 &`) to additionally
verify the completion of a command whose child process inherited the output pipes.

## Expected result

The task quickly transitions to `success`; the persisted output contains both markers:

```text
semaphore-shell-stdout-marker
semaphore-shell-stderr-marker
```

## Actual result

In one CI scenario only `stdout` remained:

```text
... installing static inventory
semaphore-shell-stdout-marker
```

In another only `stderr` remained:

```text
... installing static inventory
semaphore-shell-stderr-marker
```

Both tasks nevertheless finished with the status `success`. The reproduction concerns the API/process
lifecycle, so a screenshot adds no diagnostic value; the evidence is in the
JUnit XML, Allure results, and Compose logs of the referenced CI run.

## Root cause and upstream fixes

In `v2.19.12`, `TaskRunner.LogCmd` increments the `WaitGroup` inside the reader goroutine. `WaitLog` may
observe a zero counter and proceed with finalization before both streams have been read. In addition,
`ShellApp.Run` calls `cmd.Wait()` before `StdoutPipe`/`StderrPipe` are guaranteed to be drained, even though
`Wait` releases the resources associated with the `Cmd`.

Upstream fixed both parts after the `v2.19.12` release:

- [`5c2d6e34` — preserve output from short shell commands](https://github.com/semaphoreui/semaphore/commit/5c2d6e34bed587b3cbea029c0799c5577e781800): registers the readers before starting the goroutine and waits for the pipes to drain before `cmd.Wait()`;
- [`4976e916` — prevent hangs from inherited output pipes](https://github.com/semaphoreui/semaphore/commit/4976e91699886157184d04fa2069e912e40156b9): introduces a command-scoped finalizer, `io.Pipe`, and a bounded `WaitDelay` for descendants holding the output pipes.

## Automated control

`ShellOutputTest` keeps strict checks of both markers and of the completion time of the background
scenario. Until the fix lands in stable, it runs only under the
`feature-shell-output` profile, is not masked by retries, and is not part of the green PR/nightly gate. In the manual
`Configuration matrix` it can be enabled with the input parameter
`include_shell_output_investigation=true` to obtain the standard CI artifacts.

The remaining profile suites execute JUnit classes sequentially and wait for their own evidentiary
marker. This reduces pressure on the defective concurrent output collector of `v2.19.12` without adding
random sleeps or whole-test retries. Real concurrency of Semaphore tasks is still
verified inside `ProjectConcurrencyApiTest`. The workaround is not used in `ShellOutputTest`,
so the product regression signal is not masked.
