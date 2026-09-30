package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.UnexpectedResponseException;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.steps.semaphore.tasks.TaskSteps;
import io.bookwright.steps.semaphore.users.UserSteps;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectIsolationContractTest {

  @ParameterizedTest
  @ValueSource(strings = {"details", "structured", "raw", "stop", "launch"})
  void hiddenOperationsUseTheRequestedProjectAndResource(String operation) throws Exception {
    try (var server = new MockWebServer()) {
      var session = SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
      var steps = new TaskSteps(session.tasks(), new TeardownStorage());
      server.enqueue(new MockResponse().setResponseCode(404));

      switch (operation) {
        case "details" -> steps.verifyTaskHidden(session, 7, 11, 404);
        case "structured" -> steps.verifyOutputHidden(session, 7, 11, 404);
        case "raw" -> steps.verifyRawOutputHidden(session, 7, 11, 404);
        case "stop" -> steps.verifyStopHidden(session, 7, 11, 404);
        case "launch" -> steps.verifyLaunchHidden(session, 7, 11);
        default -> throw new AssertionError("Unknown operation " + operation);
      }

      var request = server.takeRequest(1, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      String suffix =
          switch (operation) {
            case "structured" -> "/11/output";
            case "raw" -> "/11/raw_output";
            case "stop" -> "/11/stop";
            case "launch" -> "";
            default -> "/11";
          };
      assertThat(request.getPath()).isEqualTo("/project/7/tasks" + suffix);
      assertThat(request.getMethod())
          .isEqualTo(operation.equals("stop") || operation.equals("launch") ? "POST" : "GET");
      if (operation.equals("launch")) {
        assertThat(request.getBody().readUtf8()).contains("\"template_id\":11");
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 400, 401, 403, 500})
  void aDifferentStatusDoesNotPassAsProjectIsolation(int status) throws Exception {
    try (var server = new MockWebServer()) {
      var session = SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
      var steps = new TaskSteps(session.tasks(), new TeardownStorage());
      server.enqueue(new MockResponse().setResponseCode(status).setBody("{}"));

      assertThatThrownBy(() -> steps.verifyTaskHidden(session, 7, 11, 404))
          .isInstanceOf(UnexpectedResponseException.class)
          .hasMessageContaining("Expected status [404]");
    }
  }

  @Test
  void unexpectedLaunchRegistersCleanupBeforeReportingTheFailure() throws Exception {
    try (var server = new MockWebServer()) {
      var session = SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
      var storage = new TeardownStorage();
      var steps = new TaskSteps(session.tasks(), storage);
      server.enqueue(json(201, "{\"id\":11,\"project_id\":9,\"status\":\"waiting\"}"));
      server.enqueue(json(200, "{\"id\":11,\"project_id\":9,\"status\":\"success\"}"));
      server.enqueue(new MockResponse().setResponseCode(204));

      assertThatThrownBy(() -> steps.verifyLaunchHidden(session, 7, 13))
          .isInstanceOf(UnexpectedResponseException.class);
      TeardownExtension.execute(storage, true, true);

      assertThat(server.takeRequest().getPath()).isEqualTo("/project/7/tasks");
      assertThat(server.takeRequest().getPath()).isEqualTo("/project/9/tasks/11");
      var deleted = server.takeRequest();
      assertThat(deleted.getMethod()).isEqualTo("DELETE");
      assertThat(deleted.getPath()).isEqualTo("/project/9/tasks/11");
      assertThat(storage.pollLast()).isNull();
    }
  }

  @Test
  void finiteTaskFinishesBeforeItsOriginalDeleteAction() throws Exception {
    try (var server = new MockWebServer()) {
      var session = SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
      var storage = new TeardownStorage();
      var steps = new TaskSteps(session.tasks(), storage);
      server.enqueue(json(201, "{\"id\":11,\"project_id\":7,\"status\":\"waiting\"}"));
      server.enqueue(json(200, "{\"id\":11,\"project_id\":7,\"status\":\"running\"}"));
      server.enqueue(json(200, "{\"id\":11,\"project_id\":7,\"status\":\"success\"}"));
      server.enqueue(new MockResponse().setResponseCode(204));

      var task = steps.startTask(7, 13);
      steps.awaitCompletionAfterTest(7, task.id());
      TeardownExtension.execute(storage, true, true);

      assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
      assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
      assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
      assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {204, 404, 403})
  void membershipCleanupAcceptsAbsenceButNotPermissionErrors(int cleanupStatus) throws Exception {
    try (var server = new MockWebServer()) {
      var session = SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
      var storage = new TeardownStorage();
      var users = new UserSteps(session.users(), storage);
      server.enqueue(new MockResponse().setResponseCode(204));
      server.enqueue(new MockResponse().setResponseCode(204));
      server.enqueue(new MockResponse().setResponseCode(cleanupStatus));
      users.addToProject(7, 11, "owner");
      users.removeFromProject(7, 11);

      if (cleanupStatus == 403) {
        assertThatThrownBy(() -> TeardownExtension.execute(storage, true, false))
            .isInstanceOf(TeardownException.class);
      } else {
        assertThatCode(() -> TeardownExtension.execute(storage, true, false))
            .doesNotThrowAnyException();
      }
    }
  }

  private MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
