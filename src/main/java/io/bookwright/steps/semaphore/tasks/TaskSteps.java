package io.bookwright.steps.semaphore.tasks;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskOutput;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TaskStopRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.bookwright.util.Waits;
import io.qameta.allure.Step;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.awaitility.core.ConditionTimeoutException;

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
    return startTask(projectId, new TaskRequest(templateId));
  }

  @Step("Start Semaphore task from template {request.templateId} with launch parameters")
  public Task startTask(long projectId, TaskRequest request) {
    Task task = Calls.body(api.startTask(projectId, request), 201, "started task");
    teardown.push(
        "Delete Semaphore task " + task.id(),
        () -> Calls.expectStatus(api.deleteTask(projectId, task.id()), 204));
    return task;
  }

  @Step("Find successful persisted task for Semaphore template {templateId}")
  public Task requireSuccessfulForTemplate(long projectId, long templateId) {
    List<Task> tasks = Calls.body(api.getTasks(projectId), 200, "tasks");
    return tasks.stream()
        .filter(task -> task.templateId() == templateId && "success".equals(task.status()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No successful persisted task was found for template %d in project %d. Task states: %s"
                        .formatted(
                            templateId,
                            projectId,
                            tasks.stream()
                                .map(task -> "%d:%s".formatted(task.id(), task.status()))
                                .toList())));
  }

  @Step("Start Semaphore task from template {templateId} and wait for success")
  public Task startAndWait(long projectId, long templateId) {
    return waitUntilTaskSucceeds(projectId, startTask(projectId, templateId).id());
  }

  @Step(
      "Start Semaphore task from template {request.templateId} with launch parameters and wait for success")
  public Task startAndWait(long projectId, TaskRequest request) {
    return waitUntilTaskSucceeds(projectId, startTask(projectId, request).id());
  }

  @Step("Start Semaphore task as isolated user from template {templateId} and wait for success")
  public Task startAndWait(SemaphoreSessionApis session, long projectId, long templateId) {
    Task task =
        Calls.body(
            session.tasks().startTask(projectId, new TaskRequest(templateId)), 201, "started task");
    teardown.push(
        "Delete Semaphore task " + task.id(),
        () -> Calls.expectStatus(api.deleteTask(projectId, task.id()), 204));
    return waitUntilTaskSucceeds(projectId, task.id());
  }

  @Step("Start Semaphore task from template {templateId} and wait for failure")
  public Task startAndWaitForFailure(long projectId, long templateId) {
    return waitUntilTaskFails(projectId, startTask(projectId, templateId).id());
  }

  @Step(
      "Start Semaphore task from template {request.templateId} with launch parameters and wait for failure")
  public Task startAndWaitForFailure(long projectId, TaskRequest request) {
    return waitUntilTaskFails(projectId, startTask(projectId, request).id());
  }

  @Step("Wait for Semaphore task {taskId} to succeed")
  public Task waitUntilTaskSucceeds(long projectId, long taskId) {
    return Waits.awaitSlow("Semaphore task %d reaches terminal status".formatted(taskId))
        .until(
            () -> Calls.body(api.getTask(projectId, taskId), 200, "task status"),
            task -> isSuccessfulOrThrow(projectId, task));
  }

  @Step("Wait for Semaphore task {taskId} to fail")
  public Task waitUntilTaskFails(long projectId, long taskId) {
    return Waits.awaitSlow("Semaphore task %d fails".formatted(taskId))
        .until(
            () -> Calls.body(api.getTask(projectId, taskId), 200, "task status"),
            task -> isFailedOrThrow(task));
  }

  @Step("Wait for schedule {scheduleId} to create and complete a Semaphore task")
  public Task waitForScheduledTaskToSucceed(long projectId, long scheduleId, long templateId) {
    List<Task> tasks;
    try {
      tasks =
          Waits.awaitSlow("Semaphore schedule %d creates a task".formatted(scheduleId))
              .until(
                  () -> Calls.body(api.getTasks(projectId), 200, "scheduled tasks"),
                  candidates ->
                      candidates.stream()
                          .anyMatch(candidate -> belongsTo(candidate, scheduleId, templateId)));
    } catch (ConditionTimeoutException timeout) {
      List<Task> observed =
          Calls.body(api.getTasks(projectId), 200, "scheduled tasks after timeout");
      throw new IllegalStateException(
          "Schedule %d did not create a task for template %d in project %d. Observed tasks: %s"
              .formatted(
                  scheduleId,
                  templateId,
                  projectId,
                  observed.stream()
                      .map(
                          task ->
                              "%d:schedule=%s:template=%d:status=%s"
                                  .formatted(
                                      task.id(),
                                      task.scheduleId(),
                                      task.templateId(),
                                      task.status()))
                      .toList()),
          timeout);
    }
    Task task =
        tasks.stream()
            .filter(candidate -> belongsTo(candidate, scheduleId, templateId))
            .findFirst()
            .orElseThrow();
    teardown.push(
        "Delete scheduled Semaphore task " + task.id(),
        () -> Calls.expectStatus(api.deleteTask(projectId, task.id()), 204));
    return waitUntilTaskSucceeds(projectId, task.id());
  }

  private boolean belongsTo(Task task, long scheduleId, long templateId) {
    return task.scheduleId() != null
        && task.scheduleId() == scheduleId
        && task.templateId() == templateId;
  }

  @Step("Wait for Semaphore task {taskId} output marker")
  public void waitUntilTaskOutputContains(long projectId, long taskId, String marker) {
    Waits.awaitSlow("Semaphore task %d emits expected output".formatted(taskId))
        .until(() -> getTaskOutputText(projectId, taskId), output -> output.contains(marker));
  }

  @Step("Stop Semaphore task {taskId} and wait for terminal status")
  public Task stopAndWait(long projectId, long taskId, boolean force) {
    Calls.expectStatus(api.stopTask(projectId, taskId, new TaskStopRequest(force)), 204);
    return waitUntilTaskStops(projectId, taskId);
  }

  @Step("Wait for Semaphore task {taskId} to stop")
  public Task waitUntilTaskStops(long projectId, long taskId) {
    return Waits.awaitSlow("Semaphore task %d stops".formatted(taskId))
        .until(
            () -> Calls.body(api.getTask(projectId, taskId), 200, "task status"),
            task -> isStoppedOrThrow(task));
  }

  @Step("Get Semaphore task {taskId}")
  public Task getTask(long projectId, long taskId) {
    return Calls.body(api.getTask(projectId, taskId), 200, "task");
  }

  @Step("Get tasks in Semaphore project {projectId}")
  public List<Task> getTasks(long projectId) {
    return Calls.body(api.getTasks(projectId), 200, "tasks");
  }

  @Step("Get tasks for Semaphore template {templateId}")
  public List<Task> getTasksForTemplate(long projectId, long templateId) {
    return Calls.body(api.getTemplateTasks(projectId, templateId), 200, "template tasks");
  }

  @Step("Find persisted Semaphore task {taskId} in project {projectId}")
  public Task requirePersistedTask(long projectId, long taskId) {
    List<Task> tasks = getTasks(projectId);
    return tasks.stream()
        .filter(task -> task.id() == taskId)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Task %d was not found in project %d. Available task IDs: %s"
                        .formatted(taskId, projectId, tasks.stream().map(Task::id).toList())));
  }

  @Step("Wait for Semaphore task {taskId} to reach status {status}")
  public Task waitUntilStatus(long projectId, long taskId, String status) {
    return Waits.awaitSlow("Semaphore task %d reaches status %s".formatted(taskId, status))
        .until(() -> getTask(projectId, taskId), task -> status.equals(task.status()));
  }

  @Step("Verify Semaphore task {taskId} remains in status {status}")
  public Task verifyRemainsInStatus(long projectId, long taskId, String status) {
    Waits.await("Semaphore task %d remains in status %s".formatted(taskId, status))
        .during(Duration.ofSeconds(3))
        .until(() -> getTask(projectId, taskId), task -> status.equals(task.status()));
    return getTask(projectId, taskId);
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

  private boolean isFailedOrThrow(Task task) {
    if ("success".equals(task.status()) || "stopped".equals(task.status())) {
      throw new IllegalStateException(
          "Task %d finished with status %s but error was expected"
              .formatted(task.id(), task.status()));
    }
    return "error".equals(task.status());
  }

  private boolean isStoppedOrThrow(Task task) {
    if ("success".equals(task.status()) || "error".equals(task.status())) {
      throw new IllegalStateException(
          "Task %d finished with status %s but stopped was expected"
              .formatted(task.id(), task.status()));
    }
    return "stopped".equals(task.status());
  }

  @Step("Get output of Semaphore task {taskId}")
  public List<TaskOutput> getTaskOutput(long projectId, long taskId) {
    return Calls.body(api.getTaskOutput(projectId, taskId), 200, "task output");
  }

  @Step("Get structured output text of Semaphore task {taskId}")
  public String getTaskOutputText(long projectId, long taskId) {
    return getTaskOutput(projectId, taskId).stream()
        .map(TaskOutput::output)
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElse("");
  }

  @Step("Get raw output of Semaphore task {taskId}")
  public String getTaskRawOutput(long projectId, long taskId) {
    var response = Calls.expectStatus(api.getTaskRawOutput(projectId, taskId), 200);
    try (var body = response.body()) {
      if (body == null) {
        throw new IllegalStateException("Raw task output response body was empty");
      }
      return body.string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read raw task output", error);
    }
  }
}
