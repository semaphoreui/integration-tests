package io.bookwright.steps.semaphore.tasks;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskOutput;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import io.qameta.allure.Step;
import java.util.List;

public class TaskSteps {

  private final SemaphoreTasksApi api;
  private final TeardownStorage teardown;

  @Inject
  public TaskSteps(SemaphoreTasksApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Start Semaphore task from template {templateId}")
  public Task startTask(long projectId, long templateId) {
    Task task =
        Calls.body(api.startTask(projectId, new TaskRequest(templateId)), 201, "started task");
    teardown.push(
        "Delete Semaphore task " + task.id(),
        () -> Calls.expectStatus(api.deleteTask(projectId, task.id()), 204));
    return task;
  }

  @Step("Wait for Semaphore task {taskId} to succeed")
  public Task waitUntilTaskSucceeds(long projectId, long taskId) {
    return Waits.awaitSlow("Semaphore task %d reaches terminal status".formatted(taskId))
        .until(
            () -> Calls.body(api.getTask(projectId, taskId), 200, "task status"),
            task -> isSuccessfulOrThrow(projectId, task));
  }

  private boolean isSuccessfulOrThrow(long projectId, Task task) {
    if ("error".equals(task.status()) || "stopped".equals(task.status())) {
      String diagnostic =
          Calls.body(api.getTaskOutput(projectId, task.id()), 200, "failed task output").stream()
              .map(TaskOutput::output)
              .reduce((left, right) -> left + System.lineSeparator() + right)
              .orElse("<empty output>");
      throw new IllegalStateException(
          "Task %d finished with status %s. Output:%n%s"
              .formatted(task.id(), task.status(), diagnostic));
    }
    return "success".equals(task.status());
  }

  @Step("Get output of Semaphore task {taskId}")
  public List<TaskOutput> getTaskOutput(long projectId, long taskId) {
    return Calls.body(api.getTaskOutput(projectId, taskId), 200, "task output");
  }
}
