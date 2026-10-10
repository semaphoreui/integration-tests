package io.bookwright.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.api.model.semaphore.TaskHistoryQuery;
import io.bookwright.api.semaphore.tasks.SemaphoreTasksApi;
import io.bookwright.steps.semaphore.tasks.TaskHistorySteps;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaskHistoryContractTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void historyQueriesPreserveRouteAndAllPaginationFields(boolean template) throws Exception {
    try (var server = new MockWebServer()) {
      var steps = steps(server);
      server.enqueue(page("true", "[{\"id\":17}]"));
      var query = new TaskHistoryQuery(2, 1, 18L);
      var result = template ? steps.templatePage(7, 11, query) : steps.projectPage(7, query);
      assertThat(result.tasks()).extracting(task -> task.id()).containsExactly(17L);
      assertThat(result.hasNext()).isTrue();
      var request = server.takeRequest(1, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.getMethod()).isEqualTo("GET");
      assertThat(request.getPath())
          .isEqualTo(
              "/project/7/"
                  + (template ? "templates/11/" : "")
                  + "tasks/last?count=2&limit=1&before=18");
    }
  }

  @Test
  void optionalFieldsAreOmittedAndFalseIsNotTreatedAsTrue() throws Exception {
    try (var server = new MockWebServer()) {
      server.enqueue(page("false", "[]"));
      var result = steps(server).projectPage(7, new TaskHistoryQuery(null, 1, null));
      assertThat(result.tasks()).isEmpty();
      assertThat(result.hasNext()).isFalse();
      assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath())
          .isEqualTo("/project/7/tasks/last?limit=1");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "yes", "1", "FALSE"})
  void missingOrInvalidPaginationHeaderFailsClearly(String header) throws Exception {
    try (var server = new MockWebServer()) {
      var response = new MockResponse().setBody("[]").setHeader("Content-Type", "application/json");
      if (!header.equals("missing")) response.setHeader("X-Has-Next", header);
      server.enqueue(response);
      assertThatThrownBy(() -> steps(server).projectPage(7, new TaskHistoryQuery(2, null, null)))
          .isInstanceOf(UnexpectedResponseException.class)
          .hasMessageContaining("X-Has-Next")
          .hasMessageContaining("/project/7/tasks/last");
    }
  }

  @Test
  void nullHistoryBodyIsNotSilentlyTurnedIntoAnEmptyPage() throws Exception {
    try (var server = new MockWebServer()) {
      server.enqueue(page("false", "null"));
      assertThatThrownBy(() -> steps(server).projectPage(7, new TaskHistoryQuery(2, null, null)))
          .isInstanceOf(UnexpectedResponseException.class)
          .hasMessageContaining("body present=false");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 500})
  void errorResponsesAreNotAcceptedAsEmptyHistory(int status) throws Exception {
    try (var server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(status));
      assertThatThrownBy(() -> steps(server).projectPage(7, new TaskHistoryQuery(2, null, null)))
          .isInstanceOf(UnexpectedResponseException.class)
          .hasMessageContaining("Expected status [200]");
    }
  }

  private TaskHistorySteps steps(MockWebServer server) {
    return new TaskHistorySteps(
        RetrofitFactory.create(server.url("/").toString()).create(SemaphoreTasksApi.class));
  }

  private MockResponse page(String hasNext, String body) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setHeader("X-Has-Next", hasNext)
        .setBody(body);
  }
}
