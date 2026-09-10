package io.bookwright.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiCoverageInterceptorTest {

  @Test
  void recordsApiMethodPathAndResponseWithoutTheQuery(@TempDir Path temp) throws Exception {
    Path output = temp.resolve("coverage/observations.tsv");
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(403));
      OkHttpClient client =
          new OkHttpClient.Builder().addInterceptor(new ApiCoverageInterceptor(output)).build();

      try (Response ignored =
          client
              .newCall(
                  new Request.Builder()
                      .url(server.url("/semaphore/api/project/42/?sort=name"))
                      .build())
              .execute()) {}
    }

    assertThat(Files.readAllLines(output)).containsExactly("403\tGET\t/api/project/42");
  }
}
