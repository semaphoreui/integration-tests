package io.bookwright.teardown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.UnexpectedResponseException;
import io.bookwright.api.model.semaphore.ApiTokenRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.config.MainConfig;
import io.bookwright.steps.semaphore.tokens.TokenSteps;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiTokenOwnershipTest {

  @ParameterizedTest
  @ValueSource(ints = {204, 404, 403, 500})
  void cleanupUsesTheCreatingSessionAndAcceptsOnlyDeletionOrAbsence(int status) throws Exception {
    try (var adminServer = new MockWebServer();
        var ownerServer = new MockWebServer()) {
      var admin = session(adminServer);
      var owner = session(ownerServer);
      var storage = new TeardownStorage();
      var steps = new TokenSteps(admin.tokens(), config(ownerServer), storage);
      ownerServer.enqueue(tokenResponse());
      ownerServer.enqueue(new MockResponse().setResponseCode(status));

      var token = steps.create(owner, request());
      var cleanup = storage.pollLast();
      assertThat(cleanup.name()).doesNotContain(token.id());
      if (status == 204 || status == 404) {
        cleanup.action().run();
      } else {
        assertThatThrownBy(() -> cleanup.action().run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("received " + status);
      }

      var created = ownerServer.takeRequest(1, TimeUnit.SECONDS);
      assertThat(created.getMethod()).isEqualTo("POST");
      assertThat(created.getPath()).isEqualTo("/user/tokens");
      var deleted = ownerServer.takeRequest(1, TimeUnit.SECONDS);
      assertThat(deleted.getMethod()).isEqualTo("DELETE");
      assertThat(deleted.getPath()).isEqualTo("/user/tokens/12345678");
      assertThat(adminServer.getRequestCount()).isZero();
      assertThat(storage.pollLast()).isNull();
    }
  }

  @Test
  void explicitRevocationAndListingUseTheOwnerSession() throws Exception {
    try (var adminServer = new MockWebServer();
        var ownerServer = new MockWebServer()) {
      var steps =
          new TokenSteps(session(adminServer).tokens(), config(ownerServer), new TeardownStorage());
      var owner = session(ownerServer);
      ownerServer.enqueue(tokenResponse());
      ownerServer.enqueue(new MockResponse().setResponseCode(204));
      ownerServer.enqueue(
          new MockResponse().setHeader("Content-Type", "application/json").setBody("[]"));

      steps.delete(owner, steps.create(owner, request()));
      assertThat(steps.getTokens(owner)).isEmpty();

      ownerServer.takeRequest(1, TimeUnit.SECONDS);
      var deleted = ownerServer.takeRequest(1, TimeUnit.SECONDS);
      assertThat(deleted.getMethod()).isEqualTo("DELETE");
      assertThat(deleted.getPath()).isEqualTo("/user/tokens/12345678");
      var listed = ownerServer.takeRequest(1, TimeUnit.SECONDS);
      assertThat(listed.getMethod()).isEqualTo("GET");
      assertThat(listed.getPath()).isEqualTo("/user/tokens");
      assertThat(adminServer.getRequestCount()).isZero();
    }
  }

  @Test
  void rejectedCreationDoesNotRegisterCleanup() throws Exception {
    try (var server = new MockWebServer()) {
      var owner = session(server);
      var storage = new TeardownStorage();
      var steps = new TokenSteps(owner.tokens(), config(server), storage);
      server.enqueue(new MockResponse().setResponseCode(403));

      assertThatThrownBy(() -> steps.create(owner, request()))
          .isInstanceOf(UnexpectedResponseException.class)
          .hasMessageContaining("Expected status [201]");
      assertThat(storage.pollLast()).isNull();
    }
  }

  @Test
  void bearerSessionDoesNotInheritTheOwnersCookie() throws Exception {
    try (var server = new MockWebServer()) {
      var owner = session(server);
      var steps = new TokenSteps(owner.tokens(), config(server), new TeardownStorage());
      server.enqueue(tokenResponse().setHeader("Set-Cookie", "session=owner-cookie; Path=/"));
      server.enqueue(new MockResponse().setResponseCode(401));

      steps.verifyRejected(steps.authenticate(steps.create(owner, request())));

      server.takeRequest(1, TimeUnit.SECONDS);
      var rejected = server.takeRequest(1, TimeUnit.SECONDS);
      assertThat(rejected.getHeader("Authorization")).isEqualTo("Bearer 12345678-token-secret");
      assertThat(rejected.getHeader("Cookie")).isNull();
      assertThat(rejected.getPath()).isEqualTo("/user");
    }
  }

  private SemaphoreSessionApis session(MockWebServer server) {
    return SemaphoreSessionApis.create(RetrofitFactory.create(server.url("/").toString()));
  }

  private MainConfig config(MockWebServer server) {
    return ConfigFactory.create(
        MainConfig.class, Map.of("api.base.url", server.url("/").toString()));
  }

  private ApiTokenRequest request() {
    return new ApiTokenRequest("owner-token", Instant.parse("2030-01-01T00:00:00Z"));
  }

  private MockResponse tokenResponse() {
    return new MockResponse()
        .setResponseCode(201)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"12345678-token-secret\",\"name\":\"owner-token\",\"user_id\":7}");
  }
}
