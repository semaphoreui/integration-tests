package io.bookwright.steps.semaphore.tasks;

import com.google.inject.Inject;
import io.bookwright.api.UnexpectedResponseException;
import io.bookwright.api.model.semaphore.Task;
import io.bookwright.api.model.semaphore.TaskHistoryPage;
import io.bookwright.api.model.semaphore.TaskHistoryQuery;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;
import retrofit2.Call;

public class TaskHistorySteps {
  private final SemaphoreTasksApi api;

  @Inject
  public TaskHistorySteps(SemaphoreTasksApi api) {
    this.api = api;
  }

  @Step("Read task history page in project {projectId}")
  public TaskHistoryPage projectPage(long projectId, TaskHistoryQuery query) {
    return read(api.getTaskHistory(projectId, query.count(), query.limit(), query.before()));
  }

  @Step("Read task history page for template {templateId} in project {projectId}")
  public TaskHistoryPage templatePage(long projectId, long templateId, TaskHistoryQuery query) {
    return read(
        api.getTemplateTaskHistory(
            projectId, templateId, query.count(), query.limit(), query.before()));
  }

  private TaskHistoryPage read(Call<List<Task>> call) {
    var response = Calls.expectStatus(call, 200);
    String hasNext = response.headers().get("X-Has-Next");
    if (response.body() == null || !("true".equals(hasNext) || "false".equals(hasNext))) {
      throw new UnexpectedResponseException(
          "Expected task history array and X-Has-Next: true/false for "
              + call.request().url()
              + "; body present="
              + (response.body() != null)
              + ", X-Has-Next="
              + hasNext);
    }
    return new TaskHistoryPage(response.body(), Boolean.parseBoolean(hasNext));
  }
}
