package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.api.UnexpectedResponseException;
import io.bookwright.api.model.semaphore.IntegrationAlias;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.integrations.SemaphoreIntegrationsApi;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.steps.semaphore.integrations.IntegrationSteps;
import io.bookwright.steps.semaphore.tasks.TaskSteps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class SemaphoreTaskCleanupTest {

  private MockWebServer server;
  private Retrofit retrofit;
  private TeardownStorage storage;
  private TaskSteps steps;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    retrofit =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    storage = new TeardownStorage();
    steps = new TaskSteps(retrofit.create(SemaphoreTasksApi.class), storage);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @ParameterizedTest
  @ValueSource(strings = {"waiting", "starting", "running", "stopping"})
  void activeTaskIsStoppedBeforeDeletion(String status) throws InterruptedException {
    startTask();
    server.enqueue(task(status));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(task("stopped"));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    var start = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(start).isNotNull();
    assertThat(start.getMethod()).isEqualTo("POST");
    assertThat(server.takeRequest(1, TimeUnit.SECONDS).getMethod()).isEqualTo("GET");
    var stop = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(stop.getPath()).isEqualTo("/project/7/tasks/11/stop");
    assertThat(stop.getBody().readUtf8())
        .isEqualTo("{\"force\":%s}".formatted("running".equals(status)));
    assertThat(requests()).containsExactly("GET /project/7/tasks/11", "DELETE /project/7/tasks/11");
  }

  @ParameterizedTest
  @ValueSource(strings = {"success", "error", "stopped"})
  void finishedTaskIsDeletedWithoutAStopRequest(String status) throws InterruptedException {
    startTask();
    server.enqueue(task(status));
    server.enqueue(task(status));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests())
        .containsExactly(
            "POST /project/7/tasks",
            "GET /project/7/tasks/11",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  @ParameterizedTest
  @ValueSource(strings = {"success", "error"})
  void naturalCompletionDuringStopIsAccepted(String terminalStatus) throws InterruptedException {
    startTask();
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(task(terminalStatus));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests()).endsWith("GET /project/7/tasks/11", "DELETE /project/7/tasks/11");
  }

  @Test
  void waitsForStopCompletionBeforeDeleting() throws InterruptedException {
    startTask();
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(task("stopping"));
    server.enqueue(task("stopped"));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests())
        .containsExactly(
            "POST /project/7/tasks",
            "GET /project/7/tasks/11",
            "POST /project/7/tasks/11/stop",
            "GET /project/7/tasks/11",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  @Test
  void retriesEmptyDeleteRejectionWhileTerminalTaskLeavesPool() throws InterruptedException {
    startTask();
    server.enqueue(task("stopped"));
    server.enqueue(task("stopped"));
    server.enqueue(new MockResponse().setResponseCode(400));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests())
        .containsExactly(
            "POST /project/7/tasks",
            "GET /project/7/tasks/11",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  @Test
  @Timeout(25)
  void persistentEmptyDeleteRejectionFailsWithTaskDiagnostic() {
    startTask();
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            return "GET".equals(request.getMethod())
                ? task("stopped")
                : new MockResponse().setResponseCode(400);
          }
        });

    assertThatThrownBy(this::cleanup)
        .isInstanceOf(ConditionTimeoutException.class)
        .hasMessageContaining("terminal Semaphore task 11 in project 7 can be deleted");
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 500})
  void unexpectedDeleteErrorsAreNotRetried(int status) throws InterruptedException {
    startTask();
    server.enqueue(task("success"));
    server.enqueue(task("success"));
    server.enqueue(new MockResponse().setResponseCode(status).setBody("unexpected deletion error"));

    assertThatThrownBy(this::cleanup)
        .isInstanceOf(UnexpectedResponseException.class)
        .hasMessageContaining("got " + status)
        .hasMessageContaining("DELETE");

    assertThat(requests())
        .containsExactly(
            "POST /project/7/tasks",
            "GET /project/7/tasks/11",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  @Test
  void stopFailureIsReported() throws InterruptedException {
    startTask();
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThatThrownBy(this::cleanup)
        .isInstanceOf(UnexpectedResponseException.class)
        .hasMessageContaining("got 500");

    assertThat(requests())
        .containsExactly(
            "POST /project/7/tasks", "GET /project/7/tasks/11", "POST /project/7/tasks/11/stop");
  }

  @Test
  void cleanupErrorPreservesPrimaryFailureAndContinuesOtherCleanup() {
    List<String> remainingCleanup = new ArrayList<>();
    storage.push("remaining resource", () -> remainingCleanup.add("done"));
    startTask();
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThatCode(() -> TeardownExtension.execute(storage, true, true)).doesNotThrowAnyException();

    assertThat(remainingCleanup).containsExactly("done");
  }

  @ParameterizedTest
  @ValueSource(strings = {"session", "schedule", "template"})
  void discoveredAndIsolatedSessionTasksRegisterCleanup(String source) throws InterruptedException {
    if (source.equals("session")) {
      server.enqueue(task("waiting").setResponseCode(201));
    } else {
      server.enqueue(new MockResponse().setBody("[" + taskJson("success") + "]"));
    }
    server.enqueue(task("success"));
    switch (source) {
      case "session" -> steps.startAndWait(SemaphoreSessionApis.create(retrofit), 7, 3);
      case "schedule" -> steps.waitForScheduledTaskToSucceed(7, 5, 3);
      case "template" -> steps.waitForTemplateTaskToSucceed(7, 3, "cleanup-test");
      default -> throw new IllegalArgumentException(source);
    }
    server.enqueue(task("success"));
    server.enqueue(task("success"));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests()).endsWith("GET /project/7/tasks/11", "DELETE /project/7/tasks/11");
  }

  @Test
  void webhookDispatchRegistersTheSameTaskCleanup() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setResponseCode(204)
            .addHeader("X-Semaphore-Task-ID", "11")
            .addHeader("X-Semaphore-Template-ID", "3")
            .addHeader("X-Semaphore-Project-ID", "7")
            .addHeader("X-Semaphore-Integration-ID", "2"));
    new IntegrationSteps(
            retrofit.create(SemaphoreIntegrationsApi.class),
            retrofit.create(SemaphoreTasksApi.class),
            storage)
        .dispatch(new IntegrationAlias(1, server.url("/webhook").toString()), Map.of(), Map.of());
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(task("stopped"));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests())
        .containsExactly(
            "POST /webhook",
            "GET /project/7/tasks/11",
            "POST /project/7/tasks/11/stop",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  @Test
  void ignoredWebhookDoesNotRegisterTaskCleanup() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(204));

    new IntegrationSteps(
            retrofit.create(SemaphoreIntegrationsApi.class),
            retrofit.create(SemaphoreTasksApi.class),
            storage)
        .verifyIgnored(
            7, new IntegrationAlias(1, server.url("/webhook").toString()), Map.of(), Map.of());

    assertThat(storage.pollLast()).isNull();
    assertThat(requests()).containsExactly("POST /webhook");
  }

  @Test
  void unexpectedlyDispatchedWebhookRegistersCleanupBeforeFailing() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(204).addHeader("X-Semaphore-Task-ID", "11"));

    assertThatThrownBy(
            () ->
                new IntegrationSteps(
                        retrofit.create(SemaphoreIntegrationsApi.class),
                        retrofit.create(SemaphoreTasksApi.class),
                        storage)
                    .verifyIgnored(
                        7,
                        new IntegrationAlias(1, server.url("/webhook").toString()),
                        Map.of(),
                        Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unexpectedly launched task 11 for webhook alias 1");
    server.enqueue(task("running"));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(task("stopped"));
    server.enqueue(new MockResponse().setResponseCode(204));

    cleanup();

    assertThat(requests())
        .containsExactly(
            "POST /webhook",
            "GET /project/7/tasks/11",
            "POST /project/7/tasks/11/stop",
            "GET /project/7/tasks/11",
            "DELETE /project/7/tasks/11");
  }

  private void startTask() {
    server.enqueue(task("waiting").setResponseCode(201));
    steps.startTask(7, 3);
  }

  private void cleanup() {
    var action = storage.pollLast();
    assertThat(action).as("registered task cleanup").isNotNull();
    while (action != null) {
      action.action().run();
      action = storage.pollLast();
    }
  }

  private MockResponse task(String status) {
    return new MockResponse().setBody(taskJson(status));
  }

  private String taskJson(String status) {
    return "{\"id\":11,\"project_id\":7,\"template_id\":3,\"schedule_id\":5,\"message\":\"cleanup-test\",\"status\":\"%s\"}"
        .formatted(status);
  }

  private List<String> requests() throws InterruptedException {
    List<String> requests = new ArrayList<>();
    okhttp3.mockwebserver.RecordedRequest request;
    while ((request = server.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
      requests.add(request.getMethod() + " " + request.getPath());
    }
    return requests;
  }
}
