package io.bookwright.api;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;

class RetrofitFactoryTest {

  @Test
  void doesNotApplyGlobalConnectionRetries() {
    Retrofit retrofit = RetrofitFactory.create("https://example.test");

    assertThat(retrofit.callFactory())
        .isInstanceOfSatisfying(
            OkHttpClient.class, client -> assertThat(client.retryOnConnectionFailure()).isFalse());
  }

  @Test
  void appliesBearerAuthenticationWithoutExposingItToHttpReports() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setResponseCode(200));
      Retrofit retrofit =
          RetrofitFactory.createWithBearerToken(server.url("/").toString(), "secret");
      OkHttpClient client = (OkHttpClient) retrofit.callFactory();

      try (var ignored =
          client.newCall(new Request.Builder().url(server.url("/user")).build()).execute()) {
        if (!"Bearer secret".equals(server.takeRequest().getHeader("Authorization"))) {
          throw new AssertionError("Bearer Authorization header was not applied");
        }
      }
    }
  }
}
