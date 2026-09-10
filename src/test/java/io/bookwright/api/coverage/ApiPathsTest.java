package io.bookwright.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiPathsTest {

  @Test
  void extractsApiPathBehindWebRootAndRemovesTrailingSlash() {
    assertThat(ApiPaths.apiPath("/semaphore/api/project/42/")).isEqualTo("/api/project/42");
  }

  @Test
  void ignoresRequestsOutsideTheApi() {
    assertThat(ApiPaths.apiPath("/swagger/api-docs.yml")).isNull();
  }

  @Test
  void anonymizesDynamicIdsInUndocumentedPaths() {
    assertThat(ApiPaths.anonymizeUnknown("/api/project/42/jobs/17"))
        .isEqualTo("/api/project/{id}/jobs/{id}");
    assertThat(ApiPaths.anonymizeUnknown("/api/integrations/vxzsz3e9ocrrj72p"))
        .isEqualTo("/api/integrations/{id}");
  }
}
