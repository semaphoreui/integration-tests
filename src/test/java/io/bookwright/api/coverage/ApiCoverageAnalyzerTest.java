package io.bookwright.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiCoverageAnalyzerTest {

  @Test
  void separatesSuccessfulNegativeOnlyUncoveredAndUndocumentedOperations() {
    List<DocumentedApiOperation> catalog =
        List.of(
            documented("project", "GET", "/api/projects"),
            documented("project", "PUT", "/api/project/{project_id}"),
            documented("user", "GET", "/api/users/{user_id}"));
    List<ApiCoverageObservation> observations =
        List.of(
            observed(200, "GET", "/api/projects"),
            observed(403, "PUT", "/api/project/42"),
            observed(200, "POST", "/api/project/42/cache"));

    ApiCoverageAnalyzer.Result result = new ApiCoverageAnalyzer().analyze(catalog, observations);

    assertThat(result.documented()).isEqualTo(3);
    assertThat(result.touched()).isEqualTo(2);
    assertThat(result.successful()).isEqualTo(1);
    assertThat(result.negativeOnly())
        .containsExactly(documented("project", "PUT", "/api/project/{project_id}"));
    assertThat(result.uncovered())
        .containsExactly(documented("user", "GET", "/api/users/{user_id}"));
    assertThat(result.undocumented())
        .containsExactly(new ApiOperation("POST", "/api/project/{id}/cache"));
  }

  @Test
  void prefersLiteralRouteOverParameterRoute() {
    List<DocumentedApiOperation> catalog =
        List.of(
            documented("user", "GET", "/api/users/{user_id}"),
            documented("options", "GET", "/api/users/options"));

    ApiCoverageAnalyzer.Result result =
        new ApiCoverageAnalyzer()
            .analyze(catalog, List.of(observed(200, "GET", "/api/users/options")));

    assertThat(result.components())
        .filteredOn(component -> component.component().equals("options"))
        .singleElement()
        .extracting(ApiCoverageAnalyzer.ComponentCoverage::touched)
        .isEqualTo(1);
  }

  private DocumentedApiOperation documented(String component, String method, String path) {
    return new DocumentedApiOperation(component, new ApiOperation(method, path));
  }

  private ApiCoverageObservation observed(int status, String method, String path) {
    return new ApiCoverageObservation(new ApiOperation(method, path), status);
  }
}
