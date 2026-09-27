package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;
import io.bookwright.steps.semaphore.users.UserSteps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class TotpCleanupTest {

  private MockWebServer server;
  private TeardownStorage storage;
  private UserSteps users;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    storage = new TeardownStorage();
    var retrofit =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    users = new UserSteps(retrofit.create(SemaphoreUsersApi.class), storage);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void failedBrowserScenarioCleansUpWithOrWithoutAnEnrollment(boolean enrolled)
      throws InterruptedException {
    createUserAndRegisterCleanup();
    queueCleanup(enrolled, 204);

    TeardownExtension.execute(storage, true, true);

    assertThat(requests())
        .containsExactlyElementsOf(
            enrolled
                ? List.of(
                    "POST /users", "GET /users/7", "DELETE /users/7/2fas/totp/9", "DELETE /users/7")
                : List.of("POST /users", "GET /users/7", "DELETE /users/7"));
  }

  @Test
  void bindingCleanupFailureDoesNotSkipUserDeletionOrReplacePrimaryFailure()
      throws InterruptedException {
    createUserAndRegisterCleanup();
    queueCleanup(true, 500);

    assertThatCode(() -> TeardownExtension.execute(storage, true, true)).doesNotThrowAnyException();

    assertThat(requests())
        .containsExactly(
            "POST /users", "GET /users/7", "DELETE /users/7/2fas/totp/9", "DELETE /users/7");
  }

  private void createUserAndRegisterCleanup() {
    server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":7}"));
    users.disableTotpAfterTest(
        users.createDisposable(
            new UserRequest(
                "TOTP test", "totp-test", "totp@test.invalid", "test-only", false, true, false)));
  }

  private void queueCleanup(boolean enrolled, int bindingDeleteStatus) {
    server.enqueue(
        new MockResponse()
            .setBody(
                enrolled
                    ? "{\"id\":7,\"totp\":{\"id\":9,\"user_id\":7}}"
                    : "{\"id\":7,\"totp\":null}"));
    if (enrolled) {
      server.enqueue(new MockResponse().setResponseCode(bindingDeleteStatus));
    }
    server.enqueue(new MockResponse().setResponseCode(204));
  }

  private List<String> requests() throws InterruptedException {
    List<String> result = new ArrayList<>();
    okhttp3.mockwebserver.RecordedRequest request;
    while ((request = server.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
      result.add(request.getMethod() + " " + request.getPath());
    }
    return result;
  }
}
