package io.bookwright.teardown;

import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskStopRequest;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import java.time.Duration;

/** Cleanup of test-owned tasks, whether started directly, by schedules, or by webhooks. */
public final class SemaphoreTaskCleanup {

  private SemaphoreTaskCleanup() {}

  public static void register(
      SemaphoreTasksApi api, TeardownStorage teardown, long projectId, long taskId) {
    teardown.beforeCleanup(
        "Stop unfinished Semaphore task " + taskId,
        () -> {
          var task = getTask(api, projectId, taskId);
          if (!isTerminal(task)) {
            // A queued force-stop can mark the task stopped without dequeuing it. Normal stop
            // leaves it in 'stopping', so the executor skips its playbook when admitted.
            boolean force = "running".equals(task.status());
            Calls.expectStatus(api.stopTask(projectId, taskId, new TaskStopRequest(force)), 204);
          }
        });
    teardown.push("Delete Semaphore task " + taskId, () -> delete(api, projectId, taskId));
  }

  private static void delete(SemaphoreTasksApi api, long projectId, long taskId) {
    // All stop requests run before any wait/delete, so queued tasks cannot block their runner.
    // Accept natural completion racing with cancellation as well as an explicit stopped status.
    Waits.awaitSlow(
            "Semaphore task %d in project %d finishes before cleanup".formatted(taskId, projectId))
        .pollDelay(Duration.ZERO)
        .until(() -> getTask(api, projectId, taskId), SemaphoreTaskCleanup::isTerminal);

    // Final status can be persisted before pool removal. Retry only the empty DELETE/400
    // returned at that boundary, and keep the wait bounded; other HTTP errors fail immediately.
    Waits.await(
            "terminal Semaphore task %d in project %d can be deleted".formatted(taskId, projectId))
        .pollDelay(Duration.ZERO)
        .until(
            () -> {
              var response = Calls.response(api.deleteTask(projectId, taskId));
              if (response.code() == 400
                  && (response.errorBody() == null || response.errorBody().contentLength() == 0)) {
                if (response.errorBody() != null) {
                  response.errorBody().close();
                }
                return false;
              }
              Calls.expectStatus(response, 204);
              return true;
            });
  }

  private static Task getTask(SemaphoreTasksApi api, long projectId, long taskId) {
    return Calls.body(api.getTask(projectId, taskId), 200, "task during cleanup");
  }

  private static boolean isTerminal(Task task) {
    return "success".equals(task.status())
        || "error".equals(task.status())
        || "stopped".equals(task.status());
  }
}
