package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bookwright.api.UnexpectedResponseException;
import io.bookwright.api.model.semaphore.RunnerUpdateRequest;
import io.bookwright.api.semaphore.runners.SemaphoreRunnersApi;
import io.bookwright.api.testenvironment.runners.DynamicRunnerLauncherApi;
import io.bookwright.steps.semaphore.runners.RunnerSteps;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class RunnerRestorationTest {

  private static final String ORIGINAL =
      """
      {"id":7,"name":"original-runner","active":true,"is_default":true,
       "webhook":"https://runner.invalid/original","max_parallel_tasks":2,
       "tags":["original-tag"],"registered":true,"status":"online"}
      """;
  private static final String CHANGED =
      """
      {"id":7,"name":"changed-runner","active":false,"is_default":false,
       "webhook":"","max_parallel_tasks":1,"tags":["temporary-tag"]}
      """;

  private MockWebServer server;
  private TeardownStorage storage;
  private RunnerSteps steps;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    var retrofit =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    storage = new TeardownStorage();
    steps =
        new RunnerSteps(
            retrofit.create(SemaphoreRunnersApi.class),
            retrofit.create(DynamicRunnerLauncherApi.class),
            storage);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void restoresOriginalConfigurationAfterLaterUpdates() throws Exception {
    configureTemporarily();
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setBody(CHANGED));
    steps.updateRunner(7, changedRequest());
    server.enqueue(new MockResponse().setResponseCode(204));

    TeardownExtension.execute(storage, true, false);

    assertRestorationRequest(6);
  }

  @Test
  void failedConfigurationStillRegistersRestoration() throws Exception {
    server.enqueue(new MockResponse().setBody(ORIGINAL));
    server.enqueue(new MockResponse().setResponseCode(500));
    assertThatThrownBy(() -> steps.configureTemporarily(7, changedRequest()))
        .isInstanceOf(UnexpectedResponseException.class);
    server.enqueue(new MockResponse().setResponseCode(204));

    TeardownExtension.execute(storage, true, true);

    assertRestorationRequest(3);
  }

  @Test
  void otherCleanupFailureDoesNotPreventRestorationOrReplacePrimaryFailure() throws Exception {
    configureTemporarily();
    storage.push(
        "Fail an earlier resource cleanup",
        () -> {
          throw new IllegalStateException("Resource cleanup failed");
        });
    server.enqueue(new MockResponse().setResponseCode(204));

    assertThatCode(() -> TeardownExtension.execute(storage, true, true)).doesNotThrowAnyException();

    assertRestorationRequest(4);
  }

  private void configureTemporarily() {
    server.enqueue(new MockResponse().setBody(ORIGINAL));
    server.enqueue(new MockResponse().setResponseCode(204));
    server.enqueue(new MockResponse().setBody(CHANGED));
    steps.configureTemporarily(7, changedRequest());
  }

  private RunnerUpdateRequest changedRequest() {
    return new RunnerUpdateRequest("changed-runner", false, false, "", 1, List.of("temporary-tag"));
  }

  private void assertRestorationRequest(int requestCount) throws Exception {
    assertThat(server.getRequestCount()).isEqualTo(requestCount);
    for (int i = 1; i < requestCount; i++) {
      assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }
    var restored = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(restored).isNotNull();
    assertThat(restored.getMethod()).isEqualTo("PUT");
    assertThat(restored.getPath()).isEqualTo("/runners/7");
    var json = new ObjectMapper();
    assertThat(json.readTree(restored.getBody().readUtf8()))
        .isEqualTo(
            json.readTree(
                """
                {"name":"original-runner","active":true,"is_default":true,
                 "webhook":"https://runner.invalid/original","max_parallel_tasks":2,
                 "tags":["original-tag"]}
                """));
  }
}
