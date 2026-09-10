package io.bookwright.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SwaggerApiCatalogTest {

  @Test
  void readsOperationsWithBasePathAndFirstTag() throws Exception {
    String swagger =
        """
        swagger: '2.0'
        basePath: /api
        paths:
          /projects:
            parameters: []
            get:
              tags: [project]
            post:
              tags: [project]
          /ping:
            get:
              responses: {}
        """;

    assertThat(
            new SwaggerApiCatalog()
                .read(new ByteArrayInputStream(swagger.getBytes(StandardCharsets.UTF_8))))
        .containsExactly(
            new DocumentedApiOperation("project", new ApiOperation("GET", "/api/projects")),
            new DocumentedApiOperation("project", new ApiOperation("POST", "/api/projects")),
            new DocumentedApiOperation("untagged", new ApiOperation("GET", "/api/ping")));
  }

  @Test
  void supportsSpecificationsWithoutBasePath() throws Exception {
    String swagger =
        """
        swagger: '2.0'
        paths:
          /health:
            get:
              responses: {}
        """;

    assertThat(
            new SwaggerApiCatalog()
                .read(new ByteArrayInputStream(swagger.getBytes(StandardCharsets.UTF_8))))
        .containsExactly(
            new DocumentedApiOperation("untagged", new ApiOperation("GET", "/health")));
  }
}
